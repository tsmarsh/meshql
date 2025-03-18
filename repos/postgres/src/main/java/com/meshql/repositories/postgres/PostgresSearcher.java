package com.meshql.repositories.postgres;

import com.meshql.core.Auth;
import com.meshql.repositories.rdbms.RDBMSSearcher;

import javax.sql.DataSource;

public class PostgresSearcher extends RDBMSSearcher {
    private static final String SINGLETON_QUERY_TEMPLATE = "SELECT e.*, " +
            "    ARRAY(SELECT token FROM %s_authtokens " +
            "          WHERE envelope_id = e.id AND envelope_created_at = e.created_at " +
            "          ORDER BY token_order) AS authorized_tokens " +
            "FROM %s e " +
            "WHERE %s " +
            "  AND e.created_at <= ? " +
            "  AND e.deleted = false " +
            "ORDER BY e.created_at DESC " +
            "LIMIT 1";

    /**
     * SQL template for finding multiple records.
     */
    private static final String VECTOR_QUERY_TEMPLATE = "WITH latest_versions AS (" +
            "    SELECT DISTINCT ON (id) * FROM %s " +
            "    WHERE %s " +
            "      AND created_at <= ? " +
            "      AND deleted = false " +
            "    ORDER BY id, created_at DESC" +
            ") " +
            "SELECT lv.*, " +
            "    ARRAY(SELECT token FROM %s_authtokens " +
            "          WHERE envelope_id = lv.id AND envelope_created_at = lv.created_at " +
            "          ORDER BY token_order) AS authorized_tokens " +
            "FROM latest_versions lv";

    public PostgresSearcher(DataSource dataSource, String tableName, Auth authorizer) {
        super(SINGLETON_QUERY_TEMPLATE, VECTOR_QUERY_TEMPLATE, dataSource, tableName, authorizer);
    }
}
