package com.meshql.repositories.postgres;

import com.github.jknack.handlebars.Handlebars;
import com.meshql.cert.Hooks;
import com.meshql.cert.IntegrationWorld;
import com.meshql.cert.SearcherTestTemplates;
import com.meshql.core.config.StorageConfig;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Cucumber hooks for PostgreSQL certification tests using Testcontainers.
 */
public class PostgresPluginHooks extends Hooks {
    private static final Logger logger = LoggerFactory.getLogger(PostgresPluginHooks.class);
    private static PostgreSQLContainer container;

    private DataSource dataSource;
    private String tableName;

    public PostgresPluginHooks(IntegrationWorld world) {
        super(world);
    }

    @Before
    @Override
    public void setUp() {
        super.setUp(); // Sets up testStartTime, tokens, and auth

        try {
            // Start PostgreSQL container if not already running
            if (container == null || !container.isRunning()) {
                container = new PostgreSQLContainer("postgres:latest")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withDatabaseName("testdb");
                container.start();
                logger.info("Started PostgreSQL container");
            }

            // Create data source
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setServerNames(new String[]{container.getHost()});
            ds.setPortNumbers(new int[]{container.getMappedPort(5432)});
            ds.setDatabaseName(container.getDatabaseName());
            ds.setUser(container.getUsername());
            ds.setPassword(container.getPassword());
            this.dataSource = ds;

            // Create unique table for this test
            this.tableName = "test_" + System.currentTimeMillis();

            // Initialize PostgresRepository (creates tables)
            PostgresRepository postgresRepository = new PostgresRepository(dataSource, tableName);
            postgresRepository.initialize();

            // Configure the plugin
            this.world.storageConfig = new StorageConfig("postgres");
            this.world.plugin = new PostgresPluginWrapper(dataSource, tableName);

            // Set up PostgreSQL query templates
            try {
                Handlebars handlebars = new Handlebars();
                this.world.templates = new SearcherTestTemplates(
                    // findById: e.id = '{{id}}'
                    handlebars.compileInline("e.id = '{{id}}'"),

                    // findByName: e.payload->>'name' = '{{id}}'
                    handlebars.compileInline("e.payload->>'name' = '{{id}}'"),

                    // findAllByType: e.payload->>'type' = '{{id}}'
                    handlebars.compileInline("e.payload->>'type' = '{{id}}'"),

                    // findByNameAndType
                    handlebars.compileInline("e.payload->>'name' = '{{name}}' AND e.payload->>'type' = '{{type}}'")
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to compile PostgreSQL query templates", e);
            }

            logger.info("Initialized PostgreSQL certification tests with table: {}", tableName);
        } catch (Exception e) {
            logger.error("Failed to set up PostgreSQL for certification tests", e);
            throw new RuntimeException("PostgreSQL setup failed", e);
        }
    }

    @After
    public void tearDown() {
        // Tables will be cleaned up when testcontainer is stopped
    }
}
