package com.meshql.examples.egg_economy_sap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches FK -> MeshQL projection ID mappings for each projection type.
 * Bootstraps from GraphQL getAll on first access per entity type.
 */
public class ProjectionCache {
    private static final Logger logger = LoggerFactory.getLogger(ProjectionCache.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String platformUrl;
    private final HttpClient httpClient;

    private final ConcurrentHashMap<String, String> containerInventoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> henProductivityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> farmOutputCache = new ConcurrentHashMap<>();

    private volatile boolean containerInventoryBootstrapped = false;
    private volatile boolean henProductivityBootstrapped = false;
    private volatile boolean farmOutputBootstrapped = false;

    public ProjectionCache(String platformUrl) {
        this.platformUrl = platformUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public String getContainerInventoryId(String containerId) {
        if (!containerInventoryBootstrapped) {
            bootstrapContainerInventory();
        }
        return containerInventoryCache.get(containerId);
    }

    public void registerContainerInventory(String containerId, String meshqlId) {
        if (containerId != null && meshqlId != null) {
            containerInventoryCache.put(containerId, meshqlId);
        }
    }

    public String getHenProductivityId(String henId) {
        if (!henProductivityBootstrapped) {
            bootstrapHenProductivity();
        }
        return henProductivityCache.get(henId);
    }

    public void registerHenProductivity(String henId, String meshqlId) {
        if (henId != null && meshqlId != null) {
            henProductivityCache.put(henId, meshqlId);
        }
    }

    public String getFarmOutputId(String farmId) {
        if (!farmOutputBootstrapped) {
            bootstrapFarmOutput();
        }
        return farmOutputCache.get(farmId);
    }

    public void registerFarmOutput(String farmId, String meshqlId) {
        if (farmId != null && meshqlId != null) {
            farmOutputCache.put(farmId, meshqlId);
        }
    }

    private synchronized void bootstrapContainerInventory() {
        if (containerInventoryBootstrapped) return;
        populateCache("/container_inventory/graph", "container_id", containerInventoryCache);
        containerInventoryBootstrapped = true;
        logger.info("Bootstrapped container inventory cache: {} entries", containerInventoryCache.size());
    }

    private synchronized void bootstrapHenProductivity() {
        if (henProductivityBootstrapped) return;
        populateCache("/hen_productivity/graph", "hen_id", henProductivityCache);
        henProductivityBootstrapped = true;
        logger.info("Bootstrapped hen productivity cache: {} entries", henProductivityCache.size());
    }

    private synchronized void bootstrapFarmOutput() {
        if (farmOutputBootstrapped) return;
        populateCache("/farm_output/graph", "farm_id", farmOutputCache);
        farmOutputBootstrapped = true;
        logger.info("Bootstrapped farm output cache: {} entries", farmOutputCache.size());
    }

    private void populateCache(String graphPath, String fkField, ConcurrentHashMap<String, String> cache) {
        try {
            String query = "{ getAll { id " + fkField + " } }";
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + graphPath))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode data = json.path("data").path("getAll");
                if (data.isArray()) {
                    for (JsonNode item : data) {
                        String meshqlId = item.path("id").asText(null);
                        String fkValue = item.path(fkField).asText(null);
                        if (meshqlId != null && fkValue != null && !fkValue.equals("null")) {
                            cache.put(fkValue, meshqlId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to bootstrap cache from {}: {}", graphPath, e.getMessage());
        }
    }
}
