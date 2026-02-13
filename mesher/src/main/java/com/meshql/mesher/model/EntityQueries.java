package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EntityQueries(
        @JsonProperty("singletons") List<QueryModel> singletons,
        @JsonProperty("vectors") List<QueryModel> vectors
) {}
