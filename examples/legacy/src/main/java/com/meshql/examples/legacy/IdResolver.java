package com.meshql.examples.legacy;

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
 * Resolves legacy IDs (ACCT_ID, BILL_ID) to MeshQL entity IDs by querying
 * the GraphQL API. Results are cached in ConcurrentHashMaps.
 */
public class IdResolver {
    private static final Logger logger = LoggerFactory.getLogger(IdResolver.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String platformUrl;
    private final HttpClient httpClient;

    // legacy acct_id -> meshql customer id
    private final ConcurrentHashMap<String, String> customerCache = new ConcurrentHashMap<>();
    // legacy bill_id -> meshql bill id
    private final ConcurrentHashMap<String, String> billCache = new ConcurrentHashMap<>();

    public IdResolver(String platformUrl) {
        this.platformUrl = platformUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Register a customer mapping after a successful POST.
     * Called by the processor after creating a customer via REST.
     */
    public void registerCustomer(String legacyAcctId, String meshqlId) {
        if (legacyAcctId != null && meshqlId != null) {
            customerCache.put(legacyAcctId, meshqlId);
            logger.debug("Registered customer mapping: {} -> {}", legacyAcctId, meshqlId);
        }
    }

    /**
     * Register a bill mapping after a successful POST.
     */
    public void registerBill(String legacyBillId, String meshqlId) {
        if (legacyBillId != null && meshqlId != null) {
            billCache.put(legacyBillId, meshqlId);
            logger.debug("Registered bill mapping: {} -> {}", legacyBillId, meshqlId);
        }
    }

    /**
     * Resolve a legacy ACCT_ID to a MeshQL customer ID.
     * First checks the cache, then queries GraphQL.
     */
    public String resolveCustomerId(String legacyAcctId) {
        if (legacyAcctId == null) return null;
        return customerCache.get(legacyAcctId);
    }

    /**
     * Resolve a legacy BILL_ID to a MeshQL bill ID.
     * First checks the cache, then queries GraphQL.
     */
    public String resolveBillId(String legacyBillId) {
        if (legacyBillId == null) return null;
        return billCache.get(legacyBillId);
    }

    public int customerCacheSize() {
        return customerCache.size();
    }

    public int billCacheSize() {
        return billCache.size();
    }

    /**
     * After all customers have been POSTed, query GraphQL to populate the
     * legacy_acct_id -> meshql_id cache. MeshQL REST doesn't return entity IDs,
     * so we must query GraphQL to discover them.
     */
    public void populateCustomerCacheFromGraphQL() {
        try {
            String query = "{ getAll { id legacy_acct_id } }";
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + "/customer/graph"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode data = json.path("data").path("getAll");
                if (data.isArray()) {
                    for (JsonNode customer : data) {
                        String meshqlId = customer.path("id").asText(null);
                        String legacyAcctId = customer.path("legacy_acct_id").asText(null);
                        if (meshqlId != null && legacyAcctId != null && !legacyAcctId.equals("null")) {
                            customerCache.put(legacyAcctId, meshqlId);
                        }
                    }
                }
            }
            logger.info("Populated customer cache from GraphQL: {} entries", customerCache.size());
        } catch (Exception e) {
            logger.error("Failed to populate customer cache from GraphQL: {}", e.getMessage(), e);
        }
    }

    /**
     * After all bills have been POSTed, query GraphQL to populate the
     * legacy_bill_id -> meshql_id cache.
     */
    public void populateBillCacheFromGraphQL() {
        try {
            String query = "{ getAll { id legacy_bill_id } }";
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + "/bill/graph"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode data = json.path("data").path("getAll");
                if (data.isArray()) {
                    for (JsonNode bill : data) {
                        String meshqlId = bill.path("id").asText(null);
                        String legacyBillId = bill.path("legacy_bill_id").asText(null);
                        if (meshqlId != null && legacyBillId != null && !legacyBillId.equals("null")) {
                            billCache.put(legacyBillId, meshqlId);
                        }
                    }
                }
            }
            logger.info("Populated bill cache from GraphQL: {} entries", billCache.size());
        } catch (Exception e) {
            logger.error("Failed to populate bill cache from GraphQL: {}", e.getMessage(), e);
        }
    }

    /**
     * Query the GraphQL API to find an entity ID.
     */
    private String queryGraphQL(String endpoint, String query, String queryName) {
        try {
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode data = json.get("data");
                if (data != null && data.has(queryName)) {
                    JsonNode result = data.get(queryName);
                    if (result != null && !result.isNull()) {
                        if (result.isArray() && result.size() > 0) {
                            return result.get(0).get("id").asText();
                        } else if (result.has("id")) {
                            return result.get("id").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to query GraphQL for ID resolution: {}", e.getMessage());
        }
        return null;
    }
}
