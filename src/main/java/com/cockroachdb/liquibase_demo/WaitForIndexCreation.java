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

public class WaitForIndexCreation implements CustomTaskChange {

    private String indexName;

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection jdbcConnection = (JdbcConnection) database.getConnection();
        Connection connection = jdbcConnection.getUnderlyingConnection();

        String query = "SELECT status FROM crdb_internal.jobs WHERE job_type = 'NEW SCHEMA CHANGE' AND statement ILIKE ? ORDER BY created DESC LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            Thread.sleep(1000); // Wait for the jobs to initialize
            statement.setString(1, "%CREATE INDEX " + indexName + "%");

            while (true) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String status = resultSet.getString("status");
                        if ("succeeded".equalsIgnoreCase(status)) {
                            System.out.println("Index creation completed successfully.");
                            break;
                        } else if ("failed".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) {
                            throw new CustomChangeException("Index creation failed or was canceled.");
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
            throw new CustomChangeException("Error while monitoring index creation job.", e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Waited for index creation: " + indexName;
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
