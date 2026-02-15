package com.meshql.examples.egg_economy_ksqldb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Base class for projection updaters. Provides read-modify-write pattern:
 * GET current projection via GraphQL -> compute new values -> PUT via REST.
 */
public abstract class ProjectionUpdater {
    private static final Logger logger = LoggerFactory.getLogger(ProjectionUpdater.class);
    protected static final ObjectMapper mapper = new ObjectMapper();

    protected final String platformUrl;
    protected final ProjectionCache cache;
    protected final HttpClient httpClient;

    protected ProjectionUpdater(String platformUrl, ProjectionCache cache) {
        this.platformUrl = platformUrl;
        this.cache = cache;
        this.httpClient = HttpClient.newHttpClient();
    }

    protected JsonNode getProjection(String graphPath, String meshqlId, String fields) {
        try {
            String query = "{ getById(id: \"" + meshqlId + "\") { " + fields + " } }";
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + graphPath))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = mapper.readTree(response.body());
                return json.path("data").path("getById");
            }
        } catch (Exception e) {
            logger.error("Failed to GET projection from {}/{}: {}", graphPath, meshqlId, e.getMessage());
        }
        return null;
    }

    protected String createProjection(String apiPath, ObjectNode data,
                                       String graphPath, String fkQueryName, String fkValue) {
        try {
            String body = mapper.writeValueAsString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + apiPath))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return discoverProjectionId(graphPath, fkQueryName, fkValue);
            } else {
                logger.error("POST {} failed: {} - {}", apiPath, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Failed to POST projection to {}: {}", apiPath, e.getMessage());
        }
        return null;
    }

    private String discoverProjectionId(String graphPath, String fkQueryName, String fkValue) {
        try {
            String query = "{ " + fkQueryName + "(id: \"" + fkValue + "\") { id } }";
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + graphPath))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode json = mapper.readTree(resp.body());
                JsonNode results = json.path("data").path(fkQueryName);
                if (results.isArray() && !results.isEmpty()) {
                    return results.get(results.size() - 1).path("id").asText(null);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to discover projection ID from {}: {}", graphPath, e.getMessage());
        }
        return null;
    }

    protected boolean updateProjection(String apiPath, String meshqlId, ObjectNode data) {
        try {
            String body = mapper.writeValueAsString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(platformUrl + apiPath + "/" + meshqlId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            logger.error("Failed to PUT projection to {}/{}: {}", apiPath, meshqlId, e.getMessage());
            return false;
        }
    }
}
