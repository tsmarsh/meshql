package com.meshql.repositories.memory;

import com.meshql.core.Envelope;
import com.meshql.core.Repository;
import com.tailoredshapes.stash.Stash;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of the Repository interface.
 * Stores envelopes in memory with version history support.
 */
public class InMemoryRepository implements Repository {
    private final Map<String, List<Envelope>> db = new ConcurrentHashMap<>();

    @Override
    public Envelope create(Envelope envelope, List<String> tokens) {
        String id = envelope.id() != null ? envelope.id() : UUID.randomUUID().toString();
        Stash payload = envelope.payload();
        Instant now = Instant.now();
        List<String> authTokens = tokens != null ? tokens : List.of();
        
        Envelope newEnvelope = new Envelope(id, payload, now, false, authTokens);
        
        db.compute(id, (key, existingEnvelopes) -> {
            List<Envelope> envelopes = existingEnvelopes != null ? existingEnvelopes : new ArrayList<>();
            envelopes.add(newEnvelope);
            return envelopes;
        });
        
        return newEnvelope;
    }
    
    @Override
    public List<Envelope> createMany(List<Envelope> payloads, List<String> tokens) {
        return payloads.stream()
                .map(envelope -> create(envelope, tokens))
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Envelope> read(String id, List<String> tokens, Instant createdAt) {
        List<Envelope> envelopes = db.get(id);
        if (envelopes == null || envelopes.isEmpty()) {
            return Optional.empty();
        }
        
        // Find the latest envelope that is not deleted and created before or at the specified timestamp
        return envelopes.stream()
                .filter(e -> !e.deleted() && !e.createdAt().isAfter(createdAt))
                .max(Comparator.comparing(Envelope::createdAt));
    }
    
    @Override
    public List<Envelope> readMany(List<String> ids, List<String> tokens) {
        return ids.stream()
                .map(id -> read(id, tokens, Instant.now()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<Envelope> list(List<String> tokens) {
        List<Envelope> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        // For each ID in the database
        for (List<Envelope> envelopeList : db.values()) {
            // Filter out deleted envelopes
            List<Envelope> nonDeletedEnvelopes = envelopeList.stream()
                    .filter(e -> !e.deleted())
                    .collect(Collectors.toList());
            
            if (!nonDeletedEnvelopes.isEmpty()) {
                // Sort by createdAt descending to get the latest version
                nonDeletedEnvelopes.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
                Envelope latestEnvelope = nonDeletedEnvelopes.get(0);
                
                // Add the latest envelope if it hasn't been added yet
                if (!seen.contains(latestEnvelope.id())) {
                    seen.add(latestEnvelope.id());
                    result.add(latestEnvelope);
                }
            }
        }
        
        return result;
    }
    
    @Override
    public Boolean remove(String id, List<String> tokens) {
        List<Envelope> envelopes = db.get(id);
        if (envelopes == null || envelopes.isEmpty()) {
            return false;
        }
        
        // Mark all versions as deleted
        List<Envelope> updatedEnvelopes = envelopes.stream()
                .map(e -> new Envelope(e.id(), e.payload(), e.createdAt(), true, e.authorizedTokens()))
                .collect(Collectors.toList());
        
        db.put(id, updatedEnvelopes);
        return true;
    }
    
    @Override
    public Map<String, Boolean> removeMany(List<String> ids, List<String> tokens) {
        Map<String, Boolean> results = new HashMap<>();
        for (String id : ids) {
            results.put(id, remove(id, tokens));
        }
        return results;
    }
} 