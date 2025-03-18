package com.meshql.repositories.rdbms;

import com.fasterxml.uuid.Generators;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.meshql.core.Envelope;
import com.meshql.core.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.meshql.repositories.rdbms.Converters.*;


public abstract class RDBMSRepository implements Repository {
    private static final Logger logger = LoggerFactory.getLogger(RDBMSRepository.class);
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2;

    private final DataSource dataSource;
    private final String tableName;
    public  final Handlebars handlebars;
    private final RequiredTemplates sqlTemplates;

    /**
     * Constructor for PostgresRepository.
     *
     * @param dataSource DataSource for database connections
     * @param tableName  Name of the table to use for storage
     */
    public RDBMSRepository(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.handlebars = new Handlebars();
        this.sqlTemplates    = initializeTemplates();
    }

    public abstract RequiredTemplates initializeTemplates();

    /**
     * Initializes the database schema.
     *
     * @throws SQLException if a database access error occurs
     */
    public void initialize() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);

            for(Template t : sqlTemplates.createScripts()) {
                stmt.execute(t.apply(context));
            };

            logger.info("Initialized PostgreSQL repository with table: {}", tableName);
        } catch (IOException e) {
            throw new SQLException("Failed to render SQL template", e);
        }
    }

    @Override
    public Envelope create(Envelope envelope, List<String> tokens) {
        return createWithRetry(envelope, tokens, 0);
    }

    /**
     * Creates a document with retry logic for handling unique constraint
     * violations.
     *
     * @param envelope   Envelope to create
     * @param tokens     Authorization tokens
     * @param retryCount Current retry count
     * @return Created envelope
     */
    private Envelope createWithRetry(Envelope envelope, List<String> tokens, int retryCount) {
        String id = envelope.id() != null ? envelope.id() : Generators.timeBasedGenerator().generate().toString();

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);

            String sql = sqlTemplates.insert().apply(context);
            String tokenSql = sqlTemplates.insertToken().apply(context);

            Connection conn = null;
            try {
                conn = dataSource.getConnection();
                conn.setAutoCommit(false);

                // Insert main record
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id);
                    stmt.setString(2, stashToJson(envelope.payload()));

                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        throw new SQLException("Failed to create document, no rows returned");
                    }

                    Envelope result = resultSetToEnvelope(rs);
                    rs.close();

                    // Insert token records if any
                    if (tokens != null && !tokens.isEmpty()) {
                        try (PreparedStatement tokenStmt = conn.prepareStatement(tokenSql)) {
                            for (int i = 0; i < tokens.size(); i++) {
                                tokenStmt.setString(1, id);
                                tokenStmt.setTimestamp(2, instantToTimestamp(result.createdAt()));
                                tokenStmt.setString(3, tokens.get(i));
                                tokenStmt.setInt(4, i);
                                tokenStmt.addBatch();
                            }
                            tokenStmt.executeBatch();
                        }
                    }

                    conn.commit();
                    
                    // Set the authorized tokens on the result
                    return new Envelope(
                        result.id(),
                        result.payload(),
                        result.createdAt(),
                        result.deleted(),
                        tokens != null ? tokens : List.of()
                    );
                }
            } catch (SQLException e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackException) {
                        logger.error("Failed to rollback transaction", rollbackException);
                    }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException closeException) {
                        logger.error("Failed to close connection", closeException);
                    }
                }
            }
        } catch (SQLException e) {
            // PostgreSQL unique violation error code is 23505
            if (e.getSQLState() != null && e.getSQLState().equals("23505") && retryCount < MAX_RETRIES) {
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    return createWithRetry(envelope, tokens, retryCount + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
            logger.error("Error creating document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create document", e);
        } catch (IOException e) {
            logger.error("Failed to render SQL template", e);
            throw new RuntimeException("Failed to render SQL template", e);
        }
    }

    @Override
    public List<Envelope> createMany(List<Envelope> envelopes, List<String> tokens) {
        List<Envelope> created = new ArrayList<>();
        for (Envelope envelope : envelopes) {
            created.add(create(envelope, tokens));
        }
        return created;
    }

    @Override
    public Optional<Envelope> read(String id, List<String> tokens, Instant createdAt) {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);
            context.put("hasTokens", tokens != null && !tokens.isEmpty());
            if (tokens != null && !tokens.isEmpty()) {
                context.put("tokens", tokens);
            }

            String sql = sqlTemplates.read().apply(context);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, id);
                stmt.setTimestamp(2, instantToTimestamp(createdAt));

                // Set token parameters if needed
                if (tokens != null && !tokens.isEmpty()) {
                    for (int i = 0; i < tokens.size(); i++) {
                        stmt.setString(i + 3, tokens.get(i));
                    }
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(resultSetToEnvelope(rs));
                    } else {
                        return Optional.empty();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading document: {}", e.getMessage(), e);
            return Optional.empty();
        } catch (IOException e) {
            logger.error("Failed to render SQL template", e);
            return Optional.empty();
        }
    }

    @Override
    public List<Envelope> readMany(List<String> ids, List<String> tokens) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);
            context.put("hasTokens", tokens != null && !tokens.isEmpty());
            if (tokens != null && !tokens.isEmpty()) {
                context.put("tokens", tokens);
            }

            String sql = sqlTemplates.readMany().apply(context);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Join ids with commas for the string_to_array function
                stmt.setString(1, String.join(",", ids));

                // Set token parameters if needed
                if (tokens != null && !tokens.isEmpty()) {
                    for (int i = 0; i < tokens.size(); i++) {
                        stmt.setString(i + 2, tokens.get(i));
                    }
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    return resultSetToEnvelopes(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading multiple documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (IOException e) {
            logger.error("Failed to render SQL template", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Boolean remove(String id, List<String> tokens) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);
            context.put("hasTokens", tokens != null && !tokens.isEmpty());
            if (tokens != null && !tokens.isEmpty()) {
                context.put("tokens", tokens);
            }

            String sql = sqlTemplates.remove().apply(context);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, id);

                // Set token parameters if needed
                if (tokens != null && !tokens.isEmpty()) {
                    for (int i = 0; i < tokens.size(); i++) {
                        stmt.setString(i + 2, tokens.get(i));
                    }
                }

                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error removing document: {}", e.getMessage(), e);
            return false;
        } catch (IOException e) {
            logger.error("Failed to render SQL template", e);
            return false;
        }
    }

    @Override
    public Map<String, Boolean> removeMany(List<String> ids, List<String> tokens) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);
            context.put("hasTokens", tokens != null && !tokens.isEmpty());
            if (tokens != null && !tokens.isEmpty()) {
                context.put("tokens", tokens);
            }

            String sql = sqlTemplates.removeMany().apply(context);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Join ids with commas for the string_to_array function
                stmt.setString(1, String.join(",", ids));

                // Set token parameters if needed
                if (tokens != null && !tokens.isEmpty()) {
                    for (int i = 0; i < tokens.size(); i++) {
                        stmt.setString(i + 2, tokens.get(i));
                    }
                }

                stmt.executeUpdate();

                // Return all IDs as successfully removed
                Map<String, Boolean> result = new HashMap<>();
                for (String id : ids) {
                    result.put(id, true);
                }
                return result;
            }
        } catch (SQLException e) {
            logger.error("Error removing multiple documents: {}", e.getMessage(), e);

            // Return all IDs as failed
            Map<String, Boolean> result = new HashMap<>();
            for (String id : ids) {
                result.put(id, false);
            }
            return result;
        } catch (IOException e) {
            logger.error("Failed to render SQL template", e);

            // Return all IDs as failed
            Map<String, Boolean> result = new HashMap<>();
            for (String id : ids) {
                result.put(id, false);
            }
            return result;
        }
    }

    @Override
    public List<Envelope> list(List<String> tokens) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", tableName);
            context.put("hasTokens", tokens != null && !tokens.isEmpty());
            if (tokens != null && !tokens.isEmpty()) {
                context.put("tokens", tokens);
            }

            String sql = sqlTemplates.list().apply(context);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Set token parameters if needed
                if (tokens != null && !tokens.isEmpty()) {
                    for (int i = 0; i < tokens.size(); i++) {
                        stmt.setString(i + 1, tokens.get(i));
                    }
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    return resultSetToEnvelopes(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (IOException e) {
            logger.error("Failed to render SQL template", e);
            return Collections.emptyList();
        }
    }
}