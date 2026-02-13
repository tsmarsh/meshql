package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record CodeMap(
        @JsonProperty("legacyColumn") String legacyColumn,
        @JsonProperty("cleanField") String cleanField,
        @JsonProperty("mapping") Map<String, String> mapping
) {}
