package com.meshql.repositories.ksql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class KsqlHttpClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KsqlHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    public KsqlHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void executeStatement(String ksql) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ksql", ksql);
        body.put("streamsProperties", Collections.emptyMap());

        String json = toJson(body);
        logger.debug("Executing ksqlDB statement: {}", ksql);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/ksql"))
                .header("Content-Type", "application/vnd.ksql.v1+json")
                .header("Accept", "application/vnd.ksql.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                logger.error("ksqlDB statement failed ({}): {}", response.statusCode(), response.body());
                throw new RuntimeException("ksqlDB statement failed: " + response.body());
            }
            logger.debug("ksqlDB statement succeeded: {}", response.statusCode());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to execute ksqlDB statement", e);
        }
    }

    public List<Map<String, Object>> executeQuery(String ksql) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ksql", ksql);
        body.put("streamsProperties", Collections.emptyMap());

        String json = toJson(body);
        logger.debug("Executing ksqlDB query: {}", ksql);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/query"))
                .header("Content-Type", "application/vnd.ksql.v1+json")
                .header("Accept", "application/vnd.ksql.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                String responseBody = response.body();
                // Return empty for "not ready yet" errors — caller can retry
                if (responseBody.contains("not available yet") || responseBody.contains("Cannot determine which host")) {
                    logger.debug("ksqlDB query not ready yet: {}", responseBody);
                    return Collections.emptyList();
                }
                logger.error("ksqlDB query failed ({}): {}", response.statusCode(), responseBody);
                throw new RuntimeException("ksqlDB query failed: " + responseBody);
            }
            return parseQueryResponse(response.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to execute ksqlDB query", e);
        }
    }

    public Map<String, Object> serverInfo() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/info"))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("ksqlDB server info failed: " + response.body());
            }
            return MAPPER.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get ksqlDB server info", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQueryResponse(String responseBody) {
        try {
            List<Map<String, Object>> rawResponse = MAPPER.readValue(responseBody, new TypeReference<>() {});
            if (rawResponse == null || rawResponse.isEmpty()) {
                return Collections.emptyList();
            }

            // First element is the header with schema info
            Map<String, Object> headerElement = rawResponse.get(0);
            Map<String, Object> header = (Map<String, Object>) headerElement.get("header");
            if (header == null) {
                return Collections.emptyList();
            }

            String schema = (String) header.get("schema");
            List<String> columnNames = parseSchemaColumns(schema);

            // Remaining elements are data rows
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 1; i < rawResponse.size(); i++) {
                Map<String, Object> rowElement = rawResponse.get(i);
                if (rowElement.containsKey("finalMessage")) {
                    break;
                }
                Map<String, Object> row = (Map<String, Object>) rowElement.get("row");
                if (row == null) continue;

                List<Object> columns = (List<Object>) row.get("columns");
                if (columns == null) continue;

                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int j = 0; j < Math.min(columnNames.size(), columns.size()); j++) {
                    rowMap.put(columnNames.get(j), columns.get(j));
                }
                results.add(rowMap);
            }

            return results;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ksqlDB query response", e);
        }
    }

    static List<String> parseSchemaColumns(String schema) {
        // Schema format: "`ID` STRING KEY, `PAYLOAD` STRING, `CREATED_AT` BIGINT, ..."
        List<String> columns = new ArrayList<>();
        if (schema == null || schema.isEmpty()) return columns;

        String[] parts = schema.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            // Extract column name between backticks
            int start = trimmed.indexOf('`');
            int end = trimmed.indexOf('`', start + 1);
            if (start >= 0 && end > start) {
                columns.add(trimmed.substring(start + 1, end));
            }
        }
        return columns;
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Check if a table is ready for pull queries.
     * Returns true if the table can be queried (even if empty), false if still materializing.
     */
    public boolean isTableReady(String tableName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ksql", "SELECT * FROM " + tableName + " LIMIT 1;");
        body.put("streamsProperties", Collections.emptyMap());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/query"))
                .header("Content-Type", "application/vnd.ksql.v1+json")
                .header("Accept", "application/vnd.ksql.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        // HttpClient doesn't require explicit close in Java 11+
    }
}
