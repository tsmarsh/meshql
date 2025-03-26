package com.meshql.repos.sqlite;

import com.meshql.core.config.StorageConfig;

public class SQLiteConfig extends StorageConfig {
    public String file;
    public String table;

    public SQLiteConfig(String file, String table){
        super("sqlite");
        this.file = file;
        this.table = table;
    }
}
