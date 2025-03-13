package com.meshql.api.restlette;

import com.tailoredshapes.stash.Stash;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static org.junit.jupiter.api.Assertions.*;

class SwaggerConfigTest {

    @Test
    void testConfigureSwagger() {
        // Arrange
        OpenAPI openAPI = new OpenAPI();
        Stash jsonSchema= stash(
                "type", "object",
                "properties", stash(
                        "name", stash("type", "string"),
                        "value", stash("type", "integer")));

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