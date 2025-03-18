package com.meshql.api.graphql;

import com.google.gson.Gson;
import com.meshql.core.Auth;
import com.meshql.core.Plugin;
import com.meshql.core.Searcher;
import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.RootConfig;
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

    public void init(Service sparkService, Map<String, Plugin> plugins, Auth authorizer, GraphletteConfig config) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(config.schema());

        RootConfig rootConfig = config.rootConfig();

        DTOFactory dtoFactory = new DTOFactory(config.rootConfig().resolvers());
        Plugin plugin = plugins.get(config.storage().type());
        Searcher searcher = plugin.createSearcher(config.storage());


        Map<String, DataFetcher> fetchers = Root.create(searcher, dtoFactory, authorizer, rootConfig);

        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        each(fetchers, (t, f) -> {
            builder.type("Query", b -> b.dataFetcher(t, f));
        });
        RuntimeWiring runtimeWiring = builder.build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();

        sparkService.post(config.path() + "/graphql", this::handleGraphQLRequest);
    }

    private Object handleGraphQLRequest(Request request, Response response) {
        response.type("application/json");

        try {
            // Parse the request body
            GraphQLRequest graphQLRequest = gson.fromJson(request.body(), GraphQLRequest.class);

            // Execute the query
            ExecutionResult result = graphQL.execute(graphQLRequest.getQuery());

            // Convert the result to JSON
            return gson.toJson(result.toSpecification());
        } catch (Exception e) {
            response.status(500);
            return gson.toJson(new ErrorResponse("Error processing GraphQL request: " + e.getMessage()));
        }
    }

    // Helper classes for request/response handling
    private static class GraphQLRequest {
        private String query;

        public String getQuery() {
            return query;
        }
    }

    private record ErrorResponse(String error) {
    }
}