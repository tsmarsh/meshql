package com.meshql.examples.farm;

import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Config;
import com.meshql.core.Plugin;
import com.meshql.core.config.*;
import com.meshql.repositories.mongo.MongoConfig;
import com.meshql.repositories.mongo.MongoPlugin;
import com.meshql.server.Server;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting Farm Example Application");

        // Read environment variables with defaults
        String mongoUri = getEnv("MONGO_URI", "mongodb://localhost:27017");
        String prefix = getEnv("PREFIX", "farm");
        String env = getEnv("ENV", "development");
        int port = Integer.parseInt(getEnv("PORT", "3033"));
        String platformUrl = getEnv("PLATFORM_URL", "http://localhost:" + port);

        logger.info("Configuration: mongoUri={}, prefix={}, env={}, port={}", mongoUri, prefix, env, port);

        // Create storage configs for each collection
        MongoConfig farmDB = createMongoConfig(mongoUri, prefix, env, "farm");
        MongoConfig coopDB = createMongoConfig(mongoUri, prefix, env, "coop");
        MongoConfig henDB = createMongoConfig(mongoUri, prefix, env, "hen");
        MongoConfig layReportDB = createMongoConfig(mongoUri, prefix, env, "lay_report");

        // Create graphlettes
        List<GraphletteConfig> graphlettes = new ArrayList<>();

        // Farm graphlette
        graphlettes.add(new GraphletteConfig(
                "/farm/graph",
                farmDB,
                "/app/config/graph/farm.graphql",
                new RootConfig(
                        List.of(new QueryConfig("getById", "{\"id\": \"{{id}}\"}"))  ,
                        List.of(),
                        List.of(),
                        List.of(new VectorResolverConfig("coops", null, "getByFarm",
                                URI.create(platformUrl + "/coop/graph"))),
                        List.of(),
                        List.of()
                )
        ));

        // Coop graphlette
        graphlettes.add(new GraphletteConfig(
                "/coop/graph",
                coopDB,
                "/app/config/graph/coop.graphql",
                new RootConfig(
                        List.of(
                                new QueryConfig("getByName", "{\"payload.name\": \"{{id}}\"}"),
                                new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                        ),
                        List.of(new QueryConfig("getByFarm", "{\"payload.farm_id\": \"{{id}}\"}")) ,
                        List.of(new SingletonResolverConfig("farm", "farm_id", "getById",
                                URI.create(platformUrl + "/farm/graph"))),
                        List.of(
                                new VectorResolverConfig("hens", null, "getByCoop",
                                        URI.create(platformUrl + "/hen/graph")),
                                new VectorResolverConfig("hens.layReports", null, "getByHen",
                                        URI.create(platformUrl + "/lay_report/graph"))
                        ),
                        List.of(),
                        List.of()
                )
        ));

        // Hen graphlette
        graphlettes.add(new GraphletteConfig(
                "/hen/graph",
                henDB,
                "/app/config/graph/hen.graphql",
                new RootConfig(
                        List.of(new QueryConfig("getById", "{\"id\": \"{{id}}\"}")) ,
                        List.of(
                                new QueryConfig("getByName", "{\"payload.name\": \"{{name}}\"}"),
                                new QueryConfig("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")
                        ),
                        List.of(new SingletonResolverConfig("coop", "coop_id", "getById",
                                URI.create(platformUrl + "/coop/graph"))),
                        List.of(new VectorResolverConfig("layReports", null, "getByHen",
                                URI.create(platformUrl + "/lay_report/graph"))),
                        List.of(),
                        List.of()
                )
        ));

        // Lay Report graphlette
        graphlettes.add(new GraphletteConfig(
                "/lay_report/graph",
                layReportDB,
                "/app/config/graph/lay_report.graphql",
                new RootConfig(
                        List.of(new QueryConfig("getById", "{\"id\": \"{{id}}\"}")) ,
                        List.of(new QueryConfig("getByHen", "{\"payload.hen_id\": \"{{id}}\"}")) ,
                        List.of(new SingletonResolverConfig("hen", "hen_id", "getById",
                                URI.create(platformUrl + "/hen/graph"))),
                        List.of(),
                        List.of(),
                        List.of()
                )
        ));

        // Create restlettes
        List<RestletteConfig> restlettes = new ArrayList<>();

        restlettes.add(new RestletteConfig(
                List.of(),  // no tokens for now
                "/farm/api",
                port,
                farmDB,
                loadJsonSchema("/app/config/json/farm.schema.json")
        ));

        restlettes.add(new RestletteConfig(
                List.of(),
                "/coop/api",
                port,
                coopDB,
                loadJsonSchema("/app/config/json/coop.schema.json")
        ));

        restlettes.add(new RestletteConfig(
                List.of(),
                "/hen/api",
                port,
                henDB,
                loadJsonSchema("/app/config/json/hen.schema.json")
        ));

        restlettes.add(new RestletteConfig(
                List.of(),
                "/lay_report/api",
                port,
                layReportDB,
                loadJsonSchema("/app/config/json/lay_report.schema.json")
        ));

        // Create config
        Config config = new Config(
                null,  // casbinParams
                graphlettes,
                port,
                restlettes
        );

        // Create authentication
        Auth auth = new NoAuth();

        // Register plugins
        Map<String, Plugin> plugins = new HashMap<>();
        plugins.put("mongo", new MongoPlugin(auth));

        logger.info("Initializing server on port {}", port);

        // Create and initialize the server
        Server server = new Server(plugins);
        server.init(config);

        logger.info("Server started successfully on port {}", port);
        logger.info("Health check: http://localhost:{}/health", port);
        logger.info("GraphQL endpoints:");
        logger.info("  - http://localhost:{}/farm/graph", port);
        logger.info("  - http://localhost:{}/coop/graph", port);
        logger.info("  - http://localhost:{}/hen/graph", port);
        logger.info("  - http://localhost:{}/lay_report/graph", port);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            try {
                server.stop();
                logger.info("Server stopped successfully");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Keep the main thread alive
        Thread.currentThread().join();
    }

    private static MongoConfig createMongoConfig(String uri, String prefix, String env, String entity) {
        MongoConfig config = new MongoConfig();
        config.uri = uri;
        config.db = prefix + "_" + env;
        config.collection = prefix + "-" + env + "-" + entity;
        return config;
    }

    private static JsonSchema loadJsonSchema(String path) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.readTree(new File(path));
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        return factory.getSchema(schemaNode);
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
