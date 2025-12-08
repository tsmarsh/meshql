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
            return new GraphletteConfig(path, storage, schema, rootConfig);
        }
    }
}
