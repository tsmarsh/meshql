package com.meshql.api.graphql;

import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.Handlebars;
import com.meshql.core.config.QueryConfig;
import com.meshql.core.config.RootConfig;
import com.meshql.core.Auth;
import com.meshql.core.Searcher;
import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tailoredshapes.underbar.ocho.Die.die;
import static com.tailoredshapes.underbar.ocho.Die.rethrow;

public class Root {
    private static final Logger logger = LoggerFactory.getLogger(Root.class);
    Handlebars handlebars;

    public Root() {
        this.handlebars = new Handlebars();
    }

    public static Map<String, DataFetcher> create(
            Searcher searcher,
            DTOFactory dtoFactory,
            Auth authorizer,
            RootConfig config
    ) {
        Map<String, DataFetcher> base = new HashMap<>();

        if (config.singletons() != null) {
            for (QueryConfig singleton : config.singletons()) {
                base.put(
                    singleton.name(),
                    createSingleton(searcher, dtoFactory, authorizer, singleton)
                );
            }
        }

        if (config.vectors() != null) {
            for (QueryConfig vector : config.vectors()) {
                base.put(
                    vector.name(),
                    createVector(searcher, dtoFactory, authorizer, vector)
                );
            }
        }

        return base;
    }

    private static DataFetcher<List<Stash>> createVector(
            Searcher searcher,
            DTOFactory dtoFactory,
            Auth authorizer,
            QueryConfig config
    ) {
        Template queryTemplate = rethrow(() -> new Handlebars().compileInline(config.query()), () ->"Check the query");
        String name = config.name();

        return (DataFetchingEnvironment environment) -> {
            Stash args = new Stash(environment.getArguments());
            List<String> authToken = authorizer.getAuthToken(args);

            long timestamp = getTimestamp(args);

            List<Stash> results = searcher.findAll(
                    queryTemplate,
                    args,
                    authToken,
                    timestamp
            );

            List<Stash> filledResult = dtoFactory.fillMany(results, timestamp);
            
            logger.trace("{} Results {} for {}, {}",
                name,
                filledResult,
                queryTemplate,
                args
            );
            
            return filledResult;
        };
    }

    private static DataFetcher<Stash> createSingleton(
            Searcher searcher,
            DTOFactory dtoFactory,
            Auth authorizer,
            QueryConfig config
    ) {
        Template queryTemplate = rethrow(() -> new Handlebars().compileInline(config.query()), () ->"Check the query");
        String name = config.name();

        return (DataFetchingEnvironment environment) -> {
            Stash args = new Stash(environment.getArguments());
//            GraphQLContext graphQLContext = environment.getGraphQlContext();

            List<String> authToken = authorizer.getAuthToken(args);

            long timestamp = getTimestamp(args);
            
            Map<String, Object> payload = searcher.find(
                queryTemplate,
                args,
                authToken,
                timestamp
            );

            logger.trace("{} DB Result {} for {}, {}",
                name,
                payload,
                queryTemplate,
                args
            );

            Stash filled = dtoFactory.fillOne(payload, timestamp);

            logger.trace("{} Result {} for {}, {}",
                name,
                filled,
                queryTemplate,
                args
            );
            
            return filled;
        };
    }

    private static long getTimestamp(Map<String, Object> args) {
        return args.containsKey("at")
            ? ((Number) args.get("at")).longValue()
            : System.currentTimeMillis();
    }
} 