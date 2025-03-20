package com.meshql.repos.sqlite;

import com.meshql.repos.certification.RepositoryCertification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test class for PostgresRepository using Testcontainers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQLiteRepositoryTest extends RepositoryCertification {

    private final List<DataSource> dataSources = new ArrayList<>();
    private int testCounter = 0;

    @Override
    public void init() {
        try {
            // Create a new data source for each test
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite::memory:");

            dataSources.add(dataSource);

            // Create a unique table name for each test
            String tableName = "test" + (++testCounter);

            // Create and initialize the repository
            SQLiteRepository sqLiteRepository = new SQLiteRepository(dataSource.getConnection(), tableName);
            sqLiteRepository.initialize();
            repository = sqLiteRepository;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize PostgreSQL repository", e);
        }
    }

    @AfterAll
    public void tearDown() {
        // Close all data sources
        for (DataSource dataSource : dataSources) {
            if (dataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) dataSource).close();
                } catch (Exception e) {
                    // Log but continue closing other resources
                    System.err.println("Error closing data source: " + e.getMessage());
                }
            }
        }
    }
}