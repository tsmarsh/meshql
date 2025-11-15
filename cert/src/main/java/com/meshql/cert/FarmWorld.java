package com.meshql.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.meshql.core.Plugin;
import com.meshql.core.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared state for farm certification tests.
 * Matches TypeScript FarmTestWorld pattern - lightweight with references to shared FarmEnv.
 */
public class FarmWorld {
    // Reference to shared environment (persistent across scenarios)
    public FarmEnv env;

    // Server reference (shared, created in BeforeAll)
    public Object server;

    // Legacy fields for compatibility with repositories map
    public Map<String, Repository> repositories = new HashMap<>();
    public Map<String, Plugin> plugins = new HashMap<>();

    // Per-scenario state (reset each scenario)
    public JsonNode queryResult;
    public JsonNode[] queryResults;
    public Long now;

    // Convenience accessors that delegate to env
    public String getBaseUrl() {
        return env != null ? env.getPlatformUrl() : null;
    }

    public int getPort() {
        return env != null ? env.getPort() : 0;
    }
}
