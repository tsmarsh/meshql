package com.meshql.core;


import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.RestletteConfig;

import java.util.ArrayList;
import java.util.List;

public record Config(
        List<String> casbinParams,
        List<GraphletteConfig> graphlettes,
        int port,
        List<RestletteConfig> restlettes,
        String corsOrigin,
        int connectTimeoutMs,
        int requestTimeoutMs
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> casbinParams = new ArrayList<>();
        private List<GraphletteConfig> graphlettes = new ArrayList<>();
        private int port = 3033;
        private List<RestletteConfig> restlettes = new ArrayList<>();
        private String corsOrigin = "*";
        private int connectTimeoutMs = 10_000;
        private int requestTimeoutMs = 30_000;

        public Builder casbinParams(List<String> casbinParams) {
            this.casbinParams = new ArrayList<>(casbinParams);
            return this;
        }

        public Builder casbinParam(String param) {
            this.casbinParams.add(param);
            return this;
        }

        public Builder graphlettes(List<GraphletteConfig> graphlettes) {
            this.graphlettes.addAll(graphlettes);
            return this;
        }

        public Builder graphlette(GraphletteConfig graphlette) {
            this.graphlettes.add(graphlette);
            return this;
        }

        public Builder graphlette(GraphletteConfig.Builder graphletteBuilder) {
            this.graphlettes.add(graphletteBuilder.build());
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder restlettes(List<RestletteConfig> restlettes) {
            this.restlettes.addAll(restlettes);
            return this;
        }

        public Builder restlette(RestletteConfig restlette) {
            this.restlettes.add(restlette);
            return this;
        }

        public Builder restlette(RestletteConfig.Builder restletteBuilder) {
            this.restlettes.add(restletteBuilder.build());
            return this;
        }

        public Builder corsOrigin(String corsOrigin) {
            this.corsOrigin = corsOrigin;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder requestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Config build() {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be 1-65535");
            }
            if (graphlettes.isEmpty() && restlettes.isEmpty()) {
                throw new IllegalArgumentException("at least one graphlette or restlette required");
            }

            return new Config(
                casbinParams.isEmpty() ? null : List.copyOf(casbinParams),
                List.copyOf(graphlettes),
                port,
                List.copyOf(restlettes),
                corsOrigin,
                connectTimeoutMs,
                requestTimeoutMs
            );
        }
    }
}
