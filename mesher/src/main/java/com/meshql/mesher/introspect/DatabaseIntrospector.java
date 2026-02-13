package com.meshql.mesher.introspect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Introspects a PostgreSQL database schema using JDBC.
 * Executes the queries from {@link PostgresQueries} and assembles an {@link IntrospectionResult}.
 */
public class DatabaseIntrospector {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseIntrospector.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseIntrospector(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public DatabaseIntrospector(String jdbcUrl) {
        this(jdbcUrl, null, null);
    }

    public IntrospectionResult introspect() throws SQLException {
        logger.info("Introspecting database: {}", jdbcUrl);

        try (Connection conn = connect()) {
            List<TableInfo> tables = queryTables(conn);
            List<ColumnInfo> columns = queryColumns(conn);
            List<PrimaryKeyInfo> primaryKeys = queryPrimaryKeys(conn);
            List<ForeignKeyInfo> foreignKeys = queryForeignKeys(conn);
            List<UniqueConstraintInfo> uniqueConstraints = queryUniqueConstraints(conn);
            List<CheckConstraintInfo> checkConstraints = queryCheckConstraints(conn);

            logger.info("Introspection complete: {} tables, {} columns, {} PKs, {} FKs, {} unique, {} check",
                    tables.size(), columns.size(), primaryKeys.size(),
                    foreignKeys.size(), uniqueConstraints.size(), checkConstraints.size());

            return new IntrospectionResult(tables, columns, primaryKeys,
                    foreignKeys, uniqueConstraints, checkConstraints);
        }
    }

    private Connection connect() throws SQLException {
        if (username != null) {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    private List<TableInfo> queryTables(Connection conn) throws SQLException {
        List<TableInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PostgresQueries.TABLES)) {
            while (rs.next()) {
                result.add(new TableInfo(
                        rs.getString("table_schema"),
                        rs.getString("table_name")
                ));
            }
        }
        return result;
    }

    private List<ColumnInfo> queryColumns(Connection conn) throws SQLException {
        List<ColumnInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PostgresQueries.COLUMNS)) {
            while (rs.next()) {
                result.add(new ColumnInfo(
                        rs.getString("table_schema"),
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getString("udt_name"),
                        getIntOrNull(rs, "character_maximum_length"),
                        getIntOrNull(rs, "numeric_precision"),
                        getIntOrNull(rs, "numeric_scale"),
                        "YES".equals(rs.getString("is_nullable")),
                        rs.getString("column_default"),
                        rs.getInt("ordinal_position")
                ));
            }
        }
        return result;
    }

    private List<PrimaryKeyInfo> queryPrimaryKeys(Connection conn) throws SQLException {
        List<PrimaryKeyInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PostgresQueries.PRIMARY_KEYS)) {
            while (rs.next()) {
                result.add(new PrimaryKeyInfo(
                        rs.getString("table_schema"),
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        rs.getInt("ordinal_position")
                ));
            }
        }
        return result;
    }

    private List<ForeignKeyInfo> queryForeignKeys(Connection conn) throws SQLException {
        List<ForeignKeyInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PostgresQueries.FOREIGN_KEYS)) {
            while (rs.next()) {
                result.add(new ForeignKeyInfo(
                        rs.getString("child_schema"),
                        rs.getString("child_table"),
                        rs.getString("child_column"),
                        rs.getString("parent_schema"),
                        rs.getString("parent_table"),
                        rs.getString("parent_column"),
                        rs.getInt("ordinal_position")
                ));
            }
        }
        return result;
    }

    private List<UniqueConstraintInfo> queryUniqueConstraints(Connection conn) throws SQLException {
        List<UniqueConstraintInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PostgresQueries.UNIQUE_CONSTRAINTS)) {
            while (rs.next()) {
                result.add(new UniqueConstraintInfo(
                        rs.getString("table_schema"),
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        rs.getInt("ordinal_position")
                ));
            }
        }
        return result;
    }

    private List<CheckConstraintInfo> queryCheckConstraints(Connection conn) throws SQLException {
        List<CheckConstraintInfo> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(PostgresQueries.CHECK_CONSTRAINTS)) {
            while (rs.next()) {
                result.add(new CheckConstraintInfo(
                        rs.getString("table_schema"),
                        rs.getString("table_name"),
                        rs.getString("constraint_name"),
                        rs.getString("check_clause")
                ));
            }
        }
        return result;
    }

    private static Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
