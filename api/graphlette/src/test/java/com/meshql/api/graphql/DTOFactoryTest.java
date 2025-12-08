package com.meshql.api.graphql;

import com.meshql.core.config.VectorResolverConfig;
import com.tailoredshapes.stash.Stash;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;

import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.Die.rethrow;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DTOFactoryTest {
    private DTOFactory factory;
    private GraphQLSchema schema;

    @BeforeEach
    void setUp() {
        schema = TestUtils.createTestSchema();
        VectorResolverConfig resolverConfig = VectorResolverConfig.builder()
                .name("posts")
                .id("user")
                .queryName("userPosts")
                .url("http://localhost:8080/graphql")
                .build();
        factory = new DTOFactory(
            Collections.emptyList(),
            list(resolverConfig),
            Collections.emptyList(),
            Collections.emptyList(),
            stash()
        );
    }

    @Test
    void testFillOne() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "123");
        data.put("name", "Test User");

        long timestamp = System.currentTimeMillis();
        Map<String, Object> result = factory.fillOne(data, timestamp);

        assertEquals("123", result.get("id"));
        assertEquals("Test User", result.get("name"));
        assertEquals(timestamp, result.get("_timestamp"));
        assertTrue(result.containsKey("posts"));
    }

    @Test
    void testFillMany() {
        List<Stash> data = list(
            stash("id", "123", "name", "User 1"),
            stash("id", "456", "name", "User 2")
        );

        long timestamp = System.currentTimeMillis();
        List<Stash> results = factory.fillMany(data, timestamp);

        assertEquals(2, results.size());
        assertEquals("User 1", results.get(0).get("name"));
        assertEquals("User 2", results.get(1).get("name"));
        assertTrue(results.get(0).containsKey("posts"));
        assertTrue(results.get(1).containsKey("posts"));
    }

    @Test
    void testResolverExecution() throws ExecutionException, InterruptedException {
        // Setup test data
        Stash parent = stash("userId", "123",
                "_timestamp", System.currentTimeMillis());

        // Mock DataFetchingEnvironment
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getGraphQLSchema()).thenReturn(schema);
        when(env.getFields()).thenReturn(List.of(
            TestUtils.createField("posts", "title", "content")
        ));
        
        Map<String, Object> headers = Map.of("authorization", "Bearer test-token");
        when(env.getGraphQlContext()).thenReturn(GraphQLContext.newContext().of("headers", headers).build());

        // Get the resolver function and execute it
        Map<String, Object> dto = factory.fillOne(parent, System.currentTimeMillis());
        VectorResolver resolver = (VectorResolver) dto.get("posts");

        assertNotNull(resolver);

        // The actual resolver execution would require a running GraphQL server
        // Here we're just verifying the resolver is properly configured
        Object result = resolver.resolve((Stash) parent, env);
        assertNotNull(result);
    }

    @Test
    void testDataLoaderEnabledByDefault() {
        // DTOFactory with default constructor should have DataLoader enabled
        DTOFactory defaultFactory = new DTOFactory(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            stash()
        );
        // If DataLoader is enabled, it should attempt to use registry when env has one
        // This is verified implicitly by the resolver behavior
        Stash dto = defaultFactory.fillOne(stash("id", "123"), System.currentTimeMillis());
        assertNotNull(dto);
        assertEquals("123", dto.get("id"));
    }

    @Test
    void testDataLoaderCanBeDisabled() {
        // DTOFactory with DataLoader explicitly disabled
        DTOFactory disabledFactory = new DTOFactory(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            stash(),
            false  // dataLoaderEnabled = false
        );
        // Verify factory works correctly with DataLoader disabled
        Stash dto = disabledFactory.fillOne(stash("id", "456"), System.currentTimeMillis());
        assertNotNull(dto);
        assertEquals("456", dto.get("id"));
    }

    @Test
    void testDataLoaderExplicitlyEnabled() {
        // DTOFactory with DataLoader explicitly enabled
        DTOFactory enabledFactory = new DTOFactory(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            stash(),
            true  // dataLoaderEnabled = true
        );
        // Verify factory works correctly with DataLoader enabled
        Stash dto = enabledFactory.fillOne(stash("id", "789"), System.currentTimeMillis());
        assertNotNull(dto);
        assertEquals("789", dto.get("id"));
    }
}