package com.meshql.api.graphql;

public class SubgraphException extends RuntimeException {
    public SubgraphException(String message) {
        super(message);
    }

    public SubgraphException(String message, Throwable cause) {
        super(message, cause);
    }
} 