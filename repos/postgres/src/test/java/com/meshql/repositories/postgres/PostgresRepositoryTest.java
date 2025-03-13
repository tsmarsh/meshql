package com.meshql.repositories.postgres;

import com.meshql.repos.certification.RepositoryCertification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test class for PostgresRepository using Testcontainers.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgresRepositoryTest extends RepositoryCertification {

    @Container
    private static final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withUsername("alice")
            .withPassword("face")
            .withDatabaseName("repository");

    private final List<DataSource> dataSources = new ArrayList<>();
    private int testCounter = 0;

    @BeforeAll
    public static void startContainer() {
        postgreSQLContainer.start();
    }

    @AfterAll
    public static void stopContainer() {
        postgreSQLContainer.stop();
    }

    @Override
    public void init() {
        try {
            // Create a new data source for each test
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setServerName(postgreSQLContainer.getHost());
            dataSource.setPortNumber(postgreSQLContainer.getMappedPort(5432));
            dataSource.setDatabaseName(postgreSQLContainer.getDatabaseName());
            dataSource.setUser(postgreSQLContainer.getUsername());
            dataSource.setPassword(postgreSQLContainer.getPassword());

            dataSources.add(dataSource);

            // Create a unique table name for each test
            String tableName = "test" + (++testCounter);

            // Create and initialize the repository
            PostgresRespository postgresRepository = new PostgresRespository(dataSource, tableName);
            postgresRepository.initialize();
            repository = postgresRepository;
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