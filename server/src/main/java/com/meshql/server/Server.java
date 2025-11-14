package com.meshql.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.api.graphql.DTOFactory;
import com.meshql.api.graphql.Graphlette;
import com.meshql.api.graphql.Root;
import com.meshql.api.restlette.JSONSchemaValidator;
import com.meshql.api.restlette.Restlette;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.*;
import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.RestletteConfig;
import graphql.schema.DataFetcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static spark.Spark.*;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Plugin> plugins;

    public Server(Map<String, Plugin> plugins) {
        this.plugins = plugins;
    }

    public void init(Config config) {
        // Set port
        port(config.port());

        // Add CORS support
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        options("/*", (request, response) -> {
            return "OK";
        });

        // Process authentication
        Auth auth = processAuth(config);

        // Add health check endpoint
        get("/health", (request, response) -> {
            response.type("application/json");
            return "{\"status\":\"ok\"}";
        });

        // Add ready check endpoint
        get("/ready", (request, response) -> {
            response.type("application/json");
            return "{\"status\":\"ok\"}";
        });

        // Process graphlettes
        if (config.graphlettes() != null) {
            for (GraphletteConfig graphletteConfig : config.graphlettes()) {
                try {
                    processGraphlette(graphletteConfig, auth);
                    logger.info("Initialized Graphlette at path: {}", graphletteConfig.path());
                } catch (Exception e) {
                    logger.error("Failed to initialize Graphlette at path: {}", graphletteConfig.path(), e);
                }
            }
        }

        // Process restlettes
        if (config.restlettes() != null) {
            for (RestletteConfig restletteConfig : config.restlettes()) {
                try {
                    processRestlette(restletteConfig, auth);
                    logger.info("Initialized Restlette at path: {}", restletteConfig.path());
                } catch (Exception e) {
                    logger.error("Failed to initialize Restlette at path: {}", restletteConfig.path(), e);
                }
            }
        }

        logger.info("Server initialized on port {}", config.port());

        // Wait for initialization
        awaitInitialization();
    }

    private Auth processAuth(Config config) {
        // For now, use NoAuth. In the future, implement Casbin auth if casbinParams is set
        if (config.casbinParams() != null && !config.casbinParams().isEmpty()) {
            logger.warn("Casbin auth parameters found but not yet implemented, using NoAuth");
        }
        return new NoAuth();
    }

    private void processGraphlette(GraphletteConfig config, Auth auth) throws IOException {
        // Read schema file
        String schemaContent = new String(Files.readAllBytes(Paths.get(config.schema())));

        // Create searcher from plugin
        Plugin plugin = plugins.get(config.storage().type);
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin for storage type " + config.storage().type + " not found");
        }

        Searcher searcher = plugin.createSearcher(config.storage());

        // Create DTO factory
        DTOFactory dtoFactory = new DTOFactory(config.rootConfig().resolvers());

        // Create data fetchers
        Map<String, DataFetcher> fetchers = Root.create(searcher, dtoFactory, auth, config.rootConfig());

        // Create and register Graphlette servlet
        Graphlette graphlette = new Graphlette(fetchers, schemaContent);

        // Mount the servlet at the configured path
        post(config.path(), (request, response) -> {
            // Wrap Spark's request/response to be compatible with Jakarta servlet API
            HttpServletRequest servletRequest = toJakartaRequest(request.raw());
            HttpServletResponse servletResponse = toJakartaResponse(response.raw());

            // Use the service method instead of doPost (which is protected)
            graphlette.service(servletRequest, servletResponse);
            return "";
        });
    }

    private void processRestlette(RestletteConfig config, Auth auth) {
        // Create validator - convert JsonSchema to Map
        Map<String, Object> schemaMap = objectMapper.convertValue(
            config.schema().getSchemaNode(),
            Map.class
        );
        Validator validator = new JSONSchemaValidator(schemaMap);

        // Create and register Restlette
        Restlette restlette = new Restlette(config, plugins, auth, validator);

        // Mount the servlet at the configured path
        String basePath = config.path();

        // Handle all HTTP methods by delegating to the servlet
        before(basePath + "/*", (request, response) -> {
            response.type("application/json");
        });

        // Route all requests to the servlet's service method
        get(basePath + "/*", (request, response) -> {
            HttpServletRequest servletRequest = toJakartaRequest(request.raw());
            HttpServletResponse servletResponse = toJakartaResponse(response.raw());
            restlette.service(servletRequest, servletResponse);
            return "";
        });

        post(basePath + "/*", (request, response) -> {
            HttpServletRequest servletRequest = toJakartaRequest(request.raw());
            HttpServletResponse servletResponse = toJakartaResponse(response.raw());
            restlette.service(servletRequest, servletResponse);
            return "";
        });

        put(basePath + "/*", (request, response) -> {
            HttpServletRequest servletRequest = toJakartaRequest(request.raw());
            HttpServletResponse servletResponse = toJakartaResponse(response.raw());
            restlette.service(servletRequest, servletResponse);
            return "";
        });

        delete(basePath + "/*", (request, response) -> {
            HttpServletRequest servletRequest = toJakartaRequest(request.raw());
            HttpServletResponse servletResponse = toJakartaResponse(response.raw());
            restlette.service(servletRequest, servletResponse);
            return "";
        });
    }

    private HttpServletRequest toJakartaRequest(javax.servlet.http.HttpServletRequest javaxRequest) {
        return new ServletRequestAdapter(javaxRequest);
    }

    private HttpServletResponse toJakartaResponse(javax.servlet.http.HttpServletResponse javaxResponse) {
        return new ServletResponseAdapter(javaxResponse);
    }

    public void stop() {
        logger.info("Stopping server");
        Spark.stop();

        // Cleanup plugins
        for (Plugin plugin : plugins.values()) {
            try {
                plugin.cleanUp();
            } catch (Exception e) {
                logger.error("Error cleaning up plugin", e);
            }
        }
    }
}
