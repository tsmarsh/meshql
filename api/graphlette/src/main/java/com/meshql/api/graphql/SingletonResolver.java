package com.meshql.api.graphql;

import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetchingEnvironment;

/**
 * Resolver for singleton (single object) relationships.
 * Can return Stash directly or CompletableFuture<Stash> for async/batched resolution.
 */
@FunctionalInterface
public interface SingletonResolver extends ResolverFunction {
    @Override
    Object resolve(
            Stash parent,
            DataFetchingEnvironment env
    );
}
