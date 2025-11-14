package com.meshql.repos.sqlite;

import com.github.jknack.handlebars.Handlebars;
import com.meshql.cert.Hooks;
import com.meshql.cert.IntegrationWorld;
import com.meshql.cert.SearcherTestTemplates;
import com.meshql.core.config.StorageConfig;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;

/**
 * Cucumber hooks for SQLite certification tests.
 */
public class SQLitePluginHooks extends Hooks {
    private static final Logger logger = LoggerFactory.getLogger(SQLitePluginHooks.class);

    private DataSource dataSource;
    private String tableName;
    private File dbFile;

    public SQLitePluginHooks(IntegrationWorld world) {
        super(world);
    }

    @Before
    @Override
    public void setUp() {
        super.setUp(); // Sets up testStartTime, tokens, and auth

        try {
            // Create temp SQLite database file
            dbFile = Files.createTempFile("meshql_test_", ".db").toFile();
            dbFile.deleteOnExit();

            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            this.dataSource = ds;

            // Create unique table for this test
            this.tableName = "test_" + System.currentTimeMillis();

            // Initialize SQLiteRepository (creates tables)
            SQLiteRepository sqliteRepository = new SQLiteRepository(dataSource.getConnection(), tableName);
            sqliteRepository.initialize();

            // Configure the plugin
            this.world.storageConfig = new StorageConfig("sqlite");
            this.world.plugin = new SQLitePluginWrapper(dataSource, tableName);

            // Set up SQLite query templates (note: no table alias, unlike PostgreSQL)
            try {
                Handlebars handlebars = new Handlebars();
                this.world.templates = new SearcherTestTemplates(
                    // findById: id = '{{id}}'
                    handlebars.compileInline("id = '{{id}}'"),

                    // findByName: json_extract(payload, '$.name') = '{{id}}'
                    handlebars.compileInline("json_extract(payload, '$.name') = '{{id}}'"),

                    // findAllByType: json_extract(payload, '$.type') = '{{id}}'
                    handlebars.compileInline("json_extract(payload, '$.type') = '{{id}}'"),

                    // findByNameAndType
                    handlebars.compileInline("json_extract(payload, '$.name') = '{{name}}' AND json_extract(payload, '$.type') = '{{type}}'")
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to compile SQLite query templates", e);
            }

            logger.info("Initialized SQLite certification tests with DB: {} and table: {}", dbFile.getAbsolutePath(), tableName);
        } catch (Exception e) {
            logger.error("Failed to set up SQLite for certification tests", e);
            throw new RuntimeException("SQLite setup failed", e);
        }
    }

    @After
    public void tearDown() {
        // Delete the temp database file
        if (dbFile != null && dbFile.exists()) {
            dbFile.delete();
        }
    }
}
