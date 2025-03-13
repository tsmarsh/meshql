package com.meshql.api.graphql.config;

import java.util.List;

public record RootConfig(
    List<ResolverConfig> resolvers,
    List<QueryConfig> singletons,
    List<QueryConfig> vectors
) {} 