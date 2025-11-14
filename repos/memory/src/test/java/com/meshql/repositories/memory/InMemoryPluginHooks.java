package com.meshql.repositories.memory;

import com.github.jknack.handlebars.Handlebars;
import com.meshql.cert.Hooks;
import com.meshql.cert.IntegrationWorld;
import com.meshql.cert.SearcherTestTemplates;
import com.meshql.core.config.StorageConfig;
import io.cucumber.java.Before;

/**
 * Cucumber hooks for InMemory certification tests.
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
        this.world.storageConfig = new StorageConfig("memory");
        this.world.plugin = new InMemoryPlugin();

        // Set up query templates (InMemoryPlugin doesn't implement Searcher yet)
        try {
            Handlebars handlebars = new Handlebars();
            this.world.templates = new SearcherTestTemplates(
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
