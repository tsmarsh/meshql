package com.meshql.api.graphql;

import com.google.gson.Gson;
import com.meshql.core.Auth;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import spark.Request;
import spark.Response;
import spark.Service;

import java.util.Map;

import static com.tailoredshapes.underbar.ocho.UnderBar.each;

public class Graphlette {
    private static final Gson gson = new Gson();
    private GraphQL graphQL;

    public Graphlette(Service sparkService,
                      Map<String, DataFetcher> fetchers,
                      String schema,
                      String path) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        each(fetchers, (t, f) -> {
            builder.type("Query", b -> b.dataFetcher(t, f));
        });
        RuntimeWiring runtimeWiring = builder.build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();

        sparkService.post(path + "/graphql", this::handleGraphQLRequest);
    }

    private Object handleGraphQLRequest(Request request, Response response) {
        response.type("application/json");

        try {
            GraphQLRequest graphQLRequest = gson.fromJson(request.body(), GraphQLRequest.class);

            ExecutionResult result = graphQL.execute(graphQLRequest.getQuery());

            return gson.toJson(result.toSpecification());
        } catch (Exception e) {
            response.status(500);
            return gson.toJson(new ErrorResponse("Error processing GraphQL request: " + e.getMessage()));
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