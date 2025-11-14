package com.meshql.cert.example;

import com.github.jknack.handlebars.Handlebars;
import com.meshql.cert.Hooks;
import com.meshql.cert.IntegrationWorld;
import com.meshql.cert.SearcherTestTemplates;
import com.meshql.core.config.StorageConfig;
import com.meshql.repositories.memory.InMemoryPlugin;
import io.cucumber.java.Before;

/**
 * Example hooks implementation for testing with InMemoryPlugin.
 * This serves as a reference implementation for plugin developers.
 */
public class InMemoryPluginHooks extends Hooks {

    public InMemoryPluginHooks(IntegrationWorld world) {
        super(world);
    }

    @Before
    @Override
    public void setUp() {
        super.setUp(); // Sets up testStartTime, tokens, and auth

        // Configure the plugin
        world.storageConfig = new StorageConfig("memory");
        world.plugin = new InMemoryPlugin();

        // Note: InMemoryPlugin currently doesn't implement Searcher
        // so Searcher tests will be skipped
        try {
            Handlebars handlebars = new Handlebars();
            world.templates = new SearcherTestTemplates(
                handlebars.compileInline("id = '{{id}}'"),
                handlebars.compileInline("name = '{{id}}'"),
                handlebars.compileInline("type = '{{id}}'"),
                handlebars.compileInline("name = '{{name}}' AND type = '{{type}}'")
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile templates", e);
        }
    }
}
