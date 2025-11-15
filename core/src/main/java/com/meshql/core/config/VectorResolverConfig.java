package com.meshql.core.config;

import java.net.URI;

public record VectorResolverConfig(
        String name,
        String id,
        String queryName,
        URI url
) {}
