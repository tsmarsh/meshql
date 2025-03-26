package com.meshql.repositories.mongo;

import com.meshql.core.config.StorageConfig;
import com.tailoredshapes.stash.Stash;

public class MongoConfig extends StorageConfig {
    public String uri;
    public String collection;
    public String db;
    public Stash options;

    public MongoConfig() {
        super("mongo");
    }
}
