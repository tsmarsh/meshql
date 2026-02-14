package com.meshql.examples.enterprise;

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
 * Resolves legacy IDs from both SAP and distribution systems to MeshQL entity IDs.
 * SAP: WERKS, STALL_NR, EQUNR (farm, coop, hen)
 * Distro: integer PKs (container, consumer)
 */
public class IdResolver {
    private static final Logger logger = LoggerFactory.getLogger(IdResolver.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String platformUrl;
    private final HttpClient httpClient;

    // SAP caches
    private final ConcurrentHashMap<String, String> farmCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> coopCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> henCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> henToCoopCache = new ConcurrentHashMap<>();

    // Distro caches
    private final ConcurrentHashMap<String, String> distroContainerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> distroConsumerCache = new ConcurrentHashMap<>();

    public IdResolver(String platformUrl) {
        this.platformUrl = platformUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    // --- SAP register methods ---

    public void registerFarm(String legacyWerks, String meshqlId) {
        if (legacyWerks != null && meshqlId != null) farmCache.put(legacyWerks, meshqlId);
    }

    public void registerCoop(String legacyStallNr, String meshqlId) {
        if (legacyStallNr != null && meshqlId != null) coopCache.put(legacyStallNr, meshqlId);
    }

    public void registerHen(String legacyEqunr, String meshqlId, String coopMeshqlId) {
        if (legacyEqunr != null && meshqlId != null) henCache.put(legacyEqunr, meshqlId);
        if (legacyEqunr != null && coopMeshqlId != null) henToCoopCache.put(legacyEqunr, coopMeshqlId);
    }

    // --- Distro register methods ---

    public void registerDistroContainer(String distroId, String meshqlId) {
        if (distroId != null && meshqlId != null) distroContainerCache.put(distroId, meshqlId);
    }

    public void registerDistroConsumer(String distroId, String meshqlId) {
        if (distroId != null && meshqlId != null) distroConsumerCache.put(distroId, meshqlId);
    }

    // --- SAP resolve methods ---

    public String resolveFarmId(String legacyWerks) {
        return legacyWerks == null ? null : farmCache.get(legacyWerks);
    }

    public String resolveCoopId(String legacyStallNr) {
        return legacyStallNr == null ? null : coopCache.get(legacyStallNr);
    }

    public String resolveHenId(String legacyEqunr) {
        return legacyEqunr == null ? null : henCache.get(legacyEqunr);
    }

    public String resolveCoopIdForHen(String legacyEqunr) {
        return legacyEqunr == null ? null : henToCoopCache.get(legacyEqunr);
    }

    public String resolveFarmIdForContainer(String legacyBehaeltNr) {
        if (farmCache.isEmpty()) return null;
        return farmCache.values().iterator().next();
    }

    // --- Distro resolve methods ---

    public String resolveDistroContainerId(String distroId) {
        return distroId == null ? null : distroContainerCache.get(distroId);
    }

    public String resolveDistroConsumerId(String distroId) {
        return distroId == null ? null : distroConsumerCache.get(distroId);
    }

    // --- Cache sizes ---

    public int farmCacheSize() { return farmCache.size(); }
    public int coopCacheSize() { return coopCache.size(); }
    public int henCacheSize() { return henCache.size(); }
    public int distroContainerCacheSize() { return distroContainerCache.size(); }
    public int distroConsumerCacheSize() { return distroConsumerCache.size(); }

    // --- Populate SAP caches from GraphQL ---

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

    // --- Populate distro caches from GraphQL ---

    public void populateDistroContainerCacheFromGraphQL() {
        populateCache("/container/graph", "legacy_distro_id", distroContainerCache, "distro_container");
    }

    public void populateDistroConsumerCacheFromGraphQL() {
        populateCache("/consumer/graph", "legacy_distro_id", distroConsumerCache, "distro_consumer");
    }

    // --- Check if legacy ID exists (for dedup) ---

    public boolean hasFarmMapping(String legacyWerks) {
        return legacyWerks != null && farmCache.containsKey(legacyWerks);
    }

    public boolean hasDistroContainerMapping(String distroId) {
        return distroId != null && distroContainerCache.containsKey(distroId);
    }

    public boolean hasDistroConsumerMapping(String distroId) {
        return distroId != null && distroConsumerCache.containsKey(distroId);
    }

    // --- Internal ---

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
