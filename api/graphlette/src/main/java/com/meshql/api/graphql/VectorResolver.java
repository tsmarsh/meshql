package com.meshql.api.graphql;

import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

/**
 * Resolver for vector (list) relationships.
 * Can return List<Stash> directly or CompletableFuture<List<Stash>> for async/batched resolution.
 */
@FunctionalInterface
public interface VectorResolver extends ResolverFunction {
    @Override
    Object resolve(
            Stash parent,
            DataFetchingEnvironment env
    );
}
