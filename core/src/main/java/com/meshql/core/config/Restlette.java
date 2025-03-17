package com.meshql.core.config;

import com.networknt.schema.JsonSchema;

import java.util.List;

public record Restlette(
        List<String> tokens,
        String path,
        StorageConfig storage,
        JsonSchema schema
) {}