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
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Plugin> plugins;
    private org.eclipse.jetty.server.Server jettyServer;

    public Server(Map<String, Plugin> plugins) {
        this.plugins = plugins;
    }

    public void init(Config config) throws Exception {
        // Create Jetty server
        jettyServer = new org.eclipse.jetty.server.Server(config.port());

        // Create servlet context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // Add CORS filter
        context.addFilter(CORSFilter.class, "/*", null);

        // Process authentication
        Auth auth = processAuth(config);

        // Add health check endpoint
        context.addServlet(new ServletHolder(new HealthCheckServlet()), "/health");
        context.addServlet(new ServletHolder(new HealthCheckServlet()), "/ready");

        // Process graphlettes
        if (config.graphlettes() != null) {
            for (GraphletteConfig graphletteConfig : config.graphlettes()) {
                try {
                    processGraphlette(context, graphletteConfig, auth);
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
                    processRestlette(context, restletteConfig, auth);
                    logger.info("Initialized Restlette at path: {}", restletteConfig.path());
                } catch (Exception e) {
                    logger.error("Failed to initialize Restlette at path: {}", restletteConfig.path(), e);
                }
            }
        }

        // Start the server
        jettyServer.start();
        logger.info("Server initialized on port {}", config.port());
    }

    private Auth processAuth(Config config) {
        // For now, use NoAuth. In the future, implement Casbin auth if casbinParams is set
        if (config.casbinParams() != null && !config.casbinParams().isEmpty()) {
            logger.warn("Casbin auth parameters found but not yet implemented, using NoAuth");
        }
        return new NoAuth();
    }

    private void processGraphlette(ServletContextHandler context, GraphletteConfig config, Auth auth) throws IOException {
        // Read schema file
        String schemaContent = new String(Files.readAllBytes(Paths.get(config.schema())));

        // Create searcher from plugin
        Plugin plugin = plugins.get(config.storage().type);
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin for storage type " + config.storage().type + " not found");
        }

        Searcher searcher = plugin.createSearcher(config.storage());

        // Create DTO factory
        DTOFactory dtoFactory = new DTOFactory(
            config.rootConfig().singletonResolvers(),
            config.rootConfig().vectorResolvers()
        );

        // Create data fetchers
        Map<String, DataFetcher> fetchers = Root.create(searcher, dtoFactory, auth, config.rootConfig());

        // Create and register Graphlette servlet
        Graphlette graphlette = new Graphlette(fetchers, schemaContent);
        context.addServlet(new ServletHolder(graphlette), config.path());
    }

    private void processRestlette(ServletContextHandler context, RestletteConfig config, Auth auth) {
        // Create validator - convert JsonSchema to Map
        Map<String, Object> schemaMap = objectMapper.convertValue(
            config.schema().getSchemaNode(),
            Map.class
        );
        Validator validator = new JSONSchemaValidator(schemaMap);

        // Create and register Restlette
        Restlette restlette = new Restlette(config, plugins, auth, validator);

        // Mount the servlet at the configured path with wildcard
        context.addServlet(new ServletHolder(restlette), config.path() + "/*");
    }

    public void stop() throws Exception {
        logger.info("Stopping server");

        if (jettyServer != null) {
            jettyServer.stop();
            jettyServer.join();
        }

        // Cleanup plugins
        for (Plugin plugin : plugins.values()) {
            try {
                plugin.cleanUp();
            } catch (Exception e) {
                logger.error("Error cleaning up plugin", e);
            }
        }
    }

    /**
     * CORS filter to allow cross-origin requests
     */
    public static class CORSFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader("Access-Control-Allow-Origin", "*");
            httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
                httpResponse.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }

    /**
     * Simple health check servlet
     */
    public static class HealthCheckServlet extends jakarta.servlet.http.HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"ok\"}");
        }
    }
}
