package com.meshql.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Repository {
    Envelope create(Envelope envelope, List<String> tokens);
    Optional<Envelope> read(String id, List<String> tokens, Instant createdAt);
    List<Envelope> list(List<String> tokens);
    Boolean remove(String id, List<String> tokens);
    List<Envelope> createMany(List<Envelope> payloads, List<String> tokens);
    List<Envelope> readMany(List<String> ids, List<String> tokens);
    Map<String, Boolean> removeMany(List<String> ids, List<String> tokens);
}