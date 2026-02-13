package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EntityModel(
        @JsonProperty("legacyTable") String legacyTable,
        @JsonProperty("cleanName") String cleanName,
        @JsonProperty("className") String className,
        @JsonProperty("legacyPrimaryKey") String legacyPrimaryKey,
        @JsonProperty("legacyIdField") String legacyIdField,
        @JsonProperty("isRoot") boolean isRoot,
        @JsonProperty("processingPhase") int processingPhase,
        @JsonProperty("fields") List<FieldModel> fields,
        @JsonProperty("codeMaps") List<CodeMap> codeMaps,
        @JsonProperty("relationships") EntityRelationships relationships,
        @JsonProperty("queries") EntityQueries queries
) {}
