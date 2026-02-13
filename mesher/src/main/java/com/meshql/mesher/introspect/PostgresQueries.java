package com.meshql.mesher.introspect;

/**
 * SQL queries for PostgreSQL schema introspection.
 * Ported from veriforged/src/introspect/postgres.rs.
 *
 * All queries filter system schemas (pg_catalog, information_schema)
 * and return results in deterministic order.
 */
public final class PostgresQueries {
    private PostgresQueries() {}

    public static final String TABLES = """
            SELECT table_schema,
                   table_name
            FROM information_schema.tables
            WHERE table_type = 'BASE TABLE'
              AND table_schema NOT IN ('pg_catalog', 'information_schema')
            ORDER BY table_schema, table_name
            """;

    public static final String COLUMNS = """
            SELECT table_schema,
                   table_name,
                   column_name,
                   data_type,
                   udt_name,
                   character_maximum_length,
                   numeric_precision,
                   numeric_scale,
                   is_nullable,
                   column_default,
                   ordinal_position
            FROM information_schema.columns
            WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
            ORDER BY table_schema, table_name, ordinal_position
            """;

    public static final String PRIMARY_KEYS = """
            SELECT tc.table_schema,
                   tc.table_name,
                   kcu.column_name,
                   kcu.ordinal_position
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            WHERE tc.constraint_type = 'PRIMARY KEY'
              AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
            ORDER BY tc.table_schema, tc.table_name, kcu.ordinal_position
            """;

    public static final String FOREIGN_KEYS = """
            SELECT tc.table_schema AS child_schema,
                   tc.table_name AS child_table,
                   kcu.column_name AS child_column,
                   pku.table_schema AS parent_schema,
                   pku.table_name AS parent_table,
                   pku.column_name AS parent_column,
                   kcu.ordinal_position
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            JOIN information_schema.referential_constraints rc
              ON rc.constraint_name = tc.constraint_name
             AND rc.constraint_schema = tc.table_schema
            JOIN information_schema.key_column_usage pku
              ON pku.constraint_name = rc.unique_constraint_name
             AND pku.constraint_schema = rc.unique_constraint_schema
             AND pku.ordinal_position = kcu.position_in_unique_constraint
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
            ORDER BY tc.table_schema, tc.table_name, kcu.ordinal_position
            """;

    public static final String UNIQUE_CONSTRAINTS = """
            SELECT tc.table_schema,
                   tc.table_name,
                   kcu.column_name,
                   kcu.ordinal_position
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            WHERE tc.constraint_type = 'UNIQUE'
              AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
            ORDER BY tc.table_schema, tc.table_name, kcu.ordinal_position
            """;

    public static final String CHECK_CONSTRAINTS = """
            SELECT tc.table_schema,
                   tc.table_name,
                   cc.constraint_name,
                   cc.check_clause
            FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc
              ON tc.constraint_name = cc.constraint_name
            WHERE tc.constraint_type = 'CHECK'
              AND tc.table_schema NOT IN ('pg_catalog', 'information_schema')
            ORDER BY tc.table_schema, tc.table_name, cc.constraint_name
            """;
}
