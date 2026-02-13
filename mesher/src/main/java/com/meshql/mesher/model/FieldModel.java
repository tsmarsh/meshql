package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FieldModel(
        @JsonProperty("legacyName") String legacyName,
        @JsonProperty("cleanName") String cleanName,
        @JsonProperty("legacyType") String legacyType,
        @JsonProperty("cleanType") String cleanType,
        @JsonProperty("graphqlType") String graphqlType,
        @JsonProperty("jsonSchemaType") String jsonSchemaType,
        @JsonProperty("required") boolean required,
        @JsonProperty("transformation") String transformation
) {}
