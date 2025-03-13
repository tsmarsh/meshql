package com.meshql.repositories.rdbms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.core.Envelope;
import com.tailoredshapes.stash.Stash;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static com.tailoredshapes.underbar.ocho.UnderBar.map;

/**
 * Utility class for converting between JDBC ResultSet objects and domain
 * objects.
 */
public interface Converters {
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Converts a ResultSet row to an Envelope object.
     *
     * @param rs ResultSet positioned at the row to convert
     * @return Envelope object
     * @throws SQLException if a database access error occurs
     */
    static Envelope resultSetToEnvelope(ResultSet rs) throws SQLException {
        if (rs == null) {
            return null;
        }

        String id = rs.getString("id");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        boolean deleted = rs.getBoolean("deleted");

        // Convert JSON payload to Stash
        String payloadJson = rs.getString("payload");
        Stash payload = null;
        if (payloadJson != null) {
            try {
                Map<String, Object> payloadMap = OBJECT_MAPPER.readValue(payloadJson, Map.class);
                payload = new Stash();
                for (Map.Entry<String, Object> entry : payloadMap.entrySet()) {
                    payload.put(entry.getKey(), entry.getValue());
                }
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to parse JSON payload", e);
            }
        }

        // Get authorized tokens
        List<String> authorizedTokens = new ArrayList<>();
        Array tokensArray = rs.getArray("authorized_tokens");
        if (tokensArray != null) {
            String[] tokens = (String[]) tokensArray.getArray();
            authorizedTokens = Arrays.asList(tokens);
        }

        return new Envelope(id, payload, createdAt, deleted, authorizedTokens);
    }

    /**
     * Converts a Stash object to a JSON string.
     *
     * @param stash Stash object to convert
     * @return JSON string representation
     * @throws SQLException if conversion fails
     */
    static String stashToJson(Stash stash) throws SQLException {
        if (stash == null) {
            return null;
        }

        try {
            Map<String, Object> map = new HashMap<>();
            for (String key : stash.keySet()) {
                map.put(key, stash.get(key));
            }
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to convert Stash to JSON", e);
        }
    }

    /**
     * Converts a list of ResultSet rows to a list of Envelope objects.
     *
     * @param rs ResultSet containing the rows to convert
     * @return List of Envelope objects
     * @throws SQLException if a database access error occurs
     */
    static List<Envelope> resultSetToEnvelopes(ResultSet rs) throws SQLException {
        List<Envelope> envelopes = new ArrayList<>();
        while (rs.next()) {
            envelopes.add(resultSetToEnvelope(rs));
        }
        return envelopes;
    }

    /**
     * Converts a SQL timestamp to an Instant.
     *
     * @param timestamp SQL timestamp
     * @return Instant object
     */
    static Instant timestampToInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    /**
     * Converts an Instant to a SQL timestamp.
     *
     * @param instant Instant object
     * @return SQL timestamp
     */
    static Timestamp instantToTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}