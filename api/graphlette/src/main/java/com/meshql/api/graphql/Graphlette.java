package com.meshql.api.graphql;

import com.google.gson.Gson;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tailoredshapes.underbar.ocho.UnderBar.each;

public class Graphlette extends HttpServlet {
    private static final Gson gson = new Gson();
    private GraphQL graphQL;

    public Graphlette(Map<String, DataFetcher> fetchers, String schema) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

        // Register Query data fetchers
        each(fetchers, (t, f) -> {
            builder.type("Query", b -> b.dataFetcher(t, f));
        });

        // Create a custom data fetcher that handles ResolverFunction objects
        DataFetcher<?> resolverDataFetcher = env -> {
            Object source = env.getSource();
            String fieldName = env.getField().getName();

            System.out.println("DATAFETCHER: field=" + fieldName + ", source type=" + (source != null ? source.getClass().getSimpleName() : "null"));

            if (source instanceof Map) {
                Map<String, Object> sourceMap = (Map<String, Object>) source;
                Object value = sourceMap.get(fieldName);

                System.out.println("DATAFETCHER: field=" + fieldName + ", value type=" + (value != null ? value.getClass().getSimpleName() : "null") + ", isResolver=" + (value instanceof ResolverFunction));

                // If the value is a ResolverFunction, call it
                if (value instanceof ResolverFunction) {
                    System.out.println("DATAFETCHER: Calling resolver for field " + fieldName);
                    try {
                        ResolverFunction resolver = (ResolverFunction) value;
                        Object result = resolver.resolve((com.tailoredshapes.stash.Stash) source, env);
                        System.out.println("DATAFETCHER: Resolver returned: " + (result != null ? result.getClass().getSimpleName() : "null"));
                        if (result != null && result instanceof java.util.List) {
                            System.out.println("DATAFETCHER: List size: " + ((java.util.List)result).size());
                        }
                        return result;
                    } catch (Exception e) {
                        System.err.println("DATAFETCHER: Resolver threw exception for field " + fieldName + ": " + e.getMessage());
                        if (e.getCause() != null) {
                            System.err.println("DATAFETCHER: Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
                            e.getCause().printStackTrace();
                        } else {
                            e.printStackTrace();
                        }
                        throw e;
                    }
                }

                return value;
            }

            return null;
        };

        // Register the resolver data fetcher for all object types in the schema
        typeDefinitionRegistry.getTypes(graphql.language.ObjectTypeDefinition.class).forEach(type -> {
            String typeName = type.getName();
            System.out.println("GRAPHLETTE: Found type: " + typeName);
            if (!typeName.equals("Query") && !typeName.equals("Mutation") && !typeName.equals("Subscription")) {
                System.out.println("GRAPHLETTE: Registering data fetchers for type: " + typeName);
                builder.type(typeName, typeBuilder -> {
                    type.getFieldDefinitions().forEach(field -> {
                        System.out.println("GRAPHLETTE: Registering data fetcher for " + typeName + "." + field.getName());
                        typeBuilder.dataFetcher(field.getName(), resolverDataFetcher);
                    });
                    return typeBuilder;
                });
            }
        });

        RuntimeWiring runtimeWiring = builder.build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");

        try {
            String body = request.getReader().lines().collect(Collectors.joining());
            GraphQLRequest graphQLRequest = gson.fromJson(body, GraphQLRequest.class);

            ExecutionResult result = graphQL.execute(graphQLRequest.getQuery());

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(result.toSpecification()));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(gson.toJson(new ErrorResponse("Error processing GraphQL request: " + e.getMessage())));
        }
    }

    private static class GraphQLRequest {
        private String query;

        public String getQuery() {
            return query;
        }
    }

    private record ErrorResponse(String error) {
    }
}
