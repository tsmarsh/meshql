package com.meshql.server;

import com.meshql.core.RootConfig;
import com.meshql.core.StorageConfig;

public record Graphlette(
        String path,
        StorageConfig storage,
        String schema,
        RootConfig rootConfig
) {}