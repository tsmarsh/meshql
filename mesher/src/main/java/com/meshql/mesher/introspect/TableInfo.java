package com.meshql.mesher.introspect;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TableInfo(
        @JsonProperty("schema") String schema,
        @JsonProperty("name") String name
) {}
