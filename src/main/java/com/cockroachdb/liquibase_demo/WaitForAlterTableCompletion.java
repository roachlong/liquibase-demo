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

public class WaitForAlterTableCompletion implements CustomTaskChange {

    private String tableName;

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection jdbcConnection = (JdbcConnection) database.getConnection();
        Connection connection = jdbcConnection.getUnderlyingConnection();

        String query = "SELECT status FROM [SHOW JOBS] WHERE description LIKE ? ORDER BY created DESC LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            Thread.sleep(1000); // Wait for the jobs to initialize
            statement.setString(1, "%ALTER TABLE " + tableName + "%");

            while (true) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String status = resultSet.getString("status");
                        if ("succeeded".equalsIgnoreCase(status)) {
                            System.out.println("Table alteration completed successfully.");
                            break;
                        } else if ("failed".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) {
                            throw new CustomChangeException("Table alteration failed or was canceled.");
                        } else {
                            System.out.println("Current job status: " + status + ". Waiting...");
                            Thread.sleep(1000); // Wait for 1 second before checking again
                        }
                    } else {
                        System.out.println("Job not found. Waiting...");
                        Thread.sleep(1000); // Wait for 1 second before checking again
                    }
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new CustomChangeException("Error while monitoring table alteration job.", e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Waited for table alteration: " + tableName;
    }

    @Override
    public void setUp() throws SetupException {
        // No setup required
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        // Not used
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
