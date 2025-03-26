package com.meshql.api.restlette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshql.core.Auth;
import com.meshql.core.Envelope;
import com.meshql.core.Repository;
import com.meshql.core.Validator;
import com.tailoredshapes.stash.Stash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.time.Instant;
import java.util.*;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.UnderBar.map;

public class CrudHandler {
    private static final Logger logger = LoggerFactory.getLogger(CrudHandler.class);
    private final ObjectMapper objectMapper;

    private final Auth authorizer;
    private final Repository repository;
    private final List<String> tokens;
    private final Validator validator;

    public CrudHandler(Auth authorizer, Repository repository, Validator validator,
                       List<String> tokens) {
        this.authorizer = authorizer;
        this.repository = repository;
        this.tokens = tokens != null ? tokens : List.of();
        this.validator = validator;

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Handle create request
     */
    public Stash create(Request request, Response response) {
        try {
            Stash payload = Stash.parseJSON(request.body());
            List<String> authTokens = authorizer.getAuthToken(payload);

            boolean isValid = validator.validate(payload).get();
            if (!isValid) {
                response.status(400);
                return stash("error", "Invalid payload");

            }

            String id = UUID.randomUUID().toString();
            Envelope envelope = new Envelope(
                    id,
                    payload,
                    Instant.now(),
                    false,
                    authTokens);

            Envelope result = repository.create(envelope, tokens);
            response.status(201);
            return result.payload();
        } catch (Exception e) {
            logger.error("Failed to create resource", e);
            response.status(500);
            return stash("error", "Failed to create resource: " + e.getMessage());

        }
    }

    /**
     * Handle read request
     */
    public Stash read(Request request, Response response) {
        try {
            String id = request.params(":id");
            List<String> authTokens = getAuthTokens(request);

            Optional<Envelope> envelope = repository.read(id, authTokens, Instant.now());

            if (envelope.isEmpty()) {
                response.status(404);
                return stash("error", "Resource not found");

            }

            return envelope.get().payload();
        } catch (Exception e) {
            logger.error("Failed to read resource", e);
            response.status(500);
            return stash("error", "Failed to read resource: " + e.getMessage());

        }
    }

    /**
     * Handle update request
     */
    public Stash update(Request request, Response response) {
        try {
            String id = request.params(":id");
            Stash payload = Stash.parseJSON(request.body());
            List<String> authTokens = getAuthTokens(request);

            boolean isValid = validator.validate(payload).get();
            if (!isValid) {
                response.status(400);
                return stash("error", "Invalid payload");

            }

            Optional<Envelope> existing = repository.read(id, authTokens, Instant.now());

            if (existing.isEmpty()) {
                response.status(404);
                return stash("error", "Resource not found");

            }

            Envelope envelope = new Envelope(
                    id,
                    payload,
                    Instant.now(),
                    false,
                    authTokens);

            Envelope result = repository.create(envelope, tokens);
            return result.payload();
        } catch (Exception e) {
            logger.error("Failed to update resource", e);
            response.status(500);
            return stash("error", "Failed to update resource: " + e.getMessage());

        }
    }

    /**
     * Handle delete request
     */
    public Stash remove(Request request, Response response) {
        try {
            String id = request.params(":id");
            List<String> authTokens = getAuthTokens(request);

            Boolean result = repository.remove(id, authTokens);

            if (!result) {
                response.status(404);
                return stash("error", "Resource not found or could not be deleted");

            }

            return stash(
                    "id", id,
                    "status", "deleted");
        } catch (Exception e) {
            logger.error("Failed to delete resource", e);
            response.status(500);
            return stash("error", "Failed to delete resource: " + e.getMessage());

        }
    }

    /**
     * Handle list request
     */
    public List<Stash> list(Request request, Response response) {
        try {
            List<String> authTokens = getAuthTokens(request);
            List<Envelope> results = repository.list(authTokens);

            return map(results, Envelope::payload);
        } catch (Exception e) {
            logger.error("Failed to list resources", e);
            response.status(500);
            String message = "Failed to list resources: " + e.getMessage();

            return List.of(stash("error", message));
        }
    }

    /**
     * Handle bulk create request
     */
    public Object bulkCreate(Request request, Response response) {
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    request.body(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            List<String> authTokens = getAuthTokens(request);
            List<Envelope> envelopes = new ArrayList<>();

            for (Map<String, Object> item : items) {
                Stash payload = new Stash(item);

                boolean isValid = validator.validate(payload).get();
                if (!isValid) {
                    response.status(400);
                    return stash("error", "Invalid payload in batch");

                }

                String id = UUID.randomUUID().toString();
                Envelope envelope = new Envelope(
                        id,
                        payload,
                        Instant.now(),
                        false,
                        authTokens);

                envelopes.add(envelope);
            }

            List<Envelope> results = repository.createMany(envelopes, tokens);
            response.status(201);
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            logger.error("Failed to bulk create resources", e);
            response.status(500);
            return stash("error", "Failed to bulk create resources: " + e.getMessage());

        }
    }

    /**
     * Handle bulk read request
     */
    public Object bulkRead(Request request, Response response) {
        try {
            String idsParam = request.queryParams("ids");
            if (idsParam == null || idsParam.isEmpty()) {
                response.status(400);
                return stash("error", "Missing ids parameter");

            }

            List<String> ids = Arrays.asList(idsParam.split(","));
            List<String> authTokens = getAuthTokens(request);

            List<Envelope> results = repository.readMany(ids, authTokens);

            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            logger.error("Failed to bulk read resources", e);
            response.status(500);
            return stash("error", "Failed to bulk read resources: " + e.getMessage());

        }
    }

    /**
     * Get authentication tokens from request
     */
    private List<String> getAuthTokens(Request request) {
        try {
            String authHeader = request.headers("Authorization");
            if (authHeader != null && !authHeader.isEmpty()) {
                Stash context = new Stash();
                context.put("headers", Map.of("authorization", authHeader));
                return authorizer.getAuthToken(context);
            }
            return List.of();
        } catch (Exception e) {
            logger.error("Failed to get auth tokens", e);
            return List.of();
        }
    }

}