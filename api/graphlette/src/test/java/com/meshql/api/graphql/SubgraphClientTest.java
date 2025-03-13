package com.meshql.api.graphql;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class SubgraphClientTest {
    private WireMockServer wireMockServer;
    private SubgraphClient client;
    private GraphQLSchema schema;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        client = new SubgraphClient();

        // Setup test schema
        String sdl = """
            type Query {
                user(id: ID!, at: Int): User
            }

            type User {
                id: ID!
                name: String!
                email: String
            }
        """;

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().build();
        schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testCallSubgraph() throws ExecutionException, InterruptedException {
        // Setup mock response
        stubFor(post(urlEqualTo("/graphql"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "data": {
                                    "user": {
                                        "id": "123",
                                        "name": "Test User"
                                    }
                                }
                            }
                        """)));

        // Test the call
        var result = client.callSubgraph(
                URI.create("http://localhost:" + wireMockServer.port() + "/graphql"),
                "query { user(id: \"123\") { id name } }",
                "user",
                "Bearer token123"
        );

        assertEquals("123", result.get("id"));
        assertEquals("Test User", result.get("name"));

        verify(postRequestedFor(urlEqualTo("/graphql"))
                .withHeader("Authorization", equalTo("Bearer token123")));
    }

    @Test
    void testProcessContext() {
        var context = Map.of(
            "schema", schema,
            "fieldNodes", List.of(
                Field.newField("user")
                    .selectionSet(SelectionSet.newSelectionSet()
                        .selection(new Field("name"))
                        .selection(new Field("email"))
                        .build())
                    .build()
            )
        );

        String result = SubgraphClient.processContext("123", context, "user", 1234567890);
        
        assertTrue(result.contains("user(id: \"123\""));
        assertTrue(result.contains("at: 1234567890"));
        assertTrue(result.contains("name"));
        assertTrue(result.contains("email"));
    }
} 