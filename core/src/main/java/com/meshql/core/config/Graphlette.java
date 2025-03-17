package com.meshql.core.config;

public record Graphlette(
        String path,
        StorageConfig storage,
        String schema,
        RootConfig rootConfig
) {}