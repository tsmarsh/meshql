package com.meshql.api.graphql;

import com.meshql.core.config.SingletonResolverConfig;
import com.meshql.core.config.VectorResolverConfig;
import com.tailoredshapes.stash.Stash;
import graphql.GraphQLContext;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.Die.rethrow;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test to verify that resolvers are properly added to DTOs and can be invoked by GraphQL.
 * This tests the critical path: DTOFactory -> DTO with resolvers -> Graphlette data fetcher -> resolver invocation.
 */
class ResolverInvocationTest {

    @Test
    void testVectorResolverIsInstanceOfResolverFunction() {
        // Create a DTOFactory with a VectorResolver
        VectorResolverConfig config = new VectorResolverConfig(
                "coops",
                null,
                "getByFarm",
                rethrow(() -> new URI("http://localhost:4044/coop/graph"))
        );
        DTOFactory factory = new DTOFactory(Collections.emptyList(), List.of(config));

        // Create a DTO with the resolver
        Map<String, Object> data = new HashMap<>();
        data.put("id", "farm-123");
        data.put("name", "Test Farm");

        Stash dto = factory.fillOne(data, System.currentTimeMillis());

        // Verify the resolver exists in the DTO
        assertTrue(dto.containsKey("coops"), "DTO should contain coops resolver");
        Object resolver = dto.get("coops");
        assertNotNull(resolver, "Resolver should not be null");

        // This is the critical check that Graphlette does!
        // If this fails, Graphlette won't invoke the resolver
        System.out.println("Resolver class: " + resolver.getClass().getName());
        System.out.println("Is VectorResolver? " + (resolver instanceof VectorResolver));
        System.out.println("Is ResolverFunction? " + (resolver instanceof ResolverFunction));

        assertTrue(resolver instanceof VectorResolver, "Resolver should be a VectorResolver");

        // THIS IS THE BUG: VectorResolver doesn't extend ResolverFunction!
        assertTrue(resolver instanceof ResolverFunction,
                "Resolver MUST be instanceof ResolverFunction for Graphlette to invoke it!");
    }

    @Test
    void testSingletonResolverIsInstanceOfResolverFunction() {
        // Create a DTOFactory with a SingletonResolver
        SingletonResolverConfig config = new SingletonResolverConfig(
                "farm",
                "farm_id",
                "getById",
                rethrow(() -> new URI("http://localhost:4044/farm/graph"))
        );
        DTOFactory factory = new DTOFactory(List.of(config), Collections.emptyList());

        // Create a DTO with the resolver
        Map<String, Object> data = new HashMap<>();
        data.put("id", "coop-123");
        data.put("name", "Test Coop");
        data.put("farm_id", "farm-456");

        Stash dto = factory.fillOne(data, System.currentTimeMillis());

        // Verify the resolver exists in the DTO
        assertTrue(dto.containsKey("farm"), "DTO should contain farm resolver");
        Object resolver = dto.get("farm");
        assertNotNull(resolver, "Resolver should not be null");

        System.out.println("Resolver class: " + resolver.getClass().getName());
        System.out.println("Is SingletonResolver? " + (resolver instanceof SingletonResolver));
        System.out.println("Is ResolverFunction? " + (resolver instanceof ResolverFunction));

        assertTrue(resolver instanceof SingletonResolver, "Resolver should be a SingletonResolver");

        // THIS IS THE BUG: SingletonResolver doesn't extend ResolverFunction!
        assertTrue(resolver instanceof ResolverFunction,
                "Resolver MUST be instanceof ResolverFunction for Graphlette to invoke it!");
    }

    @Test
    void testResolverInvocationPath() {
        // This test simulates the complete path from DTO creation to resolver invocation
        // as it would happen in Graphlette

        // 1. Create a DTOFactory with a VectorResolver
        VectorResolverConfig config = new VectorResolverConfig(
                "coops",
                null,  // Use default "id" field
                "getByFarm",
                rethrow(() -> new URI("http://localhost:4044/coop/graph"))
        );
        DTOFactory factory = new DTOFactory(Collections.emptyList(), List.of(config));

        // 2. Create a DTO (simulating what Root.create would do)
        Map<String, Object> farmData = new HashMap<>();
        farmData.put("id", "farm-123");
        farmData.put("name", "Emerdale");

        Stash dto = factory.fillOne(farmData, System.currentTimeMillis());

        // 3. Simulate Graphlette's resolverDataFetcher logic
        String fieldName = "coops";
        Object value = dto.get(fieldName);

        System.out.println("\n=== Simulating Graphlette Data Fetcher ===");
        System.out.println("Field name: " + fieldName);
        System.out.println("Value type: " + (value != null ? value.getClass().getName() : "null"));
        System.out.println("Is Map? " + (dto instanceof Map));
        System.out.println("Is ResolverFunction? " + (value instanceof ResolverFunction));

        // This is exactly what Graphlette does at line 52
        if (value instanceof ResolverFunction) {
            System.out.println("✓ Graphlette WOULD invoke the resolver");
            ResolverFunction resolver = (ResolverFunction) value;
            // Would call: resolver.resolve(dto, env)
        } else {
            System.out.println("✗ Graphlette WOULD NOT invoke the resolver - THIS IS THE BUG!");
        }

        // For the test to pass, the resolver must be recognized as a ResolverFunction
        assertInstanceOf(ResolverFunction.class, value,
                "Resolver must be instanceof ResolverFunction for Graphlette to invoke it");
    }
}
