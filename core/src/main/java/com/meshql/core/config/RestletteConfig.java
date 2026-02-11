package com.meshql.core.config;

import com.networknt.schema.JsonSchema;

import java.util.ArrayList;
import java.util.List;

public record RestletteConfig(
        List<String> tokens,
        String path,
        int port,
        StorageConfig storage,
        JsonSchema schema
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> tokens = new ArrayList<>();
        private String path;
        private int port = 3033;
        private StorageConfig storage;
        private JsonSchema schema;

        public Builder tokens(List<String> tokens) {
            this.tokens = new ArrayList<>(tokens);
            return this;
        }

        public Builder token(String token) {
            this.tokens.add(token);
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder storage(StorageConfig storage) {
            this.storage = storage;
            return this;
        }

        public Builder schema(JsonSchema schema) {
            this.schema = schema;
            return this;
        }

        public RestletteConfig build() {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("restlette path is required");
            }
            if (storage == null) {
                throw new IllegalArgumentException("restlette storage is required");
            }
            return new RestletteConfig(List.copyOf(tokens), path, port, storage, schema);
        }
    }
}
