package com.meshql.repositories.memory;

import com.meshql.core.*;
import com.meshql.core.config.StorageConfig;

public class InMemoryPlugin implements Plugin {
    @Override
    public Searcher createSearcher(StorageConfig config) {
        return null;
    }

    @Override
    public Repository createRepository(StorageConfig config, Auth auth) {
        return new InMemoryRepository();
    }

    @Override
    public void cleanUp() {

    }
}
