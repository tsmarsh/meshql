package com.meshql.examples.legacy;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared state for legacy BDD tests.
 * PicoContainer creates a new instance per scenario, so data that must
 * persist across scenarios is stored as static fields.
 */
public class LegacyWorld {
    // API base URL (static — same for all scenarios)
    public static String apiBase;

    // Per-scenario query results (instance — reset each scenario)
    public JsonNode graphqlResult;
    public JsonNode[] graphqlResults;
}
