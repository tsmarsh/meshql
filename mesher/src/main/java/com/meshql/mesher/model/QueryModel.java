package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QueryModel(
        @JsonProperty("name") String name,
        @JsonProperty("template") String template
) {}
