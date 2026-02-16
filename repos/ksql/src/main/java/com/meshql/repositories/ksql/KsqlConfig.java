package com.meshql.repositories.ksql;

import com.meshql.core.config.StorageConfig;

public class KsqlConfig extends StorageConfig {
    public String bootstrapServers;
    public String ksqlDbUrl;
    public String topic;
    public int partitions = 3;
    public int replicationFactor = 1;
    public boolean autoCreate = true;
    public String acks = "all";

    public KsqlConfig() {
        super("ksql");
    }
}
