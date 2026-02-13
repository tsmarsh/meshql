package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProcessingPhase(
        @JsonProperty("phase") int phase,
        @JsonProperty("entities") List<String> entities,
        @JsonProperty("cachePopulation") List<String> cachePopulation
) {}
