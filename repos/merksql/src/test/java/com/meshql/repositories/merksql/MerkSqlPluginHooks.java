package com.meshql.repositories.merksql;

import com.github.jknack.handlebars.Handlebars;
import com.meshql.cert.Hooks;
import com.meshql.cert.IntegrationWorld;
import com.meshql.cert.SearcherTestTemplates;
import io.cucumber.java.Before;

public class MerkSqlPluginHooks extends Hooks {

    public MerkSqlPluginHooks(IntegrationWorld world) {
        super(world);
    }

    @Before
    @Override
    public void setUp() {
        super.setUp();

        MerkSqlConfig config = MerkSqlPlugin.tempConfig("cert-cucumber-test");
        this.world.storageConfig = config;
        this.world.plugin = new MerkSqlPlugin();

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
