package com.meshql.dsl

import com.meshql.core.Config
import com.meshql.core.config.GraphletteConfig
import com.meshql.core.config.RestletteConfig

/**
 * Top-level DSL delegate for a meshql{} closure.
 * <p>
 * Usage:
 * <pre>
 * meshql {
 *     port 3033
 *     mongo "mongodb://localhost:27017", "farm_development"
 *
 *     entity("farm") {
 *         collection "farm-development-farm"
 *         graph "/farm/graph", "config/graph/farm.graphql", { ... }
 *         rest "/farm/api", "config/json/farm.schema.json"
 *     }
 * }
 * </pre>
 */
class MeshqlDsl {
    private int serverPort = 3033
    private String mongoUri
    private String mongoDb
    private String platformUrl

    private final List<EntityDsl> entities = []

    void port(int port) {
        this.serverPort = port
    }

    void mongo(String uri, String db) {
        this.mongoUri = uri
        this.mongoDb = db
    }

    void platform(String url) {
        this.platformUrl = url
    }

    void entity(String name, @DelegatesTo(EntityDsl) Closure cl) {
        String url = platformUrl ?: "http://localhost:${serverPort}"
        EntityDsl entityDsl = new EntityDsl(name, mongoUri, mongoDb, url, serverPort)
        cl.delegate = entityDsl
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.call()
        entities << entityDsl
    }

    Config toConfig() {
        List<GraphletteConfig> graphlettes = entities
                .collect { it.graphletteConfig }
                .findAll { it != null }

        List<RestletteConfig> restlettes = entities
                .collect { it.restletteConfig }
                .findAll { it != null }

        new Config(
                null,           // casbinParams
                graphlettes,
                serverPort,
                restlettes,
                "*",            // corsOrigin
                10_000,         // connectTimeoutMs
                30_000          // requestTimeoutMs
        )
    }
}
