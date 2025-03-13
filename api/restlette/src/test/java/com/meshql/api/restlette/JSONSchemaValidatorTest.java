package com.meshql.api.restlette;

import com.tailoredshapes.stash.Stash;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.tailoredshapes.stash.Stash.stash;
import static org.junit.jupiter.api.Assertions.*;

class JSONSchemaValidatorTest {

    @Test
    void testValidDataPassesValidation() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "minLength", 2),
                        "age", Map.of("type", "integer", "minimum", 0)),
                "required", List.of("name", "age"));

        JSONSchemaValidator validator = new JSONSchemaValidator(schema);

        Stash validData = stash(
                "name", "John",
                "age", 30);

        // Act
        boolean isValid = validator.validate(validData).get();

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testInvalidDataFailsValidation() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "minLength", 2),
                        "age", Map.of("type", "integer", "minimum", 0)),
                "required", List.of("name", "age"));

        JSONSchemaValidator validator = new JSONSchemaValidator(schema);

        // Missing required field
        Stash invalidData1 = stash(
                "name", "John");

        // Wrong type
        Stash invalidData2 = stash(
                "name", "John",
                "age", "thirty");

        // Below minimum length
        Stash invalidData3 = stash(
                "name", "J",
                "age", 30);

        // Act & Assert
        assertFalse(validator.validate(invalidData1).get());
        assertFalse(validator.validate(invalidData2).get());
        assertFalse(validator.validate(invalidData3).get());
    }

    @Test
    void testComplexSchemaValidation() throws ExecutionException, InterruptedException {
        // Arrange
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "person", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "address", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "street", Map.of("type", "string"),
                                                        "city", Map.of("type", "string")),
                                                "required", List.of("street", "city"))),
                                "required", List.of("name", "address"))),
                "required", List.of("person"));

        JSONSchemaValidator validator = new JSONSchemaValidator(schema);

        // Valid nested data
        Stash validData = stash(
                "person", stash(
                        "name", "John",
                        "address", stash(
                                "street", "123 Main St",
                                "city", "Anytown")));

        // Invalid - missing city in address
        Stash invalidData = stash(
                "person", stash(
                        "name", "John",
                        "address", stash(
                                "street", "123 Main St")));

        // Act & Assert
        assertTrue(validator.validate(validData).get());
        assertFalse(validator.validate(invalidData).get());
    }
}