package com.meshql.mesher.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meshql.mesher.introspect.IntrospectionResult;

/**
 * Builds the conversion prompt that asks Claude to produce a domain model IR
 * from introspection data.
 */
public final class DomainModelPrompt {
    private DomainModelPrompt() {}

    public static String build(IntrospectionResult introspection, String projectName,
                               String packageName, int port) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String introspectionJson = mapper.writeValueAsString(introspection);

            return PROMPT_TEMPLATE
                    .replace("{{INTROSPECTION_JSON}}", introspectionJson)
                    .replace("{{PROJECT_NAME}}", projectName)
                    .replace("{{PACKAGE_NAME}}", packageName)
                    .replace("{{PORT}}", String.valueOf(port));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build prompt", e);
        }
    }

    private static final String PROMPT_TEMPLATE = """
            You are a database modernization expert. Analyze the following PostgreSQL database introspection data and produce a domain model JSON that maps legacy tables to clean domain entities.

            ## Introspection Data

            ```json
            {{INTROSPECTION_JSON}}
            ```

            ## Project Configuration

            - Project name: {{PROJECT_NAME}}
            - Java package: {{PACKAGE_NAME}}
            - Server port: {{PORT}}
            - Prefix: derived from project name (use hyphens to underscores)
            - Legacy DB name: derived from project name (use hyphens to underscores)

            ## Your Task

            Produce a JSON domain model with the following structure. Return ONLY valid JSON, no other text.

            For each legacy table, create a clean entity:
            1. **Naming**: Convert cryptic legacy names to clean, readable names (e.g., `cust_acct` → `customer`, `mtr_rdng` → `meter_reading`)
            2. **Fields**: Map each column to a clean field with appropriate transformation
            3. **Relationships**: Detect parent/child relationships from foreign keys
            4. **Processing phases**: Order entities so parents are processed before children (for FK resolution)
            5. **Code maps**: Detect code columns (short VARCHAR with limited values from check constraints or naming patterns like `_cd`, `_type`, `_flg`) and provide expansion mappings

            ## Field Transformations

            Assign one of these transformations to each field:
            - `direct` — copy as-is
            - `titleCase` — convert UPPERCASE to Title Case (for names, addresses)
            - `toLower` — convert to lowercase (for emails)
            - `parseDate` — convert YYYYMMDD VARCHAR to ISO date (YYYY-MM-DD)
            - `flagToBoolean` — convert Y/N flag to true/false
            - `centsToDouble` — convert integer cents to float dollars (divide by 100)
            - `codeMap` — expand coded value using the codeMaps entry

            ## Queries

            For each entity, generate:
            - Singleton queries: `getById` (always), plus any by unique fields
            - Vector queries: `getAll` (always), plus `getByCustomer`/`getByParent` for child entities, `getByStatus` if status field exists

            Query templates use MongoDB format: `{"id": "{{id}}"}` for getById, `{"payload.field": "{{param}}"}` for filtered queries, `{}` for getAll.

            ## Required JSON Schema

            ```json
            {
              "projectName": "string",
              "package": "string",
              "port": number,
              "prefix": "string",
              "legacyDbName": "string",
              "entities": [{
                "legacyTable": "string (original table name, lowercase)",
                "cleanName": "string (snake_case)",
                "className": "string (PascalCase)",
                "legacyPrimaryKey": "string (original PK column name, lowercase)",
                "legacyIdField": "string (clean name for legacy ID, e.g. legacy_acct_id)",
                "isRoot": boolean,
                "processingPhase": number,
                "fields": [{
                  "legacyName": "string (original column name, lowercase)",
                  "cleanName": "string (snake_case)",
                  "legacyType": "string (SQL type)",
                  "cleanType": "string (Java type: String, int, double, boolean)",
                  "graphqlType": "string (GraphQL type: String, Int, Float, Boolean, ID)",
                  "jsonSchemaType": "string (JSON Schema type: string, integer, number, boolean)",
                  "required": boolean,
                  "transformation": "string (one of: direct, titleCase, toLower, parseDate, flagToBoolean, centsToDouble, codeMap)"
                }],
                "codeMaps": [{
                  "legacyColumn": "string (original column name, lowercase)",
                  "cleanField": "string (clean field name)",
                  "mapping": {"CODE": "expanded_value"}
                }],
                "relationships": {
                  "children": [{"targetEntity": "string", "fieldName": "string", "foreignKeyInChild": "string", "queryName": "string"}],
                  "parents": [{"targetEntity": "string", "fieldName": "string", "foreignKeyInChild": "string", "queryName": "string"}]
                },
                "queries": {
                  "singletons": [{"name": "string", "template": "string"}],
                  "vectors": [{"name": "string", "template": "string"}]
                }
              }],
              "processingPhases": [{
                "phase": number,
                "entities": ["string"],
                "cachePopulation": ["string (entities whose caches need populating after this phase)"]
              }]
            }
            ```

            ## Important Rules

            1. Root entities (no FK to other tables) are phase 1, isRoot=true
            2. Entities with FK only to phase-1 entities are phase 2
            3. Entities with FK to phase-2 entities are phase 3, etc.
            4. Each entity should preserve its legacy primary key as a field (e.g., `legacy_acct_id`) for FK resolution
            5. Child entities should have a `customer_id` or `{parent}_id` field (clean name) that will hold the MeshQL UUID
            6. The `foreignKeyInChild` in relationships refers to the clean field name (e.g., `customer_id`)
            7. Don't include the primary key column in the fields list — it's tracked via `legacyPrimaryKey` and `legacyIdField`
            8. For FK columns, don't include them directly — they're handled via relationships and ID resolution
            9. cachePopulation lists entities whose legacy→meshql ID caches should be populated after the phase completes

            Return ONLY the JSON. No markdown, no explanation.
            """;
}
