package com.meshql.core;

import com.tailoredshapes.stash.Stash;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Validator {
    CompletableFuture<Boolean> validate(Stash data);
}