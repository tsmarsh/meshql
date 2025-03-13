package com.meshql.core;

import java.util.List;

public record RootConfig(
        List<Singleton> singletons,
        List<Vector> vectors,
        List<Resolver> resolvers
) {}
