package com.meshql.repositories.mongo;

import com.meshql.auth.noop.NoAuth;
import com.meshql.cert.FarmHooks;
import com.meshql.cert.FarmWorld;
import com.meshql.core.Config;
import com.meshql.core.Plugin;
import com.meshql.core.config.*;
import com.meshql.server.Server;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.tailoredshapes.underbar.ocho.Die.rethrow;

/**
 * Cucumber hooks for MongoDB farm certification tests.
 */
public class MongoFarmHooks extends FarmHooks {
    private static final Logger logger = LoggerFactory.getLogger(MongoFarmHooks.class);
    private static final String DATABASE_NAME = "farm_test_db";

    private MongoServer mongoServer;
    private MongoClient mongoClient;
    private String farmCollection;
    private String coopCollection;
    private String henCollection;
    private Path farmSchemaFile;
    private Path coopSchemaFile;
    private Path henSchemaFile;

    public MongoFarmHooks(FarmWorld world) {
        super(world);
    }

    @Override
    protected Plugin createPlugin() {
        // Start in-memory MongoDB server
        mongoServer = new MongoServer(new MemoryBackend());
        String connectionString = mongoServer.bindAndGetConnectionString();

        // Create MongoDB client
        mongoClient = MongoClients.create(connectionString);

        // Create unique collection names for this test run
        String testId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        farmCollection = "farm_" + testId;
        coopCollection = "coop_" + testId;
        henCollection = "hen_" + testId;

        logger.info("Started in-memory MongoDB at {} with collections: {}, {}, {}",
            connectionString, farmCollection, coopCollection, henCollection);

        return new MongoPlugin(new NoAuth());
    }

    @Override
    protected String getStorageType() {
        return "mongo";
    }

    @Override
    protected StorageConfig createFarmStorageConfig() {
        MongoConfig config = new MongoConfig();
        config.uri = mongoServer.getConnectionString();
        config.db = DATABASE_NAME;
        config.collection = farmCollection;
        return config;
    }

    @Override
    protected StorageConfig createCoopStorageConfig() {
        MongoConfig config = new MongoConfig();
        config.uri = mongoServer.getConnectionString();
        config.db = DATABASE_NAME;
        config.collection = coopCollection;
        return config;
    }

    @Override
    protected StorageConfig createHenStorageConfig() {
        MongoConfig config = new MongoConfig();
        config.uri = mongoServer.getConnectionString();
        config.db = DATABASE_NAME;
        config.collection = henCollection;
        return config;
    }

    @Override
    protected void startServer() throws Exception {
        // Write schemas to temp files
        farmSchemaFile = Files.createTempFile("farm_schema", ".graphql");
        coopSchemaFile = Files.createTempFile("coop_schema", ".graphql");
        henSchemaFile = Files.createTempFile("hen_schema", ".graphql");

        Files.writeString(farmSchemaFile, FARM_SCHEMA);
        Files.writeString(coopSchemaFile, COOP_SCHEMA);
        Files.writeString(henSchemaFile, HEN_SCHEMA);

        Config config = buildFarmConfig();
        Server server = new Server(world.plugins);
        server.init(config);
        world.server = server;

        // Give server time to start
        Thread.sleep(500);
    }

    @Override
    protected void stopServer() {
        if (world.server != null && world.server instanceof Server) {
            rethrow(() -> ((Server) world.server).stop());
        }
    }

    private Config buildFarmConfig() {
        // Create MongoDB-specific storage configs
        MongoConfig farmStorage = new MongoConfig();
        farmStorage.uri = mongoServer.getConnectionString();
        farmStorage.db = DATABASE_NAME;
        farmStorage.collection = farmCollection;

        MongoConfig coopStorage = new MongoConfig();
        coopStorage.uri = mongoServer.getConnectionString();
        coopStorage.db = DATABASE_NAME;
        coopStorage.collection = coopCollection;

        MongoConfig henStorage = new MongoConfig();
        henStorage.uri = mongoServer.getConnectionString();
        henStorage.db = DATABASE_NAME;
        henStorage.collection = henCollection;

        // Farm graphlette
        GraphletteConfig farmGraphlette = new GraphletteConfig(
            "/farm/graph",
            farmStorage,
            farmSchemaFile.toString(),
            new RootConfig(
                List.of(
                    new ResolverConfig(
                        "coops",
                        null,
                        "getByFarm",
                        URI.create("http://localhost:" + world.port + "/coop/graph")
                    )
                ),
                List.of(
                    new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                ),
                Collections.emptyList()
            )
        );

        // Coop graphlette
        GraphletteConfig coopGraphlette = new GraphletteConfig(
            "/coop/graph",
            coopStorage,
            coopSchemaFile.toString(),
            new RootConfig(
                List.of(
                    new ResolverConfig(
                        "farm",
                        "farm_id",
                        "getById",
                        URI.create("http://localhost:" + world.port + "/farm/graph")
                    ),
                    new ResolverConfig(
                        "hens",
                        null,
                        "getByCoop",
                        URI.create("http://localhost:" + world.port + "/hen/graph")
                    )
                ),
                List.of(
                    new QueryConfig("getByName", "{\"payload.name\": \"{{name}}\"}"),
                    new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                ),
                List.of(
                    new QueryConfig("getByFarm", "{\"payload.farm_id\": \"{{id}}\"}")
                )
            )
        );

        // Hen graphlette
        GraphletteConfig henGraphlette = new GraphletteConfig(
            "/hen/graph",
            henStorage,
            henSchemaFile.toString(),
            new RootConfig(
                List.of(
                    new ResolverConfig(
                        "coop",
                        "coop_id",
                        "getById",
                        URI.create("http://localhost:" + world.port + "/coop/graph")
                    )
                ),
                List.of(
                    new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                ),
                List.of(
                    new QueryConfig("getByName", "{\"payload.name\": \"{{name}}\"}"),
                    new QueryConfig("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")
                )
            )
        );

        return new Config(
            Collections.emptyList(),
            List.of(farmGraphlette, coopGraphlette, henGraphlette),
            world.port,
            Collections.emptyList()
        );
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();

        // Clean up collections
        if (mongoClient != null) {
            try {
                MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
                database.getCollection(farmCollection).drop();
                database.getCollection(coopCollection).drop();
                database.getCollection(henCollection).drop();
            } catch (Exception e) {
                logger.warn("Failed to clean up MongoDB collections", e);
            }
            mongoClient.close();
        }

        // Shutdown MongoDB server
        if (mongoServer != null) {
            mongoServer.shutdown();
        }

        // Clean up temp schema files
        try {
            if (farmSchemaFile != null) Files.deleteIfExists(farmSchemaFile);
            if (coopSchemaFile != null) Files.deleteIfExists(coopSchemaFile);
            if (henSchemaFile != null) Files.deleteIfExists(henSchemaFile);
        } catch (IOException e) {
            logger.warn("Failed to clean up temp schema files", e);
        }
    }
}
