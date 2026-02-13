package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EntityRelationships(
        @JsonProperty("children") List<RelationshipModel> children,
        @JsonProperty("parents") List<RelationshipModel> parents
) {}
