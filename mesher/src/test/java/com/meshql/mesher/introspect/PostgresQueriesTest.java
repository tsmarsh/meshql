package com.meshql.mesher.introspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostgresQueriesTest {

    @Test
    void queriesAreNotEmpty() {
        assertFalse(PostgresQueries.TABLES.isBlank());
        assertFalse(PostgresQueries.COLUMNS.isBlank());
        assertFalse(PostgresQueries.PRIMARY_KEYS.isBlank());
        assertFalse(PostgresQueries.FOREIGN_KEYS.isBlank());
        assertFalse(PostgresQueries.UNIQUE_CONSTRAINTS.isBlank());
        assertFalse(PostgresQueries.CHECK_CONSTRAINTS.isBlank());
    }

    @Test
    void queriesFilterSystemSchemas() {
        assertTrue(PostgresQueries.TABLES.contains("pg_catalog"));
        assertTrue(PostgresQueries.TABLES.contains("information_schema"));
        assertTrue(PostgresQueries.COLUMNS.contains("pg_catalog"));
        assertTrue(PostgresQueries.PRIMARY_KEYS.contains("pg_catalog"));
        assertTrue(PostgresQueries.FOREIGN_KEYS.contains("pg_catalog"));
    }

    @Test
    void queriesAreOrdered() {
        assertTrue(PostgresQueries.TABLES.contains("ORDER BY"));
        assertTrue(PostgresQueries.COLUMNS.contains("ORDER BY"));
        assertTrue(PostgresQueries.PRIMARY_KEYS.contains("ORDER BY"));
        assertTrue(PostgresQueries.FOREIGN_KEYS.contains("ORDER BY"));
        assertTrue(PostgresQueries.UNIQUE_CONSTRAINTS.contains("ORDER BY"));
        assertTrue(PostgresQueries.CHECK_CONSTRAINTS.contains("ORDER BY"));
    }
}
