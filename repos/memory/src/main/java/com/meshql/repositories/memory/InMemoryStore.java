package com.meshql.repositories.memory;

import com.meshql.core.Envelope;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared data store for in-memory repository and searcher.
 * Stores envelopes in memory with version history support.
 */
public class InMemoryStore {
    private final Map<String, List<Envelope>> db = new ConcurrentHashMap<>();

    public Map<String, List<Envelope>> getDb() {
        return db;
    }
}
