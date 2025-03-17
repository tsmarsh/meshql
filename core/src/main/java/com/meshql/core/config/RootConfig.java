package com.meshql.core.config;

import java.util.List;

public record RootConfig(
        List<Singleton> singletons,
        List<Vector> vectors,
        List<Resolver> resolvers
) {}
