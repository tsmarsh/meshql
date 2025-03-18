package com.meshql.core.config;

import java.net.URI;


public record ResolverConfig(
        String name,
        String id,
        String queryName,
        URI url
) {}