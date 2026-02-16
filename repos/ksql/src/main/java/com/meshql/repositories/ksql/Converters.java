package com.meshql.repositories.ksql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.core.Envelope;
import com.tailoredshapes.stash.Stash;

import java.time.Instant;
import java.util.*;

public interface Converters {
    ObjectMapper MAPPER = new ObjectMapper();

    static String envelopeToJson(Envelope envelope) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("payload", envelope.payload() != null ? MAPPER.writeValueAsString(stashToMap(envelope.payload())) : "{}");
            map.put("created_at", envelope.createdAt().toEpochMilli());
            map.put("deleted", envelope.deleted());
            map.put("authorized_tokens", envelope.authorizedTokens() != null
                    ? MAPPER.writeValueAsString(envelope.authorizedTokens())
                    : "[]");
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize envelope to JSON", e);
        }
    }

    static Envelope rowToEnvelope(String id, Map<String, Object> row) {
        String payloadStr = getStringField(row, "PAYLOAD", "payload");
        Stash payload = parsePayload(payloadStr);

        Object createdAtObj = getField(row, "CREATED_AT", "created_at");
        long createdAtMillis = createdAtObj instanceof Number ? ((Number) createdAtObj).longValue() : 0L;
        Instant createdAt = Instant.ofEpochMilli(createdAtMillis);

        Object deletedObj = getField(row, "DELETED", "deleted");
        boolean deleted = deletedObj instanceof Boolean ? (Boolean) deletedObj : false;

        String tokensStr = getStringField(row, "AUTHORIZED_TOKENS", "authorized_tokens");
        List<String> authorizedTokens = parseTokens(tokensStr);

        return new Envelope(id, payload, createdAt, deleted, authorizedTokens);
    }

    static Stash parsePayload(String json) {
        if (json == null || json.isEmpty()) {
            return new Stash();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {});
            return mapToStash(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse payload JSON", e);
        }
    }

    static List<String> parseTokens(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    static Stash mapToStash(Map<String, Object> map) {
        Stash stash = new Stash();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                stash.put(entry.getKey(), mapToStash((Map<String, Object>) value));
            } else {
                stash.put(entry.getKey(), value);
            }
        }
        return stash;
    }

    static Map<String, Object> stashToMap(Stash stash) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : stash.keySet()) {
            Object value = stash.get(key);
            if (value instanceof Stash) {
                map.put(key, stashToMap((Stash) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private static Object getField(Map<String, Object> row, String upperKey, String lowerKey) {
        Object val = row.get(upperKey);
        if (val == null) val = row.get(lowerKey);
        return val;
    }

    private static String getStringField(Map<String, Object> row, String upperKey, String lowerKey) {
        Object val = getField(row, upperKey, lowerKey);
        return val != null ? val.toString() : null;
    }
}
