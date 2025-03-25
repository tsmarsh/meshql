package com.meshql.repositories.postgres;

import com.meshql.core.config.StorageConfig;

public class PostgresConfig extends StorageConfig {
    public String host;
    public Integer port;
    public String db;
    public String user;
    public String password;
    public String table;
}
