package com.meshql.dsl;

import com.meshql.core.Config;
import com.meshql.core.config.*;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for individual GraphDsl query and resolver parsing.
 */
class GraphDslTest {

    @Test
    void singletonQueryById() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getById", id: "id"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(1, root.singletons().size());
        assertEquals(0, root.vectors().size());
        assertEquals("getById", root.singletons().get(0).name());
        assertEquals("{\"id\": \"{{id}}\"}", root.singletons().get(0).query());
    }

    @Test
    void vectorQueryByDottedPath() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getByCoop", "payload.coop_id": "id"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(0, root.singletons().size());
        assertEquals(1, root.vectors().size());
        assertEquals("getByCoop", root.vectors().get(0).name());
        assertEquals("{\"payload.coop_id\": \"{{id}}\"}", root.vectors().get(0).query());
    }

    @Test
    void forcedSingletonQuery() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        singleton "getByName", "payload.name": "id"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(1, root.singletons().size(), "should be singleton");
        assertEquals(0, root.vectors().size());
        assertEquals("getByName", root.singletons().get(0).name());
        assertEquals("{\"payload.name\": \"{{id}}\"}", root.singletons().get(0).query());
    }

    @Test
    void forcedVectorQuery() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        vector "getByName", "payload.name": "name"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(0, root.singletons().size());
        assertEquals(1, root.vectors().size(), "should be vector");
        assertEquals("getByName", root.vectors().get(0).name());
        assertEquals("{\"payload.name\": \"{{name}}\"}", root.vectors().get(0).query());
    }

    @Test
    void queryWithCustomHandlebarsVariable() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getByName", "payload.name": "name"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals("{\"payload.name\": \"{{name}}\"}", root.vectors().get(0).query());
    }

    @Test
    void singletonResolver() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getById", id: "id"
                        resolve "coop", fk: "coop_id", via: "getById", at: "/coop/graph"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(1, root.singletonResolvers().size());
        assertEquals(0, root.vectorResolvers().size());

        SingletonResolverConfig resolver = root.singletonResolvers().get(0);
        assertEquals("coop", resolver.name());
        assertEquals("coop_id", resolver.id());
        assertEquals("getById", resolver.queryName());
        assertEquals(URI.create("http://localhost:3033/coop/graph"), resolver.url());
    }

    @Test
    void vectorResolver() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getById", id: "id"
                        resolve "hens", via: "getByCoop", at: "/hen/graph"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(0, root.singletonResolvers().size());
        assertEquals(1, root.vectorResolvers().size());

        VectorResolverConfig resolver = root.vectorResolvers().get(0);
        assertEquals("hens", resolver.name());
        assertNull(resolver.id());
        assertEquals("getByCoop", resolver.queryName());
        assertEquals(URI.create("http://localhost:3033/hen/graph"), resolver.url());
    }

    @Test
    void dottedResolverName() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getById", id: "id"
                        resolve "hens.layReports", via: "getByHen", at: "/lay_report/graph"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(1, root.vectorResolvers().size());
        assertEquals("hens.layReports", root.vectorResolvers().get(0).name());
    }

    @Test
    void internalSingletonResolver() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getById", id: "id"
                        resolve "coop", fk: "coop_id", via: "getById", at: "/coop/graph", internal: true
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(0, root.singletonResolvers().size());
        assertEquals(1, root.internalSingletonResolvers().size());

        InternalSingletonResolverConfig resolver = root.internalSingletonResolvers().get(0);
        assertEquals("coop", resolver.name());
        assertEquals("coop_id", resolver.id());
        assertEquals("getById", resolver.queryName());
        assertEquals("/coop/graph", resolver.graphletteName());
    }

    @Test
    void internalVectorResolver() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test"
                entity("item") {
                    graph "/item/graph", "item.graphql", {
                        query "getById", id: "id"
                        resolve "hens", via: "getByCoop", at: "/hen/graph", internal: true
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        RootConfig root = config.graphlettes().get(0).rootConfig();

        assertEquals(0, root.vectorResolvers().size());
        assertEquals(1, root.internalVectorResolvers().size());

        InternalVectorResolverConfig resolver = root.internalVectorResolvers().get(0);
        assertEquals("hens", resolver.name());
        assertNull(resolver.id());
        assertEquals("getByCoop", resolver.queryName());
        assertEquals("/hen/graph", resolver.graphletteName());
    }
}
