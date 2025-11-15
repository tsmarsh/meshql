package com.meshql.api.graphql;

import com.meshql.core.Filler;
import com.meshql.core.config.ResolverConfig;
import com.meshql.core.config.SingletonResolverConfig;
import com.meshql.core.config.VectorResolverConfig;
import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tailoredshapes.stash.Stash.stash;

public class DTOFactory implements Filler {
    private static final Logger logger = LoggerFactory.getLogger(DTOFactory.class);
    private final Map<String, SingletonResolver> singletonResolvers = new HashMap<>();
    private final Map<String, VectorResolver> vectorResolvers = new HashMap<>();

    public DTOFactory(List<SingletonResolverConfig> singletonConfigs, List<VectorResolverConfig> vectorConfigs) {
        if (singletonConfigs != null) {
            for (SingletonResolverConfig c : singletonConfigs) {
                singletonResolvers.put(c.name(), assignSingletonResolver(c.id(), c.queryName(), c.url()));
            }
        }
        if (vectorConfigs != null) {
            for (VectorResolverConfig c : vectorConfigs) {
                vectorResolvers.put(c.name(), assignVectorResolver(c.id(), c.queryName(), c.url()));
            }
        }
    }

    @Override
    public Stash fillOne(Map<String, Object> data, long timestamp) {
        Stash copy = stash("_timestamp", timestamp);

        // Add singleton resolvers
        singletonResolvers.forEach((key, resolver) -> {
            if (resolver != null) {
                copy.put(key, resolver);
            }
        });

        // Add vector resolvers
        vectorResolvers.forEach((key, resolver) -> {
            if (resolver != null) {
                copy.put(key, resolver);
            }
        });

        // Add data properties
        copy.putAll(data);
        return copy;
    }

    @Override
    public List<Stash> fillMany(List<Stash> data, long timestamp) {
        return data.stream()
                .map(d -> fillOne(d, timestamp))
                .collect(Collectors.toList());
    }

    private SingletonResolver assignSingletonResolver(String id, String queryName, URI url) {
        // Default to "id" if not specified (for relationships based on parent's id field)
        final String foreignKeyField = (id != null) ? id : "id";

        logger.debug("Assigning singleton resolver for: foreignKeyField={}, queryName={}, url={}", foreignKeyField, queryName, url);

        SubgraphClient client = new SubgraphClient();

        return (parent, env) -> {
            Object foreignKey = parent.get(foreignKeyField);
            if (foreignKey == null) {
                return stash();
            }

            String query = SubgraphClient.processContext(
                foreignKey.toString(),
                createContext(env),
                queryName,
                (Long) parent.get("_timestamp")
            );

            String authHeader = extractAuthHeader(env);
            return client.resolveSingleton(url, query, queryName, authHeader);
        };
    }

    private VectorResolver assignVectorResolver(String id, String queryName, URI url) {
        // Default to "id" if not specified (for relationships based on parent's id field)
        final String foreignKeyField = (id != null) ? id : "id";

        logger.debug("Assigning vector resolver for: foreignKeyField={}, queryName={}, url={}", foreignKeyField, queryName, url);

        SubgraphClient client = new SubgraphClient();

        return (parent, env) -> {
            Object foreignKey = parent.get(foreignKeyField);
            if (foreignKey == null) {
                return List.of();
            }

            String query = SubgraphClient.processContext(
                foreignKey.toString(),
                createContext(env),
                queryName,
                (Long) parent.get("_timestamp")
            );

            String authHeader = extractAuthHeader(env);
            return client.resolveVector(url, query, queryName, authHeader);
        };
    }

    private Map<String, Object> createContext(DataFetchingEnvironment env) {
        Map<String, Object> context = new HashMap<>();
        context.put("fieldNodes", env.getFields());
        context.put("schema", env.getGraphQLSchema());
        return context;
    }

    private String extractAuthHeader(DataFetchingEnvironment env) {
        Map<String, Object> headers = env.getGraphQlContext().get("headers");
        if (headers != null && headers.containsKey("authorization")) {
            return headers.get("authorization").toString();
        }
        return null;
    }
}