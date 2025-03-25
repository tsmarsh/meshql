package com.meshql.api.graphql;

import com.google.gson.Gson;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Envelope;

import com.meshql.core.config.*;
import com.meshql.repos.sqlite.SQLiteRepository;
import com.meshql.repos.sqlite.SQLiteSearcher;
import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetcher;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import spark.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.List;
import java.util.Map;


import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.Die.rethrow;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphletteIntegrationTest {
    private static Service sparkService;
    private static final int PORT = 4569;
    private static final String API_PATH = "/test/graphql";
    private static HttpClient httpClient;
    private static String BASE_URL;

    private static final String TEST_SCHEMA = """
            type Query {
                testObject(id: ID!): TestObject
                getByTitle(title: String!): [TestObject]
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
        BASE_URL = "http://localhost:" + PORT + API_PATH;
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        sparkService = Service.ignite().port(PORT);
        
        Auth auth = new NoAuth();

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:graphlette.db");


        SQLiteRepository sqLiteRepository = new SQLiteRepository(rethrow(() -> dataSource.getConnection()), "test");
        sqLiteRepository.initialize();

        Stash payload = stash(
                "id", "testobj1",
                "title", "Test Object 1",
                "content", "This is test content",
                "tags", list("test", "graphql")
        );

        SQLiteSearcher searcher = new SQLiteSearcher(dataSource, "test", auth);

        sqLiteRepository.create(new Envelope(
            null, payload, null, false, null
        ), list());


        List<ResolverConfig> resolvers = list();

        List<QueryConfig> singletons = list(
                new QueryConfig("testObject", "json_extract(payload, '$.id') = '{{id}}'")
        );

        List<QueryConfig> vectors = list(
                new QueryConfig("getByTitle", "json_extract(payload, '$.title') = '{{title}}'")
        );

        RootConfig rootConfig = new RootConfig(
            resolvers,
                singletons,
                vectors
        );

        DTOFactory dtoFactory = new DTOFactory(resolvers);
        Map<String, DataFetcher> fetchers = Root.create(searcher, dtoFactory, auth, rootConfig);

        new Graphlette(
                sparkService, fetchers, TEST_SCHEMA, "test"
        );
        
        sparkService.awaitInitialization();
    }

    @AfterAll
    static void tearDown() {
        sparkService.stop();
        sparkService.awaitStop();

       rethrow(() -> new File("graphlette.db").delete());
    }

    @Test
    void testSingltonQueries() throws IOException, InterruptedException {
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

        assertEquals("Test Object 1", testObject.get("title").getAsString());
        assertEquals("This is test content", testObject.get("content").getAsString());
    }
    
    @Test
    void testVectorQueries() throws IOException, InterruptedException {
        String graphqlQuery = "{\"query\": \"{ getByTitle(title: \\\"Test Object 1\\\") { id title } }\"}";

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
        assertNotNull(data.getAsJsonArray("getByTitle"));
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