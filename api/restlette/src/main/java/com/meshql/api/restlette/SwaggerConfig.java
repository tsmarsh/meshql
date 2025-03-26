package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;


import static com.tailoredshapes.underbar.ocho.Die.rethrow;

public class SwaggerConfig {

    @SuppressWarnings("unchecked")
    public static void configureSwagger(OpenAPI openAPI, JsonSchema jsonSchema) {
        Components components = new Components();

        // Add security scheme
        components.addSecuritySchemes("BearerAuth",
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));


        // Add schemas
        components.addSchemas("State", createSchemaFromJsonSchema(jsonSchema) );

        // Add operation status schema
        components.addSchemas("OperationStatus", new Schema<>()
                .type("object")
                .addProperty("id", new Schema<>().type("string"))
                .addProperty("status", new Schema<>().type("string"))
                .addProperty("error", new Schema<>().type("string")));

        // Configure paths
        configurePaths(openAPI);

        openAPI.components(components);
    }

    private static void configurePaths(OpenAPI openAPI) {
        // Create paths for CRUD operations
        PathItem getPath = new io.swagger.v3.oas.models.PathItem()
                .get(new io.swagger.v3.oas.models.Operation()
                        .summary("Get all items")
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse()
                                        .description("Successful operation")
                                        .content(new Content().addMediaType("application/json",
                                                new MediaType()
                                                        .schema(new Schema<>().$ref("#/components/schemas/State")))))));

        // Add other paths and operations
        // (Simplified for brevity - would need to add all CRUD operations)

        openAPI.path("/", getPath);
    }

    public static Schema<?> createSchemaFromJsonSchema(JsonSchema og) {
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = rethrow(() -> mapper.writeValueAsString(og.getSchemaNode()));

        return rethrow(() -> Json.mapper().readValue(jsonString, Schema.class));
    }
}