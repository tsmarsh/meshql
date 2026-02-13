package com.meshql.mesher.introspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "TESTCONTAINERS", matches = "1")
class DatabaseIntrospectorTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @Test
    void introspectSpringfieldSchema() throws Exception {
        // Load the springfield DDL
        String ddl = new String(
                getClass().getResourceAsStream("/springfield-init.sql").readAllBytes()
        );

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }
        }

        DatabaseIntrospector introspector = new DatabaseIntrospector(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        IntrospectionResult result = introspector.introspect();

        // 4 tables
        assertEquals(4, result.tables().size());

        // Tables should be in alphabetical order
        assertEquals("bill_hdr", result.tables().get(0).name());
        assertEquals("cust_acct", result.tables().get(1).name());
        assertEquals("mtr_rdng", result.tables().get(2).name());
        assertEquals("pymt_hist", result.tables().get(3).name());

        // Columns for cust_acct
        long custCols = result.columns().stream()
                .filter(c -> c.tableName().equals("cust_acct"))
                .count();
        assertEquals(18, custCols);

        // Primary keys
        assertEquals(4, result.primaryKeys().size());

        // Foreign keys: mtr_rdng->cust_acct, bill_hdr->cust_acct, pymt_hist->cust_acct, pymt_hist->bill_hdr
        assertEquals(4, result.foreignKeys().size());

        // Unique constraint: cust_acct.acct_num
        assertTrue(result.uniqueConstraints().stream()
                .anyMatch(u -> u.tableName().equals("cust_acct") && u.columnName().equals("acct_num")));
    }
}
