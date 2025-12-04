package com.meshql.core.config;

import java.util.List;

public record RootConfig(
    List<QueryConfig> singletons,
    List<QueryConfig> vectors,
    List<SingletonResolverConfig> singletonResolvers,
    List<VectorResolverConfig> vectorResolvers,
    List<InternalSingletonResolverConfig> internalSingletonResolvers,
    List<InternalVectorResolverConfig> internalVectorResolvers,
    boolean dataLoaderEnabled
) {
    /**
     * Constructor with dataLoaderEnabled defaulting to true for backward compatibility.
     */
    public RootConfig(
        List<QueryConfig> singletons,
        List<QueryConfig> vectors,
        List<SingletonResolverConfig> singletonResolvers,
        List<VectorResolverConfig> vectorResolvers,
        List<InternalSingletonResolverConfig> internalSingletonResolvers,
        List<InternalVectorResolverConfig> internalVectorResolvers
    ) {
        this(singletons, vectors, singletonResolvers, vectorResolvers,
             internalSingletonResolvers, internalVectorResolvers, true);
    }
} 