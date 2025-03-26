package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static org.junit.jupiter.api.Assertions.*;

class SwaggerConfigTest {

    @Test
    void testConfigureSwagger() {
        // Arrange
        OpenAPI openAPI = new OpenAPI();
        var objectMapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);


        var testSchema = stash(
                "type", "object",
                "properties", stash(
                        "name", stash("type", "string"),
                        "value", stash("type", "integer")));
        JsonNode schemaNode = objectMapper.valueToTree(testSchema);
        var jsonSchema = factory.getSchema(schemaNode);

        SwaggerConfig.createSchemaFromJsonSchema(jsonSchema);
        // Act
        SwaggerConfig.configureSwagger(openAPI, jsonSchema);

        // Assert
        Components components = openAPI.getComponents();
        assertNotNull(components);
        assertNotNull(components.getSecuritySchemes());
        assertTrue(components.getSecuritySchemes().containsKey("BearerAuth"));

        // Check for schemas
        Map<String, Schema> schemas = components.getSchemas();
        assertNotNull(schemas);
        assertTrue(schemas.containsKey("State"));
        assertTrue(schemas.containsKey("OperationStatus"));

        // Verify schema properties
        Schema stateSchema = schemas.get("State");
        assertEquals("object", stateSchema.getType());
        assertTrue(stateSchema.getProperties().containsKey("name"));
        assertTrue(stateSchema.getProperties().containsKey("value"));
    }
}