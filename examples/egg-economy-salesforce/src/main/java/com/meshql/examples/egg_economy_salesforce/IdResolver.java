package com.meshql.examples.egg_economy_salesforce;

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
 * Resolves Salesforce 18-char IDs to MeshQL entity IDs by querying
 * the GraphQL API. Results are cached in ConcurrentHashMaps.
 */
public class IdResolver {
    private static final Logger logger = LoggerFactory.getLogger(IdResolver.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String platformUrl;
    private final HttpClient httpClient;

    // SF Id -> meshql farm id
    private final ConcurrentHashMap<String, String> farmCache = new ConcurrentHashMap<>();
    // SF Id -> meshql coop id
    private final ConcurrentHashMap<String, String> coopCache = new ConcurrentHashMap<>();
    // SF Id -> meshql hen id
    private final ConcurrentHashMap<String, String> henCache = new ConcurrentHashMap<>();
    // SF Id -> meshql container id
    private final ConcurrentHashMap<String, String> containerCache = new ConcurrentHashMap<>();
    // SF Id -> meshql consumer id
    private final ConcurrentHashMap<String, String> consumerCache = new ConcurrentHashMap<>();

    // hen SF Id -> coop meshql id
    private final ConcurrentHashMap<String, String> henToCoopCache = new ConcurrentHashMap<>();

    public IdResolver(String platformUrl) {
        this.platformUrl = platformUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void registerFarm(String sfId, String meshqlId) {
        if (sfId != null && meshqlId != null) farmCache.put(sfId, meshqlId);
    }

    public void registerCoop(String sfId, String meshqlId) {
        if (sfId != null && meshqlId != null) coopCache.put(sfId, meshqlId);
    }

    public void registerHen(String sfId, String meshqlId, String coopMeshqlId) {
        if (sfId != null && meshqlId != null) henCache.put(sfId, meshqlId);
        if (sfId != null && coopMeshqlId != null) henToCoopCache.put(sfId, coopMeshqlId);
    }

    public void registerContainer(String sfId, String meshqlId) {
        if (sfId != null && meshqlId != null) containerCache.put(sfId, meshqlId);
    }

    public void registerConsumer(String sfId, String meshqlId) {
        if (sfId != null && meshqlId != null) consumerCache.put(sfId, meshqlId);
    }

    public String resolveFarmId(String sfId) {
        return sfId == null ? null : farmCache.get(sfId);
    }

    public String resolveCoopId(String sfId) {
        return sfId == null ? null : coopCache.get(sfId);
    }

    public String resolveHenId(String sfId) {
        return sfId == null ? null : henCache.get(sfId);
    }

    public String resolveCoopIdForHen(String henSfId) {
        return henSfId == null ? null : henToCoopCache.get(henSfId);
    }

    public String resolveContainerId(String sfId) {
        return sfId == null ? null : containerCache.get(sfId);
    }

    public String resolveConsumerId(String sfId) {
        return sfId == null ? null : consumerCache.get(sfId);
    }

    public String resolveFarmIdForContainer(String containerSfId) {
        if (farmCache.isEmpty()) return null;
        return farmCache.values().iterator().next();
    }

    public int farmCacheSize() { return farmCache.size(); }
    public int coopCacheSize() { return coopCache.size(); }
    public int henCacheSize() { return henCache.size(); }
    public int containerCacheSize() { return containerCache.size(); }
    public int consumerCacheSize() { return consumerCache.size(); }

    public void populateFarmCacheFromGraphQL() {
        populateCache("/farm/graph", "legacy_sf_id", farmCache, "farm");
    }

    public void populateCoopCacheFromGraphQL() {
        populateCache("/coop/graph", "legacy_sf_id", coopCache, "coop");
    }

    public void populateHenCacheFromGraphQL() {
        populateCache("/hen/graph", "legacy_sf_id", henCache, "hen");
        populateHenToCoopCache();
    }

    public void populateContainerCacheFromGraphQL() {
        populateCache("/container/graph", "legacy_sf_id", containerCache, "container");
    }

    public void populateConsumerCacheFromGraphQL() {
        populateCache("/consumer/graph", "legacy_sf_id", consumerCache, "consumer");
    }

    private void populateCache(String graphPath, String legacyIdField,
                                ConcurrentHashMap<String, String> cache, String entityName) {
        try {
            String query = "{ getAll { id " + legacyIdField + " } }";
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
                        String legacyId = item.path(legacyIdField).asText(null);
                        if (meshqlId != null && legacyId != null && !legacyId.equals("null")) {
                            cache.put(legacyId, meshqlId);
                        }
                    }
                }
            }
            logger.info("Populated {} cache from GraphQL: {} entries", entityName, cache.size());
        } catch (Exception e) {
            logger.error("Failed to populate {} cache from GraphQL: {}", entityName, e.getMessage(), e);
        }
    }

    private void populateHenToCoopCache() {
        try {
            String query = "{ getAll { id legacy_sf_id coop_id } }";
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + "/hen/graph"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode data = json.path("data").path("getAll");
                if (data.isArray()) {
                    for (JsonNode item : data) {
                        String sfId = item.path("legacy_sf_id").asText(null);
                        String coopId = item.path("coop_id").asText(null);
                        if (sfId != null && coopId != null && !coopId.equals("null")) {
                            henToCoopCache.put(sfId, coopId);
                        }
                    }
                }
            }
            logger.info("Populated hen-to-coop cache: {} entries", henToCoopCache.size());
        } catch (Exception e) {
            logger.error("Failed to populate hen-to-coop cache: {}", e.getMessage(), e);
        }
    }
}
