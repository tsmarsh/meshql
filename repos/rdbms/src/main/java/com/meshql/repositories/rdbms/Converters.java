package com.meshql.repositories.rdbms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.core.Envelope;
import com.tailoredshapes.stash.Stash;
import com.tailoredshapes.underbar.ocho.Die;
import com.tailoredshapes.underbar.ocho.UnderBar;

import java.sql.*;
import java.time.Instant;
import java.util.*;

import static com.tailoredshapes.underbar.ocho.UnderBar.list;
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
        Stash payload = Stash.parseJSON(rs.getString("payload"));

        // Get authorized tokens
        List<String> authorizedTokens;

        try {
           Array tokenArray = rs.getArray("authorized_tokens");
           authorizedTokens = tokenArray == null ? list() : Arrays.asList((String[]) (tokenArray.getArray()));
        } catch (SQLFeatureNotSupportedException se){
            String tokenString = rs.getString("authorized_tokens");
            authorizedTokens = Die.rethrow(() -> OBJECT_MAPPER.readValue(tokenString, new TypeReference<>(){}));
        }

        return new Envelope(id, payload, createdAt, deleted, authorizedTokens);
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