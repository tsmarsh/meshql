package com.meshql.repositories.postgres;

import com.github.jknack.handlebars.Template;
import com.meshql.repositories.rdbms.RDBMSRepository;
import com.meshql.repositories.rdbms.RequiredTemplates;
import com.tailoredshapes.underbar.ocho.UnderBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static com.tailoredshapes.underbar.ocho.Die.rethrow;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;
import static com.tailoredshapes.underbar.ocho.UnderBar.modifyValues;

public class PostgresRespository extends RDBMSRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresRespository.class);

    /**
     * Constructor for PostgresRepository.
     *
     * @param dataSource DataSource for database connections
     * @param tableName  Name of the table to use for storage
     */
    public PostgresRespository(DataSource dataSource, String tableName) {
        super(dataSource, tableName);
    }

    public RequiredTemplates initializeTemplates() {
        // Define SQL templates as strings
        Map<String, String> templateStrings = new HashMap<>();
        var createScripts = UnderBar.list(
                "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";",
                "CREATE TABLE IF NOT EXISTS {{tableName}} (" +
                        "    pk UUID DEFAULT uuid_generate_v4() PRIMARY KEY," +
                        "    id TEXT," +
                        "    payload JSONB," +
                        "    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()," +
                        "    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()," +
                        "    deleted BOOLEAN DEFAULT FALSE," +
                        "    CONSTRAINT {{tableName}}_id_created_at_uniq UNIQUE (id, created_at)" +
                        ");",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_id ON {{tableName}} (id);",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_created_at ON {{tableName}} (created_at);",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_deleted ON {{tableName}} (deleted);",
                "CREATE TABLE IF NOT EXISTS {{tableName}}_authtokens (" +
                        "    envelope_id TEXT NOT NULL," +
                        "    envelope_created_at TIMESTAMP WITH TIME ZONE NOT NULL," +
                        "    token TEXT NOT NULL," +
                        "    token_order INTEGER NOT NULL," +
                        "    CONSTRAINT {{tableName}}_authtokens_pkey PRIMARY KEY (envelope_id, envelope_created_at, token)," +
                        "    CONSTRAINT {{tableName}}_authtokens_fkey FOREIGN KEY (envelope_id, envelope_created_at) " +
                        "        REFERENCES {{tableName}} (id, created_at) ON DELETE CASCADE" +
                        ");",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_authtokens_token ON {{tableName}}_authtokens (token);",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_authtokens_order ON {{tableName}}_authtokens (envelope_id, envelope_created_at, token_order);"
        );

        templateStrings.put("insert",
                "INSERT INTO {{tableName}} (id, payload, created_at, updated_at, deleted) " +
                        "VALUES (?, ?::jsonb, NOW(), NOW(), FALSE) " +
                        "RETURNING *, NULL AS authorized_tokens;"
        );

        templateStrings.put("insertToken",
                "INSERT INTO {{tableName}}_authtokens (envelope_id, envelope_created_at, token, token_order) " +
                        "VALUES (?, ?, ?, ?);"
        );

        templateStrings.put("read",
                "SELECT e.*, " +
                        "    ARRAY(SELECT token FROM {{tableName}}_authtokens " +
                        "          WHERE envelope_id = e.id AND envelope_created_at = e.created_at " +
                        "          ORDER BY token_order) AS authorized_tokens " +
                        "FROM {{tableName}} e " +
                        "WHERE e.id = ? AND e.deleted IS FALSE AND e.created_at <= ? " +
                        "{{#if hasTokens}}AND EXISTS (SELECT 1 FROM {{tableName}}_authtokens " +
                        "                            WHERE envelope_id = e.id AND envelope_created_at = e.created_at " +
                        "                            AND token IN ({{#each tokens}}{{#unless @first}},{{/unless}}?{{/each}})){{/if}} " +
                        "ORDER BY e.created_at DESC LIMIT 1;"
        );

        templateStrings.put("readMany",
                "WITH latest_versions AS (" +
                        "    SELECT DISTINCT ON (id) * FROM {{tableName}} " +
                        "    WHERE id = ANY(string_to_array(?, ',')) AND deleted IS FALSE " +
                        "    {{#if hasTokens}}AND EXISTS (SELECT 1 FROM {{tableName}}_authtokens " +
                        "                                WHERE envelope_id = id AND envelope_created_at = created_at " +
                        "                                AND token IN ({{#each tokens}}{{#unless @first}},{{/unless}}?{{/each}})){{/if}} " +
                        "    ORDER BY id, created_at DESC" +
                        ") " +
                        "SELECT lv.*, " +
                        "    ARRAY(SELECT token FROM {{tableName}}_authtokens " +
                        "          WHERE envelope_id = lv.id AND envelope_created_at = lv.created_at " +
                        "          ORDER BY token_order) AS authorized_tokens " +
                        "FROM latest_versions lv;"
        );

        templateStrings.put("remove",
                "UPDATE {{tableName}} SET deleted = TRUE WHERE id = ? " +
                        "{{#if hasTokens}}AND EXISTS (SELECT 1 FROM {{tableName}}_authtokens " +
                        "                           WHERE envelope_id = id AND envelope_created_at = created_at " +
                        "                           AND token IN ({{#each tokens}}{{#unless @first}},{{/unless}}?{{/each}})){{/if}}"
        );

        templateStrings.put("removeMany",
                "UPDATE {{tableName}} SET deleted = TRUE WHERE id = ANY(string_to_array(?, ',')) " +
                        "{{#if hasTokens}}AND EXISTS (SELECT 1 FROM {{tableName}}_authtokens " +
                        "                           WHERE envelope_id = id AND envelope_created_at = created_at " +
                        "                           AND token IN ({{#each tokens}}{{#unless @first}},{{/unless}}?{{/each}})){{/if}}"
        );

        templateStrings.put("list",
                "WITH latest_versions AS (" +
                        "    SELECT DISTINCT ON (id) * FROM {{tableName}} " +
                        "    WHERE deleted IS FALSE " +
                        "    {{#if hasTokens}}AND EXISTS (SELECT 1 FROM {{tableName}}_authtokens " +
                        "                                WHERE envelope_id = id AND envelope_created_at = created_at " +
                        "                                AND token IN ({{#each tokens}}{{#unless @first}},{{/unless}}?{{/each}})){{/if}} " +
                        "    ORDER BY id, created_at DESC" +
                        ") " +
                        "SELECT lv.*, " +
                        "    ARRAY(SELECT token FROM {{tableName}}_authtokens " +
                        "          WHERE envelope_id = lv.id AND envelope_created_at = lv.created_at " +
                        "          ORDER BY token_order) AS authorized_tokens " +
                        "FROM latest_versions lv;"
        );

        // Compile all templates
        Map<String, Template> templateMap = modifyValues(templateStrings, (v) -> rethrow(() -> this.handlebars.compileInline(v)));

        return new RequiredTemplates(
                map(createScripts, (s) -> rethrow(() -> this.handlebars.compileInline(s))),
                templateMap.get("insert"),
                templateMap.get("insertToken"),
                templateMap.get("read"),
                templateMap.get("readMany"),
                templateMap.get("remove"),
                templateMap.get("removeMany"),
                templateMap.get("list")
        );
    }
}
