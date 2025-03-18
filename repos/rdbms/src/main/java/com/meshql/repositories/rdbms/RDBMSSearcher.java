package com.meshql.repositories.rdbms;

import com.github.jknack.handlebars.Template;
import com.meshql.core.Auth;
import com.meshql.core.Envelope;
import com.meshql.core.Searcher;
import com.tailoredshapes.stash.Stash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.meshql.repositories.rdbms.Converters.resultSetToEnvelope;

/**
 * PostgreSQL implementation of the Searcher interface.
 */
public abstract class RDBMSSearcher implements Searcher {
    private static final Logger logger = LoggerFactory.getLogger(RDBMSSearcher.class);

    private final DataSource dataSource;
    private final String tableName;
    private final Auth authorizer;

    /**
     * SQL template for finding a single record.
     */
    private final String SINGLETON_QUERY_TEMPLATE;

    /**
     * SQL template for finding multiple records.
     */
    private final String VECTOR_QUERY_TEMPLATE;

    /**
     * Constructor for PostgresSearcher.
     *
     * @param dataSource DataSource for database connections
     * @param tableName  Name of the table to search
     * @param authorizer Authorization service
     */
    public RDBMSSearcher(String singletonTemplate, String vectorTemplate, DataSource dataSource, String tableName, Auth authorizer) {
        this.SINGLETON_QUERY_TEMPLATE = singletonTemplate;
        this.VECTOR_QUERY_TEMPLATE = vectorTemplate;
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.authorizer = authorizer;
    }

    /**
     * Processes a Handlebars template with the provided parameters.
     *
     * @param parameters    Parameters to apply to the template
     * @param queryTemplate Handlebars template for the query
     * @return Processed query string
     */
    private String processQueryTemplate(Stash parameters, Template queryTemplate) {
        try {
            return queryTemplate.apply(parameters);
        } catch (IOException e) {
            logger.error("Failed to apply template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to apply template", e);
        }
    }

    @Override
    public Stash find(Template queryTemplate, Stash args, List<String> tokens, long timestamp) {
        String filters = processQueryTemplate(args, queryTemplate);
        String sql = String.format(SINGLETON_QUERY_TEMPLATE, tableName, tableName, filters);

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, new Timestamp(timestamp));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Envelope envelope = resultSetToEnvelope(rs);

                    if (authorizer.isAuthorized(tokens, envelope)) {
                        Stash payload = envelope.payload();
                        if (payload != null) {
                            payload.put("id", envelope.id());
                            return payload;
                        }
                    } else {
                        logger.trace("Not authorized to access document");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error executing find query: {}", e.getMessage(), e);
        }

        return new Stash();
    }

    @Override
    public List<Stash> findAll(Template queryTemplate, Stash args, List<String> tokens, long timestamp) {
        String filters = processQueryTemplate(args, queryTemplate);
        String sql = String.format(VECTOR_QUERY_TEMPLATE, tableName, filters, tableName);

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, new Timestamp(timestamp));

            try (ResultSet rs = stmt.executeQuery()) {
                List<Envelope> envelopes = new ArrayList<>();
                while (rs.next()) {
                    envelopes.add(resultSetToEnvelope(rs));
                }

                return envelopes.stream()
                        .filter(envelope -> authorizer.isAuthorized(tokens, envelope))
                        .map(envelope -> {
                            Stash payload = envelope.payload();
                            if (payload != null) {
                                payload.put("id", envelope.id());
                                return payload;
                            }
                            return null;
                        })
                        .filter(payload -> payload != null)
                        .collect(Collectors.toList());
            }
        } catch (SQLException e) {
            logger.error("Error executing findAll query: {}", e.getMessage(), e);
        }

        return Collections.emptyList();
    }
}