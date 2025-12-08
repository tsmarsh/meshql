package com.meshql.core.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public record RootConfig(
    List<QueryConfig> singletons,
    List<QueryConfig> vectors,
    List<SingletonResolverConfig> singletonResolvers,
    List<VectorResolverConfig> vectorResolvers,
    List<InternalSingletonResolverConfig> internalSingletonResolvers,
    List<InternalVectorResolverConfig> internalVectorResolvers,
    boolean dataLoaderEnabled
) {
    /**
     * Constructor with dataLoaderEnabled defaulting to true for backward compatibility.
     */
    public RootConfig(
        List<QueryConfig> singletons,
        List<QueryConfig> vectors,
        List<SingletonResolverConfig> singletonResolvers,
        List<VectorResolverConfig> vectorResolvers,
        List<InternalSingletonResolverConfig> internalSingletonResolvers,
        List<InternalVectorResolverConfig> internalVectorResolvers
    ) {
        this(singletons, vectors, singletonResolvers, vectorResolvers,
             internalSingletonResolvers, internalVectorResolvers, true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<QueryConfig> singletons = new ArrayList<>();
        private List<QueryConfig> vectors = new ArrayList<>();
        private List<SingletonResolverConfig> singletonResolvers = new ArrayList<>();
        private List<VectorResolverConfig> vectorResolvers = new ArrayList<>();
        private List<InternalSingletonResolverConfig> internalSingletonResolvers = new ArrayList<>();
        private List<InternalVectorResolverConfig> internalVectorResolvers = new ArrayList<>();
        private boolean dataLoaderEnabled = true;

        // Singleton query methods
        public Builder singleton(QueryConfig query) {
            this.singletons.add(query);
            return this;
        }

        public Builder singleton(String name, String query) {
            return singleton(new QueryConfig(name, query));
        }

        public Builder singletons(List<QueryConfig> queries) {
            this.singletons.addAll(queries);
            return this;
        }

        // Vector query methods
        public Builder vector(QueryConfig query) {
            this.vectors.add(query);
            return this;
        }

        public Builder vector(String name, String query) {
            return vector(new QueryConfig(name, query));
        }

        public Builder vectors(List<QueryConfig> queries) {
            this.vectors.addAll(queries);
            return this;
        }

        // Singleton resolver methods (external)
        public Builder singletonResolver(SingletonResolverConfig resolver) {
            this.singletonResolvers.add(resolver);
            return this;
        }

        public Builder singletonResolver(String name, String id, String queryName, String url) {
            return singletonResolver(new SingletonResolverConfig(name, id, queryName, URI.create(url)));
        }

        public Builder singletonResolver(String name, String id, String queryName, URI url) {
            return singletonResolver(new SingletonResolverConfig(name, id, queryName, url));
        }

        public Builder singletonResolvers(List<SingletonResolverConfig> resolvers) {
            this.singletonResolvers.addAll(resolvers);
            return this;
        }

        // Vector resolver methods (external)
        public Builder vectorResolver(VectorResolverConfig resolver) {
            this.vectorResolvers.add(resolver);
            return this;
        }

        public Builder vectorResolver(String name, String id, String queryName, String url) {
            return vectorResolver(new VectorResolverConfig(name, id, queryName, URI.create(url)));
        }

        public Builder vectorResolver(String name, String id, String queryName, URI url) {
            return vectorResolver(new VectorResolverConfig(name, id, queryName, url));
        }

        public Builder vectorResolvers(List<VectorResolverConfig> resolvers) {
            this.vectorResolvers.addAll(resolvers);
            return this;
        }

        // Internal singleton resolver methods
        public Builder internalSingletonResolver(InternalSingletonResolverConfig resolver) {
            this.internalSingletonResolvers.add(resolver);
            return this;
        }

        public Builder internalSingletonResolver(String name, String id, String queryName, String graphletteName) {
            return internalSingletonResolver(new InternalSingletonResolverConfig(name, id, queryName, graphletteName));
        }

        public Builder internalSingletonResolvers(List<InternalSingletonResolverConfig> resolvers) {
            this.internalSingletonResolvers.addAll(resolvers);
            return this;
        }

        // Internal vector resolver methods
        public Builder internalVectorResolver(InternalVectorResolverConfig resolver) {
            this.internalVectorResolvers.add(resolver);
            return this;
        }

        public Builder internalVectorResolver(String name, String id, String queryName, String graphletteName) {
            return internalVectorResolver(new InternalVectorResolverConfig(name, id, queryName, graphletteName));
        }

        public Builder internalVectorResolvers(List<InternalVectorResolverConfig> resolvers) {
            this.internalVectorResolvers.addAll(resolvers);
            return this;
        }

        // DataLoader setting
        public Builder dataLoaderEnabled(boolean enabled) {
            this.dataLoaderEnabled = enabled;
            return this;
        }

        public RootConfig build() {
            return new RootConfig(
                List.copyOf(singletons),
                List.copyOf(vectors),
                List.copyOf(singletonResolvers),
                List.copyOf(vectorResolvers),
                List.copyOf(internalSingletonResolvers),
                List.copyOf(internalVectorResolvers),
                dataLoaderEnabled
            );
        }
    }
}
