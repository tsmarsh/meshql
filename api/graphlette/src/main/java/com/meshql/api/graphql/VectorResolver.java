package com.meshql.api.graphql;

import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

@FunctionalInterface
public interface VectorResolver {
    List<Stash> resolve(
            Stash parent,
            DataFetchingEnvironment env
    );
}
