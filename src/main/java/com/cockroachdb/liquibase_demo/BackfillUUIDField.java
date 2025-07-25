package com.cockroachdb.liquibase_demo;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BackfillUUIDField implements CustomTaskChange {

    private static final int BATCH_SIZE = 1000;

    private String tableName;

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    private boolean isRetryable(SQLException e) {
        // CRDB uses SQLSTATE 40001 for retryable serialization failures
        return "40001".equals(e.getSQLState());
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection jdbcConnection = (JdbcConnection) database.getConnection();
        Connection connection = jdbcConnection.getUnderlyingConnection();

        long totalUpdated = 0;
        int batchNumber = 1;
        int maxRetries = 10;

        String selectQuery = String.format("""
            SELECT id
            FROM %s
            WHERE id_uuid IS NULL
            LIMIT %d
            FOR UPDATE SKIP LOCKED;
            """, tableName, BATCH_SIZE);

        try {
            connection.setAutoCommit(false);
            int updated;
            long totalTimeMs = 0;
            do {
                int retries = 0;
                updated = 0;

                while (true) {
                    long selectStart = System.nanoTime();

                    List<String> ids = new ArrayList<>(BATCH_SIZE);
                    try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery); ResultSet rs = selectStmt.executeQuery()) {
                        
                        long selectMs = (System.nanoTime() - selectStart) / 1_000_000;
                        totalTimeMs += selectMs;
                        while (rs.next()) {
                            ids.add(rs.getString("id"));
                        }

                        if (ids.isEmpty()) {
                            connection.commit();
                            break;
                        }

                        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
                        String updateQuery = String.format(
                            "UPDATE %s SET id_uuid = id::UUID WHERE id IN (%s)", tableName.split("@")[0], placeholders);

                        long updateStart = System.nanoTime();
                        try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                            for (int i = 0; i < ids.size(); i++) {
                                updateStmt.setString(i + 1, ids.get(i));
                            }
                            updated = updateStmt.executeUpdate();
                        }
                        long updateMs = (System.nanoTime() - updateStart) / 1_000_000;
                        totalTimeMs += updateMs;

                        long commitStart = System.nanoTime();
                        connection.commit();
                        long commitMs = (System.nanoTime() - commitStart) / 1_000_000;
                        totalTimeMs += commitMs;

                        // connection.setAutoCommit(true);
                        // connection.setAutoCommit(false); // force reset of transaction context

                        System.out.printf(
                            "Batch %d: selected %d in %d ms, updated %d in %d ms, committed in %d ms%n",
                            batchNumber, ids.size(), selectMs, updated, updateMs, commitMs
                        );
                        break; // success
                    } catch (SQLException e) {
                        connection.rollback();

                        if (isRetryable(e) && retries < maxRetries) {
                            retries++;
                            System.out.printf("Retrying batch %d after error: %s%n", batchNumber, e.getMessage());
                            Thread.sleep((long) Math.pow(2, retries) * 1000L); // exponential backoff
                        } else {
                            throw new CustomChangeException("Failed after retries", e);
                        }
                    }
                }

                totalUpdated += updated;
                batchNumber++;
            } while (updated > 0);

            System.out.printf("Backfill complete. Total rows updated %d records in %d ms%n", totalUpdated, totalTimeMs);

        } catch (SQLException | InterruptedException e) {
            throw new CustomChangeException("Error during batched UUID backfill.", e);
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Backfilled id_uuid in batches.";
    }

    @Override
    public void setUp() throws SetupException {}

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {}

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
