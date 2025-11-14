package com.meshql.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.meshql.core.Plugin;
import com.meshql.core.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared state for farm certification tests.
 * This class holds the server instance, API clients, entity IDs, and query results.
 */
public class FarmWorld {
    // Server configuration
    public Map<String, Plugin> plugins = new HashMap<>();
    public Map<String, Repository> repositories = new HashMap<>();
    public Object server; // Concrete type depends on implementation
    public int port = 4044;
    public String baseUrl;

    // Test state - entity IDs organized by type and name
    // e.g., ids.get("farm").get("Emerdale") = "uuid-123"
    public Map<String, Map<String, String>> ids = new HashMap<>();

    // Timestamp tracking for temporal tests
    public Long firstStamp;
    public Long now;

    // Query results
    public JsonNode queryResult;
    public JsonNode[] queryResults;

    public FarmWorld() {
        baseUrl = "http://localhost:" + port;

        // Initialize ID maps for each entity type
        ids.put("farm", new HashMap<>());
        ids.put("coop", new HashMap<>());
        ids.put("hen", new HashMap<>());
    }
}
