package com.meshql.api.graphql;

import com.tailoredshapes.stash.Stash;
import graphql.language.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.Die.rethrow;

public class SubgraphClient {
    private static final Logger logger = LoggerFactory.getLogger(SubgraphClient.class);
    private final HttpClient httpClient;

    public SubgraphClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Object callSubgraph(
            URI uri,
            String query,
            String queryName,
            String authHeader
    ) {
        var request = createRequest(uri, query, authHeader);
        return rethrow(() -> {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Stash stash = handleResponse(response);
            return extractData(stash, queryName);
        });
    }

    private HttpRequest createRequest(URI uri, String query, String authHeader) {
        var builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        String.format("{\"query\": \"%s\"}", query.replace("\"", "\\\""))
                ));

        if (authHeader != null && !authHeader.isEmpty()) {
            builder.header("Authorization", authHeader);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Stash handleResponse(HttpResponse<String> response) {
        try {
            if (response.statusCode() != 200) {
                throw new SubgraphException("HTTP error: " + response.statusCode());
            }
            return Stash.parseJSON(response.body());
        } catch (Exception e) {
            logger.error("Error parsing response: {}", response.body(), e);
            throw new SubgraphException("Failed to parse response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object extractData(Stash json, String queryName) {
        if (json.containsKey("errors")) {
            var errors = (List<Map<String, Object>>) json.get("errors");
            throw new SubgraphException(errors.get(0).get("message").toString());
        }
        var data = json.asStash("data");
        System.out.println("SUBGRAPH: queryName=" + queryName + ", hasContent=" + data.hasContent(queryName));
        if(data.hasContent(queryName)){
            Object result = data.get(queryName);
            System.out.println("SUBGRAPH: result type=" + (result != null ? result.getClass().getName() : "null") + ", isList=" + (result instanceof List));
            if(result instanceof List){
                List<Stash> list = data.asStashes(queryName);
                System.out.println("SUBGRAPH: Returning list with " + list.size() + " items");
                return list;
            }else {
                Stash stash = data.asStash(queryName);
                System.out.println("SUBGRAPH: Returning single stash");
                return stash;
            }
        } else {
            System.out.println("SUBGRAPH: No content for queryName, returning empty stash");
            return stash();
        }
    }

    public static String processSelectionSet(SelectionSet selectionSet) {
        return selectionSet.getSelections().stream()
                .filter(Field.class::isInstance)
                .map(Field.class::cast)
                .map(SubgraphClient::processFieldNode)
                .reduce("", String::concat);
    }

    public static String processFieldNode(Field field) {
        var name = field.getName();
        if (field.getSelectionSet() != null) {
            return String.format("%s {\n%s}\n", name, processSelectionSet(field.getSelectionSet()));
        }
        return name + "\n";
    }

    public static String processContext(
            String id,
            Map<String, Object> context,
            String queryName,
            long timestamp
    ) {
        // Validate that fieldNodes exist
        if (!context.containsKey("fieldNodes") || !(context.get("fieldNodes") instanceof List<?> fieldNodesRaw)) {
            throw new SubgraphException("Context is malformed: missing fieldNodes");
        }

        @SuppressWarnings("unchecked")
        List<Field> fieldNodes = (List<Field>) fieldNodesRaw;
        if (fieldNodes.isEmpty()) {
            throw new SubgraphException("Context is malformed: empty fieldNodes");
        }

        var firstNode = fieldNodes.get(0);
        if (firstNode.getSelectionSet() == null) {
            throw new SubgraphException("Context is malformed: first field has no selectionSet");
        }

        var selections = processSelectionSet(firstNode.getSelectionSet());
        return String.format("{%s(id: \"%s\" at: %d){\n%s}}", queryName, id, timestamp, selections);
    }
}
