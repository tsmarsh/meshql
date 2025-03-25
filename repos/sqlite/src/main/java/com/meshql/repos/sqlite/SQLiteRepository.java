package com.meshql.repos.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.meshql.core.Envelope;
import com.meshql.core.Repository;
import com.tailoredshapes.stash.Stash;
import com.tailoredshapes.underbar.ocho.UnderBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SQLiteRepository implements Repository {
    private static final Logger log = LoggerFactory.getLogger(SQLiteRepository.class);
    private final Connection connection;
    private final String table;
    private final ObjectMapper objectMapper;

    public SQLiteRepository(Connection connection, String table) {
        this.connection = connection;
        this.table = table;
        this.objectMapper = new ObjectMapper();
    }

    public void initialize() {
        String createTableSQL = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "    _id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "    id TEXT, " +
            "    payload TEXT NOT NULL, " +
            "    created_at INTEGER DEFAULT (strftime('%%s', 'now')), " +
            "    deleted INTEGER DEFAULT 0, " +
            "    authorized_tokens TEXT, " +
            "    UNIQUE (id, created_at)" +
            ");", table);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e){
            log.error("Failed to create SQLite table " + table);
            throw new RuntimeException(e);
        }
    }

    private Envelope rowToEnvelope(ResultSet rs) throws SQLException, JsonProcessingException {
        Envelope envelope = new Envelope(
                rs.getString("id"),
                Stash.parseJSON(rs.getString("payload")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                (rs.getInt("deleted") == 1),
                objectMapper.readValue(rs.getString("authorized_tokens"), new TypeReference<>(){})
        );
        
        return envelope;
    }

    @Override
    public Envelope create(Envelope doc, List<String> tokens) {
        String query = String.format(
            "INSERT INTO %s (id, payload, created_at, authorized_tokens) " +
            "VALUES (?, ?, ?, ?);", table);

        String id = doc.id() != null ? doc.id() : Generators.timeBasedGenerator().generate().toString();
        long createdAt = System.currentTimeMillis();
        
        try {
            PreparedStatement pstmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, id);
            pstmt.setString(2, objectMapper.writeValueAsString(doc.payload()));
            pstmt.setLong(3, createdAt);
            pstmt.setString(4, objectMapper.writeValueAsString(tokens != null ? tokens : Collections.emptyList()));
            
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long lastId = generatedKeys.getLong(1);
                    
                    try (PreparedStatement selectStmt = connection.prepareStatement(
                            String.format("SELECT * FROM %s WHERE _id = ?", table))) {
                        selectStmt.setLong(1, lastId);
                        
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            if (rs.next()) {
                                return rowToEnvelope(rs);
                            }
                        }
                    }
                }
            }
            
            return null;
        } catch (SQLException e) {
            if (e.getMessage().contains("SQLITE_CONSTRAINT")) {
                try {
                    TimeUnit.MILLISECONDS.sleep(2);
                    return create(doc, tokens);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying create operation", ex);
                }
            }
            throw new RuntimeException("Error creating document", e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing JSON", e);
        }
    }

    @Override
    public List<Envelope> createMany(List<Envelope> payloads, List<String> tokens) {
        List<Envelope> created = new ArrayList<>();
        
        try {
            connection.setAutoCommit(false);
            
            for (Envelope envelope : payloads) {
                created.add(create(envelope, tokens));
            }
            
            connection.commit();
            connection.setAutoCommit(true);
            
            return created;
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                throw new RuntimeException("Error rolling back transaction", ex);
            }
            throw new RuntimeException("Error creating multiple documents", e);
        }
    }

    @Override
    public Optional<Envelope> read(String id, List<String> tokens, Instant at) {
        long atMs = at != null ? at.toEpochMilli() : System.currentTimeMillis();
        
        StringBuilder queryBuilder = new StringBuilder(String.format(
            "SELECT * FROM %s " +
            "WHERE id = ? AND deleted = 0 AND created_at <= ? ", table));
            
        if (tokens != null && !tokens.isEmpty()) {
            queryBuilder.append("AND EXISTS (SELECT 1 FROM json_each(authorized_tokens) WHERE value IN (SELECT value FROM json_each(?))) ");
        }
        
        queryBuilder.append("ORDER BY created_at DESC LIMIT 1;");
        
        try (PreparedStatement pstmt = connection.prepareStatement(queryBuilder.toString())) {
            pstmt.setString(1, id);
            pstmt.setLong(2, atMs);
            
            if (tokens != null && !tokens.isEmpty()) {
                pstmt.setString(3, objectMapper.writeValueAsString(tokens));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rowToEnvelope(rs));
                }
            }
            
            return Optional.empty();
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Error reading document", e);
        }
    }

    @Override
    public List<Envelope> readMany(List<String> ids, List<String> tokens) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        long now = System.currentTimeMillis();
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
        }
        
        StringBuilder queryBuilder = new StringBuilder(String.format(
            "SELECT * FROM %s " +
            "WHERE id IN (%s) AND deleted = 0 AND created_at <= ? ", 
            table, placeholders));
            
        if (tokens != null && !tokens.isEmpty()) {
            queryBuilder.append("AND EXISTS (SELECT 1 FROM json_each(authorized_tokens) WHERE value IN (SELECT value FROM json_each(?))) ");
        }
        
        queryBuilder.append("ORDER BY created_at DESC;");
        
        try (PreparedStatement pstmt = connection.prepareStatement(queryBuilder.toString())) {
            int paramIndex = 1;
            
            for (String id : ids) {
                pstmt.setString(paramIndex++, id);
            }
            
            pstmt.setLong(paramIndex++, now);
            
            if (tokens != null && !tokens.isEmpty()) {
                pstmt.setString(paramIndex, objectMapper.writeValueAsString(tokens));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                Set<String> seen = new HashSet<>();
                List<Envelope> envelopes = new ArrayList<>();
                
                while (rs.next()) {
                    String id = rs.getString("id");
                    if (!seen.contains(id)) {
                        seen.add(id);
                        envelopes.add(rowToEnvelope(rs));
                    }
                }
                
                return envelopes;
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Error reading multiple documents", e);
        }
    }

    @Override
    public Boolean remove(String id, List<String> tokens) {
        StringBuilder queryBuilder = new StringBuilder(String.format(
            "UPDATE %s SET deleted = 1 WHERE id = ? ", table));
            
        if (tokens != null && !tokens.isEmpty()) {
            queryBuilder.append("AND EXISTS (SELECT 1 FROM json_each(authorized_tokens) WHERE value IN (SELECT value FROM json_each(?))) ");
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(queryBuilder.toString())) {
            pstmt.setString(1, id);
            
            if (tokens != null && !tokens.isEmpty()) {
                pstmt.setString(2, objectMapper.writeValueAsString(tokens));
            }
            
            int changes = pstmt.executeUpdate();
            return changes > 0;
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Error removing document", e);
        }
    }

    @Override
    public Map<String, Boolean> removeMany(List<String> ids, List<String> tokens) {
        Map<String, Boolean> result = new HashMap<>();
        
        try {
            connection.setAutoCommit(false);
            
            for (String id : ids) {
                result.put(id, remove(id, tokens));
            }
            
            connection.commit();
            connection.setAutoCommit(true);
            
            return result;
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                throw new RuntimeException("Error rolling back transaction", ex);
            }
            throw new RuntimeException("Error removing multiple documents", e);
        }
    }

    @Override
    public List<Envelope> list(List<String> tokens) {
        StringBuilder queryBuilder = new StringBuilder(String.format(
            "SELECT * FROM %s WHERE deleted = 0 ", table));
            
        if (tokens != null && !tokens.isEmpty()) {
            queryBuilder.append("AND EXISTS (SELECT 1 FROM json_each(authorized_tokens) WHERE value IN (SELECT value FROM json_each(?))) ");
        }
        
        queryBuilder.append("ORDER BY created_at DESC;");
        
        try (PreparedStatement pstmt = connection.prepareStatement(queryBuilder.toString())) {
            if (tokens != null && !tokens.isEmpty()) {
                pstmt.setString(1, objectMapper.writeValueAsString(tokens));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                Set<String> seen = new HashSet<>();
                List<Envelope> envelopes = new ArrayList<>();
                
                while (rs.next()) {
                    String id = rs.getString("id");
                    if (!seen.contains(id)) {
                        seen.add(id);
                        envelopes.add(rowToEnvelope(rs));
                    }
                }
                
                return envelopes;
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Error listing documents", e);
        }
    }
} 