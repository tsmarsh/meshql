package com.meshql.mesher.introspect;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PrimaryKeyInfo(
        @JsonProperty("tableSchema") String tableSchema,
        @JsonProperty("tableName") String tableName,
        @JsonProperty("columnName") String columnName,
        @JsonProperty("ordinalPosition") int ordinalPosition
) {}
