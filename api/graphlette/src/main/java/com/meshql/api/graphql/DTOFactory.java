package com.meshql.api.graphql;

import com.meshql.core.Filler;
import com.meshql.core.config.ResolverConfig;
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
    private final Map<String, ResolverFunction> resolvers = new HashMap<>();

    public DTOFactory(List<ResolverConfig> config) {
        if (config != null) {
            for (ResolverConfig c : config) {
                resolvers.put(c.name(), assignResolver(c.id(), c.queryName(), c.url()));
            }
        }
    }

    @Override
    public Stash fillOne(Map<String, Object> data, long timestamp) {
        Stash copy = stash("_timestamp", timestamp);

        // Add resolvers
        resolvers.forEach((key, resolver) -> {
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

    private ResolverFunction assignResolver(String id, String queryName, URI url) {
        logger.debug("Assigning resolver for: {}, {}, {}", id, queryName, url);
        
        SubgraphClient client = new SubgraphClient();

        return (parent, env) -> {
            Object foreignKey = parent.get(id);
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
            return client.callSubgraph(url, query, queryName, authHeader);
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