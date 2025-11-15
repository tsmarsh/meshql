package com.meshql.api.graphql;

import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetchingEnvironment;

@FunctionalInterface
public interface SingletonResolver extends ResolverFunction {
    @Override
    Stash resolve(
            Stash parent,
            DataFetchingEnvironment env
    );
}
