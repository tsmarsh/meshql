package com.meshql.repositories.merksql;

import com.meshql.core.config.StorageConfig;

public class MerkSqlConfig extends StorageConfig {
    public String dataDir;
    public String topic;

    public MerkSqlConfig() {
        super("merksql");
    }
}
