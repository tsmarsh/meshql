package com.meshql.mesher.introspect;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForeignKeyInfo(
        @JsonProperty("childSchema") String childSchema,
        @JsonProperty("childTable") String childTable,
        @JsonProperty("childColumn") String childColumn,
        @JsonProperty("parentSchema") String parentSchema,
        @JsonProperty("parentTable") String parentTable,
        @JsonProperty("parentColumn") String parentColumn,
        @JsonProperty("ordinalPosition") int ordinalPosition
) {}
