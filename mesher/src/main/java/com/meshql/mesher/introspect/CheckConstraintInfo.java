package com.meshql.mesher.introspect;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CheckConstraintInfo(
        @JsonProperty("tableSchema") String tableSchema,
        @JsonProperty("tableName") String tableName,
        @JsonProperty("constraintName") String constraintName,
        @JsonProperty("checkClause") String checkClause
) {}
