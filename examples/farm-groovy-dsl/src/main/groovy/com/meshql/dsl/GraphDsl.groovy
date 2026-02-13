package com.meshql.dsl

import com.meshql.core.config.InternalSingletonResolverConfig
import com.meshql.core.config.InternalVectorResolverConfig
import com.meshql.core.config.QueryConfig
import com.meshql.core.config.RootConfig
import com.meshql.core.config.SingletonResolverConfig
import com.meshql.core.config.VectorResolverConfig

/**
 * DSL delegate for the graph{} closure inside an entity.
 * <p>
 * Queries and resolvers are defined via method calls:
 * <pre>
 * query   "getById",   id: "id"
 * query   "getByCoop", "payload.coop_id": "id"
 *
 * resolve "coop",       fk: "coop_id", via: "getById",  at: "/coop/graph"
 * resolve "layReports",                 via: "getByHen", at: "/lay_report/graph"
 * </pre>
 */
class GraphDsl {
    private final String platformUrl

    final List<QueryConfig> singletons = []
    final List<QueryConfig> vectors = []
    final List<SingletonResolverConfig> singletonResolvers = []
    final List<VectorResolverConfig> vectorResolvers = []
    final List<InternalSingletonResolverConfig> internalSingletonResolvers = []
    final List<InternalVectorResolverConfig> internalVectorResolvers = []

    GraphDsl(String platformUrl) {
        this.platformUrl = platformUrl
    }

    /**
     * Define a query. Auto-detects singleton (key is "id") vs vector.
     * <p>
     * {@code query "getById", id: "id"}
     * {@code query "getByCoop", "payload.coop_id": "id"}
     */
    void query(Map<String, String> fieldMap, String name) {
        String queryJson = buildQueryJson(fieldMap)
        if (isSingletonQuery(fieldMap)) {
            singletons << new QueryConfig(name, queryJson)
        } else {
            vectors << new QueryConfig(name, queryJson)
        }
    }

    /**
     * Define a singleton query (returns one result).
     * <p>{@code singleton "getByName", "payload.name": "id"}
     */
    void singleton(Map<String, String> fieldMap, String name) {
        singletons << new QueryConfig(name, buildQueryJson(fieldMap))
    }

    /**
     * Define a vector query (returns many results).
     * <p>{@code vector "getByName", "payload.name": "name"}
     */
    void vector(Map<String, String> fieldMap, String name) {
        vectors << new QueryConfig(name, buildQueryJson(fieldMap))
    }

    /**
     * Define a resolver (relationship to another entity).
     * <p>
     * Singleton resolver (has fk):
     * {@code resolve "coop", fk: "coop_id", via: "getById", at: "/coop/graph"}
     * <p>
     * Vector resolver (no fk):
     * {@code resolve "layReports", via: "getByHen", at: "/lay_report/graph"}
     * <p>
     * Internal resolver (same JVM):
     * {@code resolve "coop", fk: "coop_id", via: "getById", at: "/coop/graph", internal: true}
     */
    void resolve(Map<String, Object> params, String name) {
        String fk = params.get("fk")
        String queryName = params.get("via")
        String at = params.get("at")
        boolean internal = params.containsKey("internal") && params.get("internal")

        if (!queryName) {
            throw new IllegalArgumentException("Resolver '${name}' requires 'via' parameter")
        }
        if (!at) {
            throw new IllegalArgumentException("Resolver '${name}' requires 'at' parameter")
        }

        if (fk) {
            if (internal) {
                internalSingletonResolvers << new InternalSingletonResolverConfig(name, fk, queryName, at)
            } else {
                singletonResolvers << new SingletonResolverConfig(
                        name, fk, queryName, URI.create(platformUrl + at))
            }
        } else {
            if (internal) {
                internalVectorResolvers << new InternalVectorResolverConfig(name, null, queryName, at)
            } else {
                vectorResolvers << new VectorResolverConfig(
                        name, null, queryName, URI.create(platformUrl + at))
            }
        }
    }

    private boolean isSingletonQuery(Map<String, String> fieldMap) {
        fieldMap.size() == 1 && fieldMap.containsKey("id")
    }

    private String buildQueryJson(Map<String, String> fieldMap) {
        def entries = fieldMap.collect { key, value ->
            "\"${key}\": \"{{${value}}}\""
        }
        "{${entries.join(', ')}}"
    }

    RootConfig toRootConfig() {
        new RootConfig(
                singletons,
                vectors,
                singletonResolvers,
                vectorResolvers,
                internalSingletonResolvers,
                internalVectorResolvers
        )
    }
}
