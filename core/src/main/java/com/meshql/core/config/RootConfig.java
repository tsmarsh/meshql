package com.meshql.core.config;

import java.util.List;

public record RootConfig(
    List<ResolverConfig> resolvers,
    List<QueryConfig> singletons,
    List<QueryConfig> vectors
) {} 