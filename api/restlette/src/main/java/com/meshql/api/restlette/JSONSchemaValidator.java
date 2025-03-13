package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.core.Validator;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tailoredshapes.stash.Stash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class JSONSchemaValidator implements Validator {
    private static final Logger logger = LoggerFactory.getLogger(JSONSchemaValidator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonSchema schema;

    public JSONSchemaValidator(Map<String, Object> schemaMap) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonNode schemaNode = objectMapper.valueToTree(schemaMap);
        this.schema = factory.getSchema(schemaNode);
    }

    @Override
    public CompletableFuture<Boolean> validate(Stash data) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(data);
            Set<ValidationMessage> validationMessages = schema.validate(jsonNode);

            if (!validationMessages.isEmpty()) {
                logger.debug("Validation errors: {}", validationMessages);
                return CompletableFuture.completedFuture(false);
            }

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            logger.error("Schema validation error", e);
            return CompletableFuture.completedFuture(false);
        }
    }
}