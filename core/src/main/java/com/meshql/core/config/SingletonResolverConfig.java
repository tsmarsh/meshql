package com.meshql.core.config;

import java.net.URI;

public record SingletonResolverConfig(
        String name,
        String id,
        String queryName,
        URI url
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String id;
        private String queryName;
        private URI url;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder queryName(String queryName) {
            this.queryName = queryName;
            return this;
        }

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder url(String url) {
            this.url = URI.create(url);
            return this;
        }

        public SingletonResolverConfig build() {
            return new SingletonResolverConfig(name, id, queryName, url);
        }
    }
}
