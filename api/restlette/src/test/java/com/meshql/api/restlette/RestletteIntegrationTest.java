package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;

import com.meshql.core.Repository;
import com.meshql.core.Validator;
import com.meshql.repositories.memory.InMemoryRepository;
import com.tailoredshapes.stash.Stash;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Function;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.io.Requests.get;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.*;

class RestletteIntegrationTest {
    private static Service sparkService;
    private static final int PORT = 4568;
    private static final String API_PATH = "/api/test";
    private static ObjectMapper objectMapper;
    private static HttpClient httpClient;
    private static String BASE_URL;
    private static Repository repository;
    private static Auth auth;
    private static Validator validator;

    // Define schema
    private static Stash testSchema = stash(
            "type", "object",
            "properties", stash(
                    "title", stash("type", "string"),
                    "content", stash("type", "string"),
                    "tags", stash("type", "array", "items", stash("type", "string"))),
            "required", list("title", "content"));

    @BeforeAll
    static void setUp() {
        BASE_URL = "http://localhost:" + PORT + API_PATH;
        repository = new InMemoryRepository();
        auth = new NoAuth();
        validator = new JSONSchemaValidator(testSchema);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        sparkService = Service.ignite().port(PORT);

        CrudHandler crudHandler = new CrudHandler(auth, repository, validator, API_PATH, null);

        Restlette.init(sparkService, crudHandler, API_PATH, PORT, testSchema);

        sparkService.awaitInitialization();
    }

    @AfterAll
    static void tearDown() {
        sparkService.stop();
        sparkService.awaitStop();
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