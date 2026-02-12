package com.meshql.examples.logistics;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared state for logistics BDD tests.
 * PicoContainer creates a new instance per scenario, so entity IDs
 * that must persist across scenarios are stored as static fields.
 * Per-scenario query results are instance fields.
 */
public class LogisticsWorld {
    // API base URL (static — same for all scenarios)
    public static String apiBase;

    // Entity IDs created during setup (static — persist across scenarios)
    public static String warehouseId1;
    public static String warehouseId2;
    public static String shipmentId1;
    public static String shipmentId2;
    public static String packageId1;
    public static String packageId2;
    public static Map<String, String> trackingUpdateIds = new HashMap<>();

    // Per-scenario query results (instance — reset each scenario)
    public JsonNode restResponse;
    public JsonNode graphqlResult;
    public JsonNode[] graphqlResults;
}
