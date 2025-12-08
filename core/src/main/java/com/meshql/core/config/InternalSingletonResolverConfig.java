package com.meshql.core.config;

public record InternalSingletonResolverConfig(
        String name,
        String id,
        String queryName,
        String graphletteName
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String id;
        private String queryName;
        private String graphletteName;

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

        public Builder graphletteName(String graphletteName) {
            this.graphletteName = graphletteName;
            return this;
        }

        public InternalSingletonResolverConfig build() {
            return new InternalSingletonResolverConfig(name, id, queryName, graphletteName);
        }
    }
}
