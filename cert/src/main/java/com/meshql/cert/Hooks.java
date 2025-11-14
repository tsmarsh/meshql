package com.meshql.cert;

import com.meshql.auth.noop.NoAuth;
import io.cucumber.java.Before;

import java.util.Collections;

/**
 * Cucumber hooks for test setup and teardown.
 * Database plugin implementations should extend this class and provide:
 * - Plugin implementation
 * - StorageConfig
 * - SearcherTestTemplates with database-specific queries
 */
public class Hooks {
    protected final IntegrationWorld world;

    public Hooks(IntegrationWorld world) {
        this.world = world;
    }

    @Before
    public void setUp() {
        world.testStartTime = System.currentTimeMillis();
        world.tokens = Collections.singletonList("test-token");
        world.auth = new NoAuth();

        // Subclasses should override to provide:
        // - world.plugin
        // - world.storageConfig
        // - world.templates
    }
}
