package com.meshql.cert;

/**
 * Holds database-specific query templates for Farm certification tests.
 * Queries vary by database type (MongoDB uses JSON, PostgreSQL/MySQL use SQL).
 */
public class FarmQueries {
    public final String farmById;
    public final String coopById;
    public final String coopByName;
    public final String coopByFarmId;
    public final String henById;
    public final String henByName;
    public final String henByCoopId;

    public FarmQueries(
            String farmById,
            String coopById,
            String coopByName,
            String coopByFarmId,
            String henById,
            String henByName,
            String henByCoopId
    ) {
        this.farmById = farmById;
        this.coopById = coopById;
        this.coopByName = coopByName;
        this.coopByFarmId = coopByFarmId;
        this.henById = henById;
        this.henByName = henByName;
        this.henByCoopId = henByCoopId;
    }

    /**
     * MongoDB query templates (JSON syntax).
     */
    public static FarmQueries mongoQueries() {
        return new FarmQueries(
                "{\"id\": \"{{id}}\"}",
                "{\"id\": \"{{id}}\"}",
                "{\"payload.name\": \"{{id}}\"}",
                "{\"payload.farm_id\": \"{{id}}\"}",
                "{\"id\": \"{{id}}\"}",
                "{\"payload.name\": \"{{name}}\"}",
                "{\"payload.coop_id\": \"{{id}}\"}"
        );
    }

    /**
     * PostgreSQL query templates (SQL syntax with JSONB operators).
     */
    public static FarmQueries postgresQueries() {
        return new FarmQueries(
                "id = '{{id}}'",
                "id = '{{id}}'",
                "payload->>'name' = '{{id}}'",
                "payload->>'farm_id' = '{{id}}'",
                "id = '{{id}}'",
                "payload->>'name' = '{{name}}'",
                "payload->>'coop_id' = '{{id}}'"
        );
    }

    /**
     * MySQL query templates (SQL syntax with JSON operators).
     */
    public static FarmQueries mysqlQueries() {
        return new FarmQueries(
                "id = '{{id}}'",
                "id = '{{id}}'",
                "JSON_EXTRACT(payload, '$.name') = '{{id}}'",
                "JSON_EXTRACT(payload, '$.farm_id') = '{{id}}'",
                "id = '{{id}}'",
                "JSON_EXTRACT(payload, '$.name') = '{{name}}'",
                "JSON_EXTRACT(payload, '$.coop_id') = '{{id}}'"
        );
    }

    /**
     * SQLite query templates (SQL syntax with JSON operators).
     */
    public static FarmQueries sqliteQueries() {
        return new FarmQueries(
                "id = '{{id}}'",
                "id = '{{id}}'",
                "json_extract(payload, '$.name') = '{{id}}'",
                "json_extract(payload, '$.farm_id') = '{{id}}'",
                "id = '{{id}}'",
                "json_extract(payload, '$.name') = '{{name}}'",
                "json_extract(payload, '$.coop_id') = '{{id}}'"
        );
    }
}
