package com.meshql.repositories.postgres;

import com.github.jknack.handlebars.Template;
import com.meshql.repositories.rdbms.RDBMSRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static com.tailoredshapes.underbar.ocho.Die.rethrow;

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
        templateStrings.put("createExtension", "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";");

        templateStrings.put("createTable",
                "CREATE TABLE IF NOT EXISTS {{tableName}} (" +
                        "    pk UUID DEFAULT uuid_generate_v4() PRIMARY KEY," +
                        "    id TEXT," +
                        "    payload JSONB," +
                        "    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()," +
                        "    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()," +
                        "    deleted BOOLEAN DEFAULT FALSE," +
                        "    authorized_tokens TEXT[]," +
                        "    CONSTRAINT {{tableName}}_id_created_at_uniq UNIQUE (id, created_at)" +
                        ");"
        );

        templateStrings.put("createIdIndex",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_id ON {{tableName}} (id);"
        );

        templateStrings.put("createCreatedAtIndex",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_created_at ON {{tableName}} (created_at);"
        );

        templateStrings.put("createDeletedIndex",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_deleted ON {{tableName}} (deleted);"
        );

        templateStrings.put("createTokensIndex",
                "CREATE INDEX IF NOT EXISTS idx_{{tableName}}_tokens ON {{tableName}} USING GIN (authorized_tokens);"
        );

        templateStrings.put("insert",
                "INSERT INTO {{tableName}} (id, payload, created_at, updated_at, deleted, authorized_tokens) " +
                        "VALUES (?, ?::jsonb, NOW(), NOW(), FALSE, ?) " +
                        "RETURNING *;"
        );

        templateStrings.put("read",
                "SELECT * FROM {{tableName}} WHERE id = ? AND deleted IS FALSE AND created_at <= ? " +
                        "{{#if hasTokens}}AND authorized_tokens && ?{{/if}} " +
                        "ORDER BY created_at DESC LIMIT 1;"
        );

        templateStrings.put("readMany",
                "SELECT DISTINCT ON (id) * FROM {{tableName}} WHERE id = ANY(?) AND deleted IS FALSE " +
                        "{{#if hasTokens}}AND authorized_tokens && ?{{/if}} " +
                        "ORDER BY id, created_at DESC;"
        );

        templateStrings.put("remove",
                "UPDATE {{tableName}} SET deleted = TRUE WHERE id = ? " +
                        "{{#if hasTokens}}AND authorized_tokens && ?{{/if}}"
        );

        templateStrings.put("removeMany",
                "UPDATE {{tableName}} SET deleted = TRUE WHERE id = ANY(?) " +
                        "{{#if hasTokens}}AND authorized_tokens && ?{{/if}}"
        );

        templateStrings.put("list",
                "SELECT DISTINCT ON (id) * FROM {{tableName}} WHERE deleted IS FALSE " +
                        "{{#if hasTokens}}AND authorized_tokens && ?{{/if}} " +
                        "ORDER BY id, created_at DESC;"
        );

        // Compile all templates
        Map<String, Template> templateMap = modifyValues(templateStrings, (v) -> rethrow(() -> this.handlebars.compileInline(v)));
        return new RequiredTemplates(
                templateMap.get("createExtension"),
                templateMap.get("createTable"),
                templateMap.get("createIdIndex"),
                templateMap.get("createCreatedAtIndex"),
                templateMap.get("createDeletedIndex"),
                templateMap.get("createTokensIndex"),
                templateMap.get("insert"),
                templateMap.get("read"),
                templateMap.get("readMany"),
                templateMap.get("remove"),
                templateMap.get("removeMany"),
                templateMap.get("list")
        );
    }
}
