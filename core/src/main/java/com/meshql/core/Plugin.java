package com.meshql.core;

import com.meshql.core.config.StorageConfig;

public interface Plugin {
    Searcher createSearcher(StorageConfig config);
    Repository createRepository(StorageConfig config, Auth auth);
    void cleanUp();
}
