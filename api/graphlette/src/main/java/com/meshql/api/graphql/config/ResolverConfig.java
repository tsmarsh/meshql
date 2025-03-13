package com.meshql.api.graphql.config;

import java.net.URI;


public record ResolverConfig(
        String name,
        String id,
        String queryName,
        URI url
) {}