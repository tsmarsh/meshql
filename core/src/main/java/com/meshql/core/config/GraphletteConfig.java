package com.meshql.core.config;

public record GraphletteConfig(
        String path,
        StorageConfig storage,
        String schema,
        RootConfig rootConfig
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String path;
        private StorageConfig storage;
        private String schema;
        private RootConfig rootConfig;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder storage(StorageConfig storage) {
            this.storage = storage;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder rootConfig(RootConfig rootConfig) {
            this.rootConfig = rootConfig;
            return this;
        }

        public Builder rootConfig(RootConfig.Builder rootConfigBuilder) {
            this.rootConfig = rootConfigBuilder.build();
            return this;
        }

        public GraphletteConfig build() {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("graphlette path is required");
            }
            if (storage == null) {
                throw new IllegalArgumentException("graphlette storage is required");
            }
            if (schema == null || schema.isBlank()) {
                throw new IllegalArgumentException("graphlette schema is required");
            }
            if (rootConfig == null) {
                throw new IllegalArgumentException("graphlette rootConfig is required");
            }
            return new GraphletteConfig(path, storage, schema, rootConfig);
        }
    }
}
