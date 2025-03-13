package com.meshql.server;

import com.meshql.core.StorageConfig;
import com.networknt.schema.JsonSchema;

import java.util.List;

public record Restlette(
        List<String> tokens,
        String path,
        StorageConfig storage,
        JsonSchema schema
) {}