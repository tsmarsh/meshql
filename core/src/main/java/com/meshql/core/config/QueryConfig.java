package com.meshql.core.config;

public record QueryConfig(
    String name,
    String query
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String query;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public QueryConfig build() {
            return new QueryConfig(name, query);
        }
    }
}
