package com.meshql.repositories.postgres;

import com.meshql.core.Auth;
import com.meshql.repositories.rdbms.RDBMSSearcher;

import javax.sql.DataSource;

public class PostgresSearcher extends RDBMSSearcher {
    private static final String SINGLETON_QUERY_TEMPLATE = "SELECT * " +
            "FROM %s " +
            "WHERE %s " +
            "  AND created_at <= ? " +
            "  AND deleted = false " +
            "ORDER BY created_at DESC " +
            "LIMIT 1";

    /**
     * SQL template for finding multiple records.
     */
    private static final String VECTOR_QUERY_TEMPLATE = "SELECT DISTINCT ON (id) * " +
            "FROM %s " +
            "WHERE %s " +
            "  AND created_at <= ? " +
            "  AND deleted = false " +
            "ORDER BY id, created_at DESC";

    public PostgresSearcher(DataSource dataSource, String tableName, Auth authorizer) {
        super(SINGLETON_QUERY_TEMPLATE, VECTOR_QUERY_TEMPLATE, dataSource, tableName, authorizer);
    }
}
