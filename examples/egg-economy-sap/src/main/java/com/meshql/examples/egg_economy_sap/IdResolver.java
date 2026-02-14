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
 * Resolves SAP legacy IDs (WERKS, STALL_NR, EQUNR, BEHAELT_NR, KUNNR) to MeshQL entity IDs
 * by querying the GraphQL API. Results are cached in ConcurrentHashMaps.
 */
public class IdResolver {
    private static final Logger logger = LoggerFactory.getLogger(IdResolver.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String platformUrl;
    private final HttpClient httpClient;

    // legacy WERKS -> meshql farm id
    private final ConcurrentHashMap<String, String> farmCache = new ConcurrentHashMap<>();
    // legacy STALL_NR -> meshql coop id
    private final ConcurrentHashMap<String, String> coopCache = new ConcurrentHashMap<>();
    // legacy EQUNR -> meshql hen id
    private final ConcurrentHashMap<String, String> henCache = new ConcurrentHashMap<>();
    // legacy BEHAELT_NR -> meshql container id
    private final ConcurrentHashMap<String, String> containerCache = new ConcurrentHashMap<>();
    // legacy KUNNR -> meshql consumer id
    private final ConcurrentHashMap<String, String> consumerCache = new ConcurrentHashMap<>();

    // hen EQUNR -> coop meshql id (for lay report coop_id resolution)
    private final ConcurrentHashMap<String, String> henToCoopCache = new ConcurrentHashMap<>();

    public IdResolver(String platformUrl) {
        this.platformUrl = platformUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    // --- Register methods (called after successful POST) ---

    public void registerFarm(String legacyWerks, String meshqlId) {
        if (legacyWerks != null && meshqlId != null) {
            farmCache.put(legacyWerks, meshqlId);
        }
    }

    public void registerCoop(String legacyStallNr, String meshqlId) {
        if (legacyStallNr != null && meshqlId != null) {
            coopCache.put(legacyStallNr, meshqlId);
        }
    }

    public void registerHen(String legacyEqunr, String meshqlId, String coopMeshqlId) {
        if (legacyEqunr != null && meshqlId != null) {
            henCache.put(legacyEqunr, meshqlId);
        }
        if (legacyEqunr != null && coopMeshqlId != null) {
            henToCoopCache.put(legacyEqunr, coopMeshqlId);
        }
    }

    public void registerContainer(String legacyBehaeltNr, String meshqlId) {
        if (legacyBehaeltNr != null && meshqlId != null) {
            containerCache.put(legacyBehaeltNr, meshqlId);
        }
    }

    public void registerConsumer(String legacyKunnr, String meshqlId) {
        if (legacyKunnr != null && meshqlId != null) {
            consumerCache.put(legacyKunnr, meshqlId);
        }
    }

    // --- Resolve methods ---

    public String resolveFarmId(String legacyWerks) {
        if (legacyWerks == null) return null;
        return farmCache.get(legacyWerks);
    }

    public String resolveCoopId(String legacyStallNr) {
        if (legacyStallNr == null) return null;
        return coopCache.get(legacyStallNr);
    }

    public String resolveHenId(String legacyEqunr) {
        if (legacyEqunr == null) return null;
        return henCache.get(legacyEqunr);
    }

    public String resolveCoopIdForHen(String legacyEqunr) {
        if (legacyEqunr == null) return null;
        return henToCoopCache.get(legacyEqunr);
    }

    public String resolveContainerId(String legacyBehaeltNr) {
        if (legacyBehaeltNr == null) return null;
        return containerCache.get(legacyBehaeltNr);
    }

    public String resolveConsumerId(String legacyKunnr) {
        if (legacyKunnr == null) return null;
        return consumerCache.get(legacyKunnr);
    }

    /**
     * For storage deposit source_id resolution: find a farm associated with a container's zone.
     * Simplified: returns the first farm in cache, or null.
     */
    public String resolveFarmIdForContainer(String legacyBehaeltNr) {
        if (farmCache.isEmpty()) return null;
        return farmCache.values().iterator().next();
    }

    // --- Cache size methods ---

    public int farmCacheSize() { return farmCache.size(); }
    public int coopCacheSize() { return coopCache.size(); }
    public int henCacheSize() { return henCache.size(); }
    public int containerCacheSize() { return containerCache.size(); }
    public int consumerCacheSize() { return consumerCache.size(); }

    // --- Populate caches from GraphQL (after phased processing) ---

    public void populateFarmCacheFromGraphQL() {
        populateCache("/farm/graph", "legacy_werks", farmCache, "farm");
    }

    public void populateCoopCacheFromGraphQL() {
        populateCache("/coop/graph", "legacy_stall_nr", coopCache, "coop");
    }

    public void populateHenCacheFromGraphQL() {
        populateCache("/hen/graph", "legacy_equnr", henCache, "hen");
        populateHenToCoopCache();
    }

    public void populateContainerCacheFromGraphQL() {
        populateCache("/container/graph", "legacy_behaelt_nr", containerCache, "container");
    }

    public void populateConsumerCacheFromGraphQL() {
        populateCache("/consumer/graph", "legacy_kunnr", consumerCache, "consumer");
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
            String query = "{ getAll { id legacy_equnr coop_id } }";
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
                        String legacyEqunr = item.path("legacy_equnr").asText(null);
                        String coopId = item.path("coop_id").asText(null);
                        if (legacyEqunr != null && coopId != null && !coopId.equals("null")) {
                            henToCoopCache.put(legacyEqunr, coopId);
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
