package com.meshql.repositories.postgres;

import com.meshql.core.config.StorageConfig;

/**
 * PostgreSQL storage configuration for tests.
 */
public class PostgresConfig extends StorageConfig {
    public String uri;
    public String table;
    public String username;
    public String password;
    public int maxPoolSize = 10;
    public int minIdle = 2;

    public PostgresConfig() {
        super("postgres");
    }
}
