package com.meshql.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.core.Config;
import com.meshql.core.Plugin;
import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.QueryConfig;
import com.meshql.core.config.RootConfig;
import com.meshql.core.config.SingletonResolverConfig;
import com.meshql.core.config.StorageConfig;
import com.meshql.core.config.VectorResolverConfig;
// Server import removed - using Object to avoid circular dependency
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
            StorageConfig henStorage
    ) {
        this.platformUrl = platformUrl;
        this.port = port;
        this.farmSchemaFile = farmSchemaFile;
        this.coopSchemaFile = coopSchemaFile;
        this.henSchemaFile = henSchemaFile;
        this.farmStorage = farmStorage;
        this.coopStorage = coopStorage;
        this.henStorage = henStorage;

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
        // Farm graphlette
        GraphletteConfig farmGraphlette = new GraphletteConfig(
                "/farm/graph",
                farmStorage,
                farmSchemaFile.toString(),
                new RootConfig(
                        List.of(
                                new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                        ),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        List.of(
                                new VectorResolverConfig(
                                        "coops",
                                        null,
                                        "getByFarm",
                                        new URI(platformUrl + "/coop/graph")
                                )
                        )
                )
        );

        // Coop graphlette
        GraphletteConfig coopGraphlette = new GraphletteConfig(
                "/coop/graph",
                coopStorage,
                coopSchemaFile.toString(),
                new RootConfig(
                        List.of(
                                new QueryConfig("getByName", "{\"payload.name\": \"{{id}}\"}"),
                                new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                        ),
                        List.of(
                                new QueryConfig("getByFarm", "{\"payload.farm_id\": \"{{id}}\"}")
                        ),
                        List.of(
                                new SingletonResolverConfig(
                                        "farm",
                                        "farm_id",
                                        "getById",
                                        new URI(platformUrl + "/farm/graph")
                                )
                        ),
                        List.of(
                                new VectorResolverConfig(
                                        "hens",
                                        null,
                                        "getByCoop",
                                        new URI(platformUrl + "/hen/graph")
                                )
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
                                new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
                        ),
                        List.of(
                                new QueryConfig("getByName", "{\"payload.name\": \"{{name}}\"}"),
                                new QueryConfig("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")
                        ),
                        List.of(
                                new SingletonResolverConfig(
                                        "coop",
                                        "coop_id",
                                        "getById",
                                        new URI(platformUrl + "/coop/graph")
                                )
                        ),
                        Collections.emptyList()
                )
        );

        return new Config(
                Collections.emptyList(),
                List.of(farmGraphlette, coopGraphlette, henGraphlette),
                port,
                Collections.emptyList()
        );
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
