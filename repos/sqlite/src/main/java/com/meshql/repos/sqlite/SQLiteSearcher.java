package com.meshql.repos.sqlite;

import com.meshql.core.Auth;
import com.meshql.repositories.rdbms.RDBMSSearcher;

import javax.sql.DataSource;

public class SQLiteSearcher extends RDBMSSearcher {
    private static final String SINGLETON_QUERY_TEMPLATE = "        SELECT *\n" +
            "        FROM {{_name}}\n" +
            "        WHERE {{{filters}}}\n" +
            "         AND created_at <= {{_created_at}}\n" +
            "        ORDER BY created_at DESC\n" +
            "        LIMIT 1";

    /**
     * SQL template for finding multiple records.
     */
    private static final String VECTOR_QUERY_TEMPLATE = "WITH latest AS (\n" +
            "        SELECT\n" +
            "            id,\n" +
            "            MAX(created_at) AS max_created_at\n" +
            "        FROM {{_name}}\n" +
            "        WHERE {{{filters}}}\n" +
            "            AND created_at <= {{_created_at}}\n" +
            "            AND deleted = 0\n" +
            "        GROUP BY id\n" +
            "        )\n" +
            "        SELECT t1.*\n" +
            "        FROM {{_name}} t1\n" +
            "        JOIN latest t2\n" +
            "        ON t1.id = t2.id\n" +
            "        AND t1.created_at = t2.max_created_at\n" +
            "        WHERE t1.deleted = 0";

    public SQLiteSearcher(DataSource dataSource, String tableName, Auth authorizer) {
        super(SINGLETON_QUERY_TEMPLATE, VECTOR_QUERY_TEMPLATE, dataSource, tableName, authorizer);
    }
}
