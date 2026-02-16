package com.meshql.repos.sqlite;

import com.meshql.core.config.StorageConfig;

import java.util.Collections;
import java.util.List;

public class SQLiteConfig extends StorageConfig {
    public String file;
    public String table;
    public List<String> indexedFields;

    public SQLiteConfig(String file, String table) {
        this(file, table, Collections.emptyList());
    }

    public SQLiteConfig(String file, String table, List<String> indexedFields) {
        super("sqlite");
        this.file = file;
        this.table = table;
        this.indexedFields = indexedFields;
    }
}
