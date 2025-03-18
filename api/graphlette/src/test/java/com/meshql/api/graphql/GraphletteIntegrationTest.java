package com.meshql.api.graphql;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Plugin;
import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.RootConfig;
import com.meshql.core.config.StorageConfig;
import com.meshql.plugins.memory.InMemoryPlugin;
import com.tailoredshapes.stash.Stash;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphletteIntegrationTest {
    private static Service sparkService;
    private static final int PORT = 4569;
    private static final String API_PATH = "/api/graphql";
    private static HttpClient httpClient;
    private static String BASE_URL;
    private static final Gson gson = new Gson();

    private static final String TEST_SCHEMA = """
            type Query {
                testObject(id: ID!): TestObject
                allTestObjects: [TestObject]
            }
            
            type TestObject {
                id: ID!
                title: String!
                content: String
                tags: [String]
            }
            """;

    @BeforeAll
    static void setUp() {
        BASE_URL = "http://localhost:" + PORT + API_PATH + "/graphql";
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        sparkService = Service.ignite().port(PORT);

        // Configure graphlette
        Graphlette graphlette = new Graphlette();
        Map<String, Plugin> plugins = new HashMap<>();
        plugins.put("memory", new InMemoryPlugin());
        
        Auth auth = new NoAuth();
        
        // Create some test data for the in-memory repository
        InMemoryPlugin memoryPlugin = (InMemoryPlugin) plugins.get("memory");
        memoryPlugin.getRepository().upsert("testobj1", stash(
            "id", "testobj1",
            "title", "Test Object 1",
            "content", "This is test content",
            "tags", list("test", "graphql")
        ));
        
        // Configure Graphlette
        Map<String, Object> resolvers = new HashMap<>();
        resolvers.put("testObject", stash("type", "testObject"));
        resolvers.put("allTestObjects", stash("type", "testObject"));
        
        RootConfig rootConfig = new RootConfig(resolvers);
        StorageConfig storageConfig = new StorageConfig("memory", "test");
        GraphletteConfig config = new GraphletteConfig(API_PATH, storageConfig, TEST_SCHEMA, rootConfig);
        
        graphlette.init(sparkService, plugins, auth, config);
        
        sparkService.awaitInitialization();
    }

    @AfterAll
    static void tearDown() {
        sparkService.stop();
        sparkService.awaitStop();
    }

    @Test
    void testQuerySingleObject() throws IOException, InterruptedException {
        String graphqlQuery = "{\"query\": \"{ testObject(id: \\\"testobj1\\\") { id title content tags } }\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertNotNull(jsonResponse.get("data"));
        
        JsonObject data = jsonResponse.getAsJsonObject("data");
        JsonObject testObject = data.getAsJsonObject("testObject");
        
        assertEquals("testobj1", testObject.get("id").getAsString());
        assertEquals("Test Object 1", testObject.get("title").getAsString());
        assertEquals("This is test content", testObject.get("content").getAsString());
    }
    
    @Test
    void testQueryAllObjects() throws IOException, InterruptedException {
        String graphqlQuery = "{\"query\": \"{ allTestObjects { id title } }\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertNotNull(jsonResponse.get("data"));
        
        JsonObject data = jsonResponse.getAsJsonObject("data");
        assertNotNull(data.getAsJsonArray("allTestObjects"));
    }
    
    @Test
    void testInvalidQuery() throws IOException, InterruptedException {
        String invalidQuery = "{\"query\": \"{ nonExistentField { id } }\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidQuery))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); // GraphQL returns 200 even for errors

        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertNotNull(jsonResponse.get("errors"));
    }
} 