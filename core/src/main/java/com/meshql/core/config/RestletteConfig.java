package com.meshql.core.config;

import com.networknt.schema.JsonSchema;

import java.util.List;

public record RestletteConfig(
        List<String> tokens,
        String path,
        int port,
        StorageConfig storage,
        JsonSchema schema
) {}