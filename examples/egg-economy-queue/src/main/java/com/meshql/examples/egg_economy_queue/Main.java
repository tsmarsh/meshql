package com.meshql.examples.egg_economy_queue;

import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Config;
import com.meshql.core.Plugin;
import com.meshql.core.config.*;
import com.meshql.repositories.merksql.MerkSqlConfig;
import com.meshql.repositories.merksql.MerkSqlPlugin;
import com.meshql.server.Server;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static String configDir;

    public static void main(String[] args) throws Exception {
        logger.info("Starting Egg Economy Queue Application (MerkSQL)");

        String dataDir = getEnv("DATA_DIR", "./data");
        configDir = getEnv("CONFIG_DIR", "/app/config");
        int port = Integer.parseInt(getEnv("PORT", "5088"));

        logger.info("Configuration: dataDir={}, configDir={}, port={}", dataDir, configDir, port);

        // --- Actor storage configs ---
        MerkSqlConfig farmDB = createConfig(dataDir, "farm");
        MerkSqlConfig coopDB = createConfig(dataDir, "coop");
        MerkSqlConfig henDB = createConfig(dataDir, "hen");
        MerkSqlConfig containerDB = createConfig(dataDir, "container");
        MerkSqlConfig consumerDB = createConfig(dataDir, "consumer");

        // --- Event storage configs ---
        MerkSqlConfig layReportDB = createConfig(dataDir, "lay_report");
        MerkSqlConfig storageDepositDB = createConfig(dataDir, "storage_deposit");
        MerkSqlConfig storageWithdrawalDB = createConfig(dataDir, "storage_withdrawal");
        MerkSqlConfig containerTransferDB = createConfig(dataDir, "container_transfer");
        MerkSqlConfig consumptionReportDB = createConfig(dataDir, "consumption_report");

        // --- Projection storage configs ---
        MerkSqlConfig containerInventoryDB = createConfig(dataDir, "container_inventory");
        MerkSqlConfig henProductivityDB = createConfig(dataDir, "hen_productivity");
        MerkSqlConfig farmOutputDB = createConfig(dataDir, "farm_output");

        Config config = Config.builder()
                .port(port)

                // ===== ACTOR GRAPHLETTES (5) =====

                .graphlette(GraphletteConfig.builder()
                        .path("/farm/graph")
                        .storage(farmDB)
                        .schema(graphSchema("farm"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getAll", "")
                                .vector("getByZone", "zone = '{{zone}}'")
                                .internalVectorResolver("coops", null, "getByFarm", "/coop/graph")
                                .internalVectorResolver("farmOutput", null, "getByFarm", "/farm_output/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/coop/graph")
                        .storage(coopDB)
                        .schema(graphSchema("coop"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getAll", "")
                                .vector("getByFarm", "farm_id = '{{id}}'")
                                .internalSingletonResolver("farm", "farm_id", "getById", "/farm/graph")
                                .internalVectorResolver("hens", null, "getByCoop", "/hen/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/hen/graph")
                        .storage(henDB)
                        .schema(graphSchema("hen"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByCoop", "coop_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("coop", "coop_id", "getById", "/coop/graph")
                                .internalVectorResolver("layReports", null, "getByHen", "/lay_report/graph")
                                .internalVectorResolver("productivity", null, "getByHen", "/hen_productivity/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/container/graph")
                        .storage(containerDB)
                        .schema(graphSchema("container"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getAll", "")
                                .vector("getByZone", "zone = '{{zone}}'")
                                .internalVectorResolver("inventory", null, "getByContainer", "/container_inventory/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/consumer/graph")
                        .storage(consumerDB)
                        .schema(graphSchema("consumer"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getAll", "")
                                .vector("getByZone", "zone = '{{zone}}'")
                                .internalVectorResolver("consumptionReports", null, "getByConsumer", "/consumption_report/graph")))

                // ===== EVENT GRAPHLETTES (5) =====

                .graphlette(GraphletteConfig.builder()
                        .path("/lay_report/graph")
                        .storage(layReportDB)
                        .schema(graphSchema("lay_report"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByHen", "hen_id = '{{id}}'")
                                .vector("getByFarm", "farm_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("hen", "hen_id", "getById", "/hen/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/storage_deposit/graph")
                        .storage(storageDepositDB)
                        .schema(graphSchema("storage_deposit"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByContainer", "container_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("container", "container_id", "getById", "/container/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/storage_withdrawal/graph")
                        .storage(storageWithdrawalDB)
                        .schema(graphSchema("storage_withdrawal"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByContainer", "container_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("container", "container_id", "getById", "/container/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/container_transfer/graph")
                        .storage(containerTransferDB)
                        .schema(graphSchema("container_transfer"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getBySourceContainer", "source_container_id = '{{id}}'")
                                .vector("getByDestContainer", "dest_container_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("sourceContainer", "source_container_id", "getById", "/container/graph")
                                .internalSingletonResolver("destContainer", "dest_container_id", "getById", "/container/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/consumption_report/graph")
                        .storage(consumptionReportDB)
                        .schema(graphSchema("consumption_report"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByConsumer", "consumer_id = '{{id}}'")
                                .vector("getByContainer", "container_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("consumer", "consumer_id", "getById", "/consumer/graph")
                                .internalSingletonResolver("container", "container_id", "getById", "/container/graph")))

                // ===== PROJECTION GRAPHLETTES (3) =====

                .graphlette(GraphletteConfig.builder()
                        .path("/container_inventory/graph")
                        .storage(containerInventoryDB)
                        .schema(graphSchema("container_inventory"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByContainer", "container_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("container", "container_id", "getById", "/container/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/hen_productivity/graph")
                        .storage(henProductivityDB)
                        .schema(graphSchema("hen_productivity"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByHen", "hen_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("hen", "hen_id", "getById", "/hen/graph")))

                .graphlette(GraphletteConfig.builder()
                        .path("/farm_output/graph")
                        .storage(farmOutputDB)
                        .schema(graphSchema("farm_output"))
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "id = '{{id}}'")
                                .vector("getByFarm", "farm_id = '{{id}}'")
                                .vector("getAll", "")
                                .internalSingletonResolver("farm", "farm_id", "getById", "/farm/graph")))

                // ===== RESTLETTES (13) =====

                .restlette(RestletteConfig.builder()
                        .path("/farm/api").port(port).storage(farmDB)
                        .schema(loadJsonSchema("farm")))
                .restlette(RestletteConfig.builder()
                        .path("/coop/api").port(port).storage(coopDB)
                        .schema(loadJsonSchema("coop")))
                .restlette(RestletteConfig.builder()
                        .path("/hen/api").port(port).storage(henDB)
                        .schema(loadJsonSchema("hen")))
                .restlette(RestletteConfig.builder()
                        .path("/container/api").port(port).storage(containerDB)
                        .schema(loadJsonSchema("container")))
                .restlette(RestletteConfig.builder()
                        .path("/consumer/api").port(port).storage(consumerDB)
                        .schema(loadJsonSchema("consumer")))
                .restlette(RestletteConfig.builder()
                        .path("/lay_report/api").port(port).storage(layReportDB)
                        .schema(loadJsonSchema("lay_report")))
                .restlette(RestletteConfig.builder()
                        .path("/storage_deposit/api").port(port).storage(storageDepositDB)
                        .schema(loadJsonSchema("storage_deposit")))
                .restlette(RestletteConfig.builder()
                        .path("/storage_withdrawal/api").port(port).storage(storageWithdrawalDB)
                        .schema(loadJsonSchema("storage_withdrawal")))
                .restlette(RestletteConfig.builder()
                        .path("/container_transfer/api").port(port).storage(containerTransferDB)
                        .schema(loadJsonSchema("container_transfer")))
                .restlette(RestletteConfig.builder()
                        .path("/consumption_report/api").port(port).storage(consumptionReportDB)
                        .schema(loadJsonSchema("consumption_report")))
                .restlette(RestletteConfig.builder()
                        .path("/container_inventory/api").port(port).storage(containerInventoryDB)
                        .schema(loadJsonSchema("container_inventory")))
                .restlette(RestletteConfig.builder()
                        .path("/hen_productivity/api").port(port).storage(henProductivityDB)
                        .schema(loadJsonSchema("hen_productivity")))
                .restlette(RestletteConfig.builder()
                        .path("/farm_output/api").port(port).storage(farmOutputDB)
                        .schema(loadJsonSchema("farm_output")))

                .build();

        Auth auth = new NoAuth();
        Map<String, Plugin> plugins = new HashMap<>();
        plugins.put("merksql", new MerkSqlPlugin());

        logger.info("Initializing server on port {}", port);

        Server server = new Server(plugins);
        server.init(config);

        logger.info("Server started successfully on port {}", port);
        logger.info("Health check: http://localhost:{}/health", port);
        logger.info("No external infrastructure required - all data stored in {}", dataDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            try {
                server.stop();
                logger.info("Server stopped successfully");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        Thread.currentThread().join();
    }

    private static MerkSqlConfig createConfig(String dataDir, String entity) {
        MerkSqlConfig config = new MerkSqlConfig();
        config.dataDir = dataDir;
        config.topic = entity;
        return config;
    }

    private static String graphSchema(String entity) {
        return configDir + "/graph/" + entity + ".graphql";
    }

    private static JsonSchema loadJsonSchema(String entity) throws Exception {
        String path = configDir + "/json/" + entity + ".schema.json";
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
