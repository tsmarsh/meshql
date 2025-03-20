package com.meshql.repositories.postgres;

import com.github.jknack.handlebars.Handlebars;
import com.meshql.core.Auth;
import com.meshql.repositories.rdbms.RDBMSSearcher;

import javax.sql.DataSource;

import java.sql.Timestamp;

import static com.tailoredshapes.underbar.ocho.Die.rethrow;

public class PostgresSearcher extends RDBMSSearcher {
    private  static Handlebars handlebars = new Handlebars();
    private static final String SINGLETON_QUERY_TEMPLATE = "SELECT e.*, " +
            "    ARRAY(SELECT token FROM {{_name}}_authtokens " +
            "          WHERE envelope_id = e.id AND envelope_created_at = e.created_at " +
            "          ORDER BY token_order) AS authorized_tokens " +
            "FROM {{_name}} e " +
            "WHERE {{{filters}}} " +
            "  AND e.created_at <= '{{_createdAt}}' " +
            "  AND e.deleted = false " +
            "ORDER BY e.created_at DESC " +
            "LIMIT 1";

    /**
     * SQL template for finding multiple records.
     */
    private static final String VECTOR_QUERY_TEMPLATE = "WITH latest_versions AS (" +
            "    SELECT DISTINCT ON (id) * FROM {{_name}} " +
            "    WHERE {{{filters}}}" +
            "      AND created_at <= '{{_createdAt}}' " +
            "      AND deleted = false " +
            "    ORDER BY id, created_at DESC" +
            ") " +
            "SELECT lv.*, " +
            "    ARRAY(SELECT token FROM {{_name}}_authtokens " +
            "          WHERE envelope_id = lv.id AND envelope_created_at = lv.created_at " +
            "          ORDER BY token_order) AS authorized_tokens " +
            "FROM latest_versions lv";

    public PostgresSearcher(DataSource dataSource, String tableName, Auth authorizer) {
        super(rethrow(() -> handlebars.compileInline(SINGLETON_QUERY_TEMPLATE)), rethrow(() -> handlebars.compileInline(VECTOR_QUERY_TEMPLATE)), dataSource, tableName, authorizer, (t) -> new Timestamp(t).toInstant());
    }
}
