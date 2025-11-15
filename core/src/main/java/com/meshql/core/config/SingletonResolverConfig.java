package com.meshql.core.config;

import java.net.URI;

public record SingletonResolverConfig(
        String name,
        String id,
        String queryName,
        URI url
) {}
