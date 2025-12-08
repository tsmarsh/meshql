package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;

import com.meshql.core.Plugin;
import com.meshql.core.Validator;
import com.meshql.core.config.RestletteConfig;
import com.meshql.core.config.StorageConfig;
import com.meshql.repositories.memory.InMemoryPlugin;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.tailoredshapes.stash.Stash;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.UnderBar.*;
import static org.junit.jupiter.api.Assertions.*;

class RestletteIntegrationTest {
    private static Server server;
    private static final int PORT = 4568;
    private static final String API_PATH = "/api/test";
    private static ObjectMapper objectMapper;
    private static HttpClient httpClient;
    private static String BASE_URL;
    private static Auth auth;
    private static Validator validator;
    private static Map<String, Plugin> storageFactory;

    // Define schema
    private static Stash testSchema = stash(
            "type", "object",
            "properties", stash(
                    "title", stash("type", "string"),
                    "content", stash("type", "string"),
                    "tags", stash("type", "array", "items", stash("type", "string"))),
            "required", list("title", "content"));

    @BeforeAll
    static void setUp() throws Exception {
        BASE_URL = "http://localhost:" + PORT + API_PATH;
        auth = new NoAuth();
        validator = new JSONSchemaValidator(testSchema);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonNode schemaNode = objectMapper.valueToTree(testSchema);
        var jsonSchema = factory.getSchema(schemaNode);

        var rc = RestletteConfig.builder()
                .path(API_PATH)
                .port(PORT)
                .storage(new StorageConfig("memory"))
                .schema(jsonSchema)
                .build();

        storageFactory = hash("memory", new InMemoryPlugin());

        Restlette restlette = new Restlette(rc, storageFactory, auth, validator);

        // Setup Jetty 12 server
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(PORT);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Register the Restlette servlet with wildcard path to handle all routes
        context.addServlet(new ServletHolder(restlette), API_PATH + "/*");

        server.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        each(storageFactory, (k, v) -> v.cleanUp());
    }

//    @Test
//    void testSwaggerDocsAvailable() {
//        String docsResponse = get("http://localhost:" + PORT + API_PATH + "/api-docs/swagger.json", Function.identity()).join();
//
//        assertNotNull(docsResponse);
//       assertEquals(API_PATH + " API", docsResponse.asStash("info").asString("title"));
//    }

    @Test
    void testFullRestletteIntegration() throws Exception {
        Stash testStash = stash("title", "Test Post", "content", "Test Content", "tags", list("test", "api"));

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "test-token")
                .POST(HttpRequest.BodyPublishers.ofString(testStash.toJSONString()))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode());

        Stash saved = Stash.parseJSON(createResponse.body());

        assertEquals("Test Post", saved.get("title"));
        assertEquals("Test Content", saved.get("content"));
        List<String> tags = (List<String>) saved.get("tags");

        assertEquals(2, tags.size());
    }

    @Test
    void testShouldRejectInvalidData() throws Exception {
        String invalidData = "{\"title\":\"Test Post\",\"tags\":\"not-an-array\"}";
        HttpRequest invalidRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "test-token")
                .POST(HttpRequest.BodyPublishers.ofString(invalidData))
                .build();

        HttpResponse<String> invalidResponse = httpClient.send(invalidRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidResponse.statusCode());
    }
}
