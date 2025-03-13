package com.meshql.api.graphql;

import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetchingEnvironment;


@FunctionalInterface
public interface ResolverFunction {
    Stash resolve(
            Stash parent,
            DataFetchingEnvironment env
    );
}