package com.meshql.api.graphql;

import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TestUtils {
    public static GraphQLSchema createTestSchema() {
        String sdl = """
            type Query {
                user(id: ID!, at: Int): User
                userPosts(id: ID!, at: Int): [Post]
            }
            
            type User {
                id: ID!
                name: String!
                posts: [Post]
            }
            
            type Post {
                id: ID!
                title: String!
                content: String
                userId: ID!
            }
        """;
        
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring().build();
        return new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
    }

    public static Field createField(String name, String... subFields) {
        SelectionSet.Builder selectionSetBuilder = SelectionSet.newSelectionSet();
        Arrays.stream(subFields)
                .map(field -> Field.newField(field).build())
                .forEach(selectionSetBuilder::selection);

        return Field.newField(name)
                .selectionSet(selectionSetBuilder.build())
                .build();
    }
}