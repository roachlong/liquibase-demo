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
import java.sql.SQLException;

public class BackfillUUIDField implements CustomTaskChange {

    private static final int BATCH_SIZE = 10000;

    private String tableName;

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection jdbcConnection = (JdbcConnection) database.getConnection();
        Connection connection = jdbcConnection.getUnderlyingConnection();

        long totalUpdated = 0;
        int batchNumber = 1;

        // Use LIMIT-based batching
        String rawQuery = """
            WITH batch AS (
                SELECT id, id::UUID AS new_uuid
                FROM %s
                WHERE id_uuid IS NULL
                LIMIT %d
            )
            UPDATE %s m
            SET id_uuid = b.new_uuid
            FROM batch b
            WHERE m.id = b.id;
            """;

        String updateQuery = String.format(rawQuery, tableName, BATCH_SIZE, tableName);

        try {
            connection.setAutoCommit(false);

            int updated;
            do {
                try (PreparedStatement statement = connection.prepareStatement(updateQuery)) {
                    updated = statement.executeUpdate();
                    connection.commit();
                    totalUpdated += updated;

                    System.out.printf("Batch %d: updated %d rows and committed.%n", batchNumber, updated);
                    batchNumber++;
                }
            } while (updated > 0);

            System.out.printf("Backfill complete. Total rows updated: %d%n", totalUpdated);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw new CustomChangeException("Error during batched UUID backfill.", e);
        } finally {
            try {
                connection.setAutoCommit(true);
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
