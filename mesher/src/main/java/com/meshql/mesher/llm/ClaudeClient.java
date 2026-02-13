package com.meshql.mesher.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Anthropic Claude Messages API.
 * Pattern matches veriforged/src/llm/client.rs.
 */
public class ClaudeClient {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeClient.class);

    private static final String DEFAULT_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final int MAX_TOKENS = 16384;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public ClaudeClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public static ClaudeClient fromEnv() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }
        return new ClaudeClient(apiKey, DEFAULT_API_URL);
    }

    public String sendMessage(String prompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "max_tokens", MAX_TOKENS,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                ))
        );

        String body = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        logger.info("Sending request to Claude API ({} chars)", prompt.length());

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorMsg;
            try {
                JsonNode errorJson = mapper.readTree(response.body());
                errorMsg = errorJson.path("error").path("message").asText(response.body());
            } catch (Exception e) {
                errorMsg = response.body();
            }
            throw new IOException("API request failed (" + response.statusCode() + "): " + errorMsg);
        }

        JsonNode responseJson = mapper.readTree(response.body());
        JsonNode content = responseJson.path("content");
        if (content.isArray() && !content.isEmpty()) {
            String text = content.get(0).path("text").asText("");
            logger.info("Received response ({} chars)", text.length());
            return text;
        }

        throw new IOException("Empty response from Claude API");
    }
}
