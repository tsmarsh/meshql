package com.meshql.mesher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DomainModel(
        @JsonProperty("projectName") String projectName,
        @JsonProperty("package") String packageName,
        @JsonProperty("port") int port,
        @JsonProperty("prefix") String prefix,
        @JsonProperty("legacyDbName") String legacyDbName,
        @JsonProperty("entities") List<EntityModel> entities,
        @JsonProperty("processingPhases") List<ProcessingPhase> processingPhases
) {}
