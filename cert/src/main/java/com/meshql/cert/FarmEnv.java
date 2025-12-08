package com.meshql.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.core.Config;
import com.meshql.core.Plugin;
import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.RootConfig;
import com.meshql.core.config.StorageConfig;
// Server import removed - using Object to avoid circular dependency
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Farm test environment - holds persistent state across all scenarios.
 * This matches the TypeScript FarmEnv class pattern.
 */
public class FarmEnv {
    private static final Logger logger = LoggerFactory.getLogger(FarmEnv.class);

    private final String platformUrl;
    private final int port;
    private final Path farmSchemaFile;
    private final Path coopSchemaFile;
    private final Path henSchemaFile;
    private final StorageConfig farmStorage;
    private final StorageConfig coopStorage;
    private final StorageConfig henStorage;
    private final FarmQueries queries;

    // Runtime state - persistent across all scenarios (like TypeScript's FarmEnv)
    public final Map<String, Map<String, String>> ids = new HashMap<>();
    public Long firstStamp;
    public String token;

    public FarmEnv(
            String platformUrl,
            int port,
            Path farmSchemaFile,
            Path coopSchemaFile,
            Path henSchemaFile,
            StorageConfig farmStorage,
            StorageConfig coopStorage,
            StorageConfig henStorage,
            FarmQueries queries
    ) {
        this.platformUrl = platformUrl;
        this.port = port;
        this.farmSchemaFile = farmSchemaFile;
        this.coopSchemaFile = coopSchemaFile;
        this.henSchemaFile = henSchemaFile;
        this.farmStorage = farmStorage;
        this.coopStorage = coopStorage;
        this.henStorage = henStorage;
        this.queries = queries;

        // Initialize ID maps for each entity type
        ids.put("farm", new HashMap<>());
        ids.put("coop", new HashMap<>());
        ids.put("hen", new HashMap<>());
    }

    /**
     * Build the server configuration.
     * Matches TypeScript FarmEnv.config() method.
     */
    public Config buildConfig() throws Exception {
        return Config.builder()
                .port(port)
                // Farm graphlette
                .graphlette(GraphletteConfig.builder()
                        .path("/farm/graph")
                        .storage(farmStorage)
                        .schema(farmSchemaFile.toString())
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", queries.farmById)
                                .vectorResolver("coops", null, "getByFarm", platformUrl + "/coop/graph")))
                // Coop graphlette
                .graphlette(GraphletteConfig.builder()
                        .path("/coop/graph")
                        .storage(coopStorage)
                        .schema(coopSchemaFile.toString())
                        .rootConfig(RootConfig.builder()
                                .singleton("getByName", queries.coopByName)
                                .singleton("getById", queries.coopById)
                                .vector("getByFarm", queries.coopByFarmId)
                                .singletonResolver("farm", "farm_id", "getById", platformUrl + "/farm/graph")
                                .vectorResolver("hens", null, "getByCoop", platformUrl + "/hen/graph")))
                // Hen graphlette
                .graphlette(GraphletteConfig.builder()
                        .path("/hen/graph")
                        .storage(henStorage)
                        .schema(henSchemaFile.toString())
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", queries.henById)
                                .vector("getByName", queries.henByName)
                                .vector("getByCoop", queries.henByCoopId)
                                .singletonResolver("coop", "coop_id", "getById", platformUrl + "/coop/graph")))
                .build();
    }

    /**
     * Initialize and start the server.
     * Matches TypeScript FarmEnv.buildService() method.
     * Returns Object to avoid circular dependency on server module.
     */
    public Object buildService(Map<String, Plugin> plugins) throws Exception {
        Config config = buildConfig();
        // Use reflection to avoid compile-time dependency on Server class
        Class<?> serverClass = Class.forName("com.meshql.server.Server");
        Object server = serverClass.getConstructor(Map.class).newInstance(plugins);
        serverClass.getMethod("init", Config.class).invoke(server, config);
        // Give server time to start
        Thread.sleep(500);
        logger.info("Farm test server started on port {}", port);
        return server;
    }

    public int getPort() {
        return port;
    }

    public String getPlatformUrl() {
        return platformUrl;
    }
}
