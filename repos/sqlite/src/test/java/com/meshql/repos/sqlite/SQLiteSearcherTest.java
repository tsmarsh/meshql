package com.meshql.repos.sqlite;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.repos.certification.SearcherCertification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.tailoredshapes.underbar.ocho.Die.rethrow;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQLiteSearcherTest extends SearcherCertification {

    private final List<DataSource> dataSources = new ArrayList<>();
    private int testCounter = 0;
    private final Handlebars handlebars = new Handlebars();

    @Override
    public void init() {
        try {
            // Create a new data source for each test
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:test.db");
            dataSources.add(dataSource);

            // Create a unique table name for each test
            String tableName = "test" + ++testCounter;

            // Create and initialize the repository
            SQLiteRepository sqLiteRepository = new SQLiteRepository(dataSource.getConnection(), tableName);
            sqLiteRepository.initialize();
            repository = sqLiteRepository;

            // Create the searcher with NoAuth
            Auth noAuth = new NoAuth();
            searcher = new SQLiteSearcher(dataSource, tableName, noAuth);

            // Create the templates
            templates = createTemplates();

        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to initialize PostgreSQL searcher test", e);
        }
    }

    private SearcherTemplates createTemplates() throws IOException {
        Template findById = handlebars.compileInline("id = '{{id}}'");
        Template findByName = handlebars.compileInline("json_extract(payload, '$.name') = '{{id}}'");
        Template findAllByType = handlebars
                .compileInline("json_extract(payload, '$.type') = '{{id}}'");
        Template findByNameAndType = handlebars.compileInline(
                "json_extract(payload, '$.type') = '{{type}}' AND json_extract(payload, '$.name') = '{{name}}'");

        return new SearcherTemplates(findById, findByName, findAllByType, findByNameAndType);
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
        rethrow(()->new File("test.db").delete());
    }
}