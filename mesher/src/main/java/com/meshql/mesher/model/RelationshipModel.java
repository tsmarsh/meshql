package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RelationshipModel(
        @JsonProperty("targetEntity") String targetEntity,
        @JsonProperty("fieldName") String fieldName,
        @JsonProperty("foreignKeyInChild") String foreignKeyInChild,
        @JsonProperty("queryName") String queryName
) {}
