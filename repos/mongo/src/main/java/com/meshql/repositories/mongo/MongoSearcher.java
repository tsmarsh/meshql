package com.meshql.repositories.mongo;

import com.github.jknack.handlebars.Template;
import com.meshql.core.Auth;
import com.meshql.core.Envelope;
import com.meshql.core.Searcher;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.tailoredshapes.stash.Stash;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.meshql.repositories.mongo.Converters.documentToEnvelope;

/**
 * MongoDB implementation of the Searcher interface.
 */
public class MongoSearcher implements Searcher {
    private static final Logger logger = LoggerFactory.getLogger(MongoSearcher.class);

    private final MongoCollection<Document> collection;
    private final Auth authorizer;

    /**
     * Constructor for MongoSearcher.
     *
     * @param collection MongoDB collection to search
     * @param authorizer Authorization service
     */
    public MongoSearcher(MongoCollection<Document> collection, Auth authorizer) {
        this.collection = collection;
        this.authorizer = authorizer;
    }

    /**
     * Process a query template with parameters.
     *
     * @param parameters    Parameters to apply to the template
     * @param queryTemplate Handlebars template for the query
     * @return Processed query as a Document
     */
    private Document processQueryTemplate(Stash parameters, Template queryTemplate) {
        try {
            String query = queryTemplate.apply(parameters);
            try {
                // Parse the query string into a Document
                return Document.parse(query);
            } catch (Exception e) {
                logger.error("Failed to create query: Template: {}, Parameters: {}, Query: {}",
                        queryTemplate, parameters, query, e);
                throw new RuntimeException("Failed to parse query", e);
            }
        } catch (IOException e) {
            logger.error("Failed to apply template", e);
            throw new RuntimeException("Failed to apply template", e);
        }
    }

    @Override
    public Stash find(Template queryTemplate, Stash args, List<String> tokens, long timestamp) {
        Document query = processQueryTemplate(args, queryTemplate);

        // Add timestamp filter
        query.append("createdAt", new Document("$lt", new Date(timestamp)));

        try {
            List<Document> results = collection.find(query)
                    .sort(Sorts.descending("createdAt"))
                    .into(new ArrayList<>());

            if (!results.isEmpty()) {
                Document result = results.get(0);
                Envelope envelope = documentToEnvelope(result);

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

            return new Stash();
        } catch (Exception e) {
            logger.error("Error finding document: {}", e.getMessage(), e);
            return new Stash();
        }
    }

    @Override
    public List<Stash> findAll(Template queryTemplate, Stash args, List<String> tokens, long timestamp) {
        Document query = processQueryTemplate(args, queryTemplate);

        // Add timestamp filter
        query.append("createdAt", new Document("$lt", new Date(timestamp)));

        try {
            // Create a custom aggregation pipeline using Document objects directly
            List<Bson> pipeline = Arrays.asList(
                    Aggregates.match(query),
                    Aggregates.sort(Sorts.descending("createdAt")),
                    // Use Document directly for the group stage
                    new Document("$group",
                            new Document("_id", "$id")
                                    .append("doc", new Document("$first", "$$ROOT"))),
                    // Use Document directly for the replaceRoot stage
                    new Document("$replaceRoot",
                            new Document("newRoot", "$doc")));

            List<Document> results = collection.aggregate(pipeline).into(new ArrayList<>());

            return results.stream()
                    .map(Converters::documentToEnvelope)
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
        } catch (Exception e) {
            logger.error("Error finding documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}