package com.meshql.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.Map;

/**
 * Simple HTTP client for REST and GraphQL operations.
 */
public class HttpClient {
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpClient() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a POST request with JSON body.
     */
    public JsonNode post(String url, String jsonBody) throws IOException {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return executeRequest(request);
    }

    /**
     * Send a PUT request with JSON body.
     */
    public JsonNode put(String url, String jsonBody) throws IOException {
        HttpPut request = new HttpPut(url);
        request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return executeRequest(request);
    }

    /**
     * Send a GET request.
     */
    public JsonNode get(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        return executeRequest(request);
    }

    /**
     * Send a DELETE request.
     */
    public void delete(String url) throws IOException {
        HttpDelete request = new HttpDelete(url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            EntityUtils.consume(response.getEntity());
        }
    }

    /**
     * Execute a GraphQL query.
     */
    public JsonNode graphql(String url, String query) throws IOException {
        String requestBody = objectMapper.writeValueAsString(Map.of("query", query));
        JsonNode response = post(url, requestBody);
        return response.get("data");
    }

    private JsonNode executeRequest(HttpUriRequestBase request) throws IOException {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            return objectMapper.readTree(responseBody);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException("Failed to parse response", e);
        }
    }

    public void close() throws IOException {
        httpClient.close();
    }
}
