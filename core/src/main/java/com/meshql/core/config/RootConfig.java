package com.meshql.core.config;

import java.util.List;

public record RootConfig(
    List<QueryConfig> singletons,
    List<QueryConfig> vectors,
    List<SingletonResolverConfig> singletonResolvers,
    List<VectorResolverConfig> vectorResolvers
) {} 