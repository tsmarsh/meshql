package com.meshql.server;

import com.meshql.auth.noop.NoAuth;
import com.meshql.core.*;
import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.QueryConfig;
import com.meshql.core.config.RootConfig;
import com.meshql.core.config.StorageConfig;
import com.tailoredshapes.stash.Stash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Server class.
 */
class ServerTest {
    private Server server;
    private Path schemaFile;
    private TestPlugin testPlugin;
    private static final int TEST_PORT = 14044;

    @BeforeEach
    void setUp() throws Exception {
        // Create a test GraphQL schema
        schemaFile = Files.createTempFile("test_schema", ".graphql");
        Files.writeString(schemaFile, """
            type Query {
              getById(id: ID): TestEntity
            }

            type TestEntity {
              id: ID
              name: String
            }
            """);

        // Create test plugin
        testPlugin = new TestPlugin();

        // Create server
        server = new Server(Map.of("test", testPlugin));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (schemaFile != null) {
            Files.deleteIfExists(schemaFile);
        }
    }

    @Test
    void testServerStartsAndStops() throws Exception {
        Config config = new Config(
            Collections.emptyList(),
            Collections.emptyList(),
            TEST_PORT,
            Collections.emptyList()
        );

        server.init(config);
        assertTrue(true, "Server should start without exceptions");

        server.stop();
        assertTrue(true, "Server should stop without exceptions");
    }

    @Test
    void testHealthEndpoint() throws Exception {
        Config config = new Config(
            Collections.emptyList(),
            Collections.emptyList(),
            TEST_PORT,
            Collections.emptyList()
        );

        server.init(config);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/health"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ok"));
    }

    @Test
    void testGraphletteRegistration() throws Exception {
        StorageConfig storageConfig = new StorageConfig("test");
        GraphletteConfig graphletteConfig = new GraphletteConfig(
            "/test/graph",
            storageConfig,
            schemaFile.toString(),
            new RootConfig(
                List.of(new QueryConfig("getById", "{\"id\": \"{{id}}\"}}")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
            )
        );

        Config config = new Config(
            Collections.emptyList(),
            List.of(graphletteConfig),
            TEST_PORT,
            Collections.emptyList()
        );

        server.init(config);

        // Verify the graphlette endpoint exists by making a request
        HttpClient client = HttpClient.newHttpClient();
        String query = "{\"query\":\"{getById(id:\\\"test123\\\"){id name}}\"}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/test/graph"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(query))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("data"), "Response should contain 'data' field");
    }

    /**
     * Test plugin for unit tests
     */
    private static class TestPlugin implements Plugin {
        @Override
        public Searcher createSearcher(StorageConfig config) {
            return new TestSearcher();
        }

        @Override
        public Repository createRepository(StorageConfig config, Auth auth) {
            return new TestRepository();
        }

        @Override
        public void cleanUp() {
            // No-op
        }
    }

    /**
     * Test searcher that returns mock data
     */
    private static class TestSearcher implements Searcher {
        @Override
        public Stash find(com.github.jknack.handlebars.Template queryTemplate, Stash args, List<String> tokens, long timestamp) {
            Stash result = new Stash();
            result.put("id", "test123");
            result.put("name", "Test Entity");
            return result;
        }

        @Override
        public List<Stash> findAll(com.github.jknack.handlebars.Template queryTemplate, Stash args, List<String> tokens, long timestamp) {
            return List.of(find(queryTemplate, args, tokens, timestamp));
        }
    }

    /**
     * Test repository (not used in these tests)
     */
    private static class TestRepository implements Repository {
        @Override
        public Envelope create(Envelope envelope, List<String> tokens) {
            return new Envelope(
                "test123",
                envelope.payload(),
                Instant.now(),
                false,
                Collections.emptyList()
            );
        }

        @Override
        public List<Envelope> createMany(List<Envelope> envelopes, List<String> tokens) {
            return envelopes.stream()
                .map(e -> create(e, tokens))
                .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public Optional<Envelope> read(String id, List<String> tokens, Instant createdAt) {
            return Optional.empty();
        }

        @Override
        public List<Envelope> readMany(List<String> ids, List<String> tokens) {
            return Collections.emptyList();
        }

        @Override
        public Boolean remove(String id, List<String> tokens) {
            return true;
        }

        @Override
        public Map<String, Boolean> removeMany(List<String> ids, List<String> tokens) {
            return Collections.emptyMap();
        }

        @Override
        public List<Envelope> list(List<String> tokens) {
            return Collections.emptyList();
        }
    }
}
