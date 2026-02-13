package com.meshql.mesher.introspect;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ColumnInfo(
        @JsonProperty("tableSchema") String tableSchema,
        @JsonProperty("tableName") String tableName,
        @JsonProperty("columnName") String columnName,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("udtName") String udtName,
        @JsonProperty("characterMaximumLength") Integer characterMaximumLength,
        @JsonProperty("numericPrecision") Integer numericPrecision,
        @JsonProperty("numericScale") Integer numericScale,
        @JsonProperty("nullable") boolean nullable,
        @JsonProperty("columnDefault") String columnDefault,
        @JsonProperty("ordinalPosition") int ordinalPosition
) {}
