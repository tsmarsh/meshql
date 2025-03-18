package com.meshql.core.config;

public record GraphletteConfig(
        String path,
        StorageConfig storage,
        String schema,
        RootConfig rootConfig
) {}