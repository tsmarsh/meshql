package com.meshql.dsl;

import com.meshql.core.Config;
import com.meshql.core.config.*;
import com.meshql.repositories.mongo.MongoConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Groovy DSL produces the same Config as the Java builder.
 */
class MeshqlDslTest {

    private static final String PLATFORM_URL = "http://localhost:3033";

    /**
     * Build the reference config using the Java builder — mirrors farm Main.java exactly.
     */
    private Config buildJavaConfig() {
        MongoConfig farmDB = createMongoConfig("farm");
        MongoConfig coopDB = createMongoConfig("coop");
        MongoConfig henDB = createMongoConfig("hen");
        MongoConfig layReportDB = createMongoConfig("lay_report");

        return Config.builder()
                .port(3033)
                .graphlette(GraphletteConfig.builder()
                        .path("/farm/graph")
                        .storage(farmDB)
                        .schema("config/graph/farm.graphql")
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "{\"id\": \"{{id}}\"}")
                                .vectorResolver("coops", null, "getByFarm", PLATFORM_URL + "/coop/graph")))
                .graphlette(GraphletteConfig.builder()
                        .path("/coop/graph")
                        .storage(coopDB)
                        .schema("config/graph/coop.graphql")
                        .rootConfig(RootConfig.builder()
                                .singleton("getByName", "{\"payload.name\": \"{{id}}\"}")
                                .singleton("getById", "{\"id\": \"{{id}}\"}")
                                .vector("getByFarm", "{\"payload.farm_id\": \"{{id}}\"}")
                                .singletonResolver("farm", "farm_id", "getById", PLATFORM_URL + "/farm/graph")
                                .vectorResolver("hens", null, "getByCoop", PLATFORM_URL + "/hen/graph")
                                .vectorResolver("hens.layReports", null, "getByHen", PLATFORM_URL + "/lay_report/graph")))
                .graphlette(GraphletteConfig.builder()
                        .path("/hen/graph")
                        .storage(henDB)
                        .schema("config/graph/hen.graphql")
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "{\"id\": \"{{id}}\"}")
                                .vector("getByName", "{\"payload.name\": \"{{name}}\"}")
                                .vector("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")
                                .singletonResolver("coop", "coop_id", "getById", PLATFORM_URL + "/coop/graph")
                                .vectorResolver("layReports", null, "getByHen", PLATFORM_URL + "/lay_report/graph")))
                .graphlette(GraphletteConfig.builder()
                        .path("/lay_report/graph")
                        .storage(layReportDB)
                        .schema("config/graph/lay_report.graphql")
                        .rootConfig(RootConfig.builder()
                                .singleton("getById", "{\"id\": \"{{id}}\"}")
                                .vector("getByHen", "{\"payload.hen_id\": \"{{id}}\"}")
                                .singletonResolver("hen", "hen_id", "getById", PLATFORM_URL + "/hen/graph")))
                .restlette(RestletteConfig.builder()
                        .path("/farm/api").port(3033).storage(farmDB))
                .restlette(RestletteConfig.builder()
                        .path("/coop/api").port(3033).storage(coopDB))
                .restlette(RestletteConfig.builder()
                        .path("/hen/api").port(3033).storage(henDB))
                .restlette(RestletteConfig.builder()
                        .path("/lay_report/api").port(3033).storage(layReportDB))
                .build();
    }

    private static MongoConfig createMongoConfig(String entity) {
        MongoConfig config = new MongoConfig();
        config.uri = "mongodb://localhost:27017";
        config.db = "farm_development";
        config.collection = "farm-development-" + entity;
        return config;
    }

    private Config buildGroovyConfig() {
        String script = """
            meshql {
                port 3033
                mongo "mongodb://localhost:27017", "farm_development"

                entity("farm") {
                    collection "farm-development-farm"
                    graph "/farm/graph", "config/graph/farm.graphql", {
                        query   "getById", id: "id"
                        resolve "coops", via: "getByFarm", at: "/coop/graph"
                    }
                    rest "/farm/api", "config/json/farm.schema.json"
                }

                entity("coop") {
                    collection "farm-development-coop"
                    graph "/coop/graph", "config/graph/coop.graphql", {
                        singleton "getByName", "payload.name": "id"
                        query     "getById",   id: "id"
                        query     "getByFarm", "payload.farm_id": "id"
                        resolve "farm",            fk: "farm_id", via: "getById",  at: "/farm/graph"
                        resolve "hens",                           via: "getByCoop", at: "/hen/graph"
                        resolve "hens.layReports",                via: "getByHen", at: "/lay_report/graph"
                    }
                    rest "/coop/api", "config/json/coop.schema.json"
                }

                entity("hen") {
                    collection "farm-development-hen"
                    graph "/hen/graph", "config/graph/hen.graphql", {
                        query "getById",   id: "id"
                        query "getByName", "payload.name": "name"
                        query "getByCoop", "payload.coop_id": "id"
                        resolve "coop",       fk: "coop_id", via: "getById",  at: "/coop/graph"
                        resolve "layReports",                 via: "getByHen", at: "/lay_report/graph"
                    }
                    rest "/hen/api", "config/json/hen.schema.json"
                }

                entity("lay_report") {
                    collection "farm-development-lay_report"
                    graph "/lay_report/graph", "config/graph/lay_report.graphql", {
                        query "getById",  id: "id"
                        query "getByHen", "payload.hen_id": "id"
                        resolve "hen", fk: "hen_id", via: "getById", at: "/hen/graph"
                    }
                    rest "/lay_report/api", "config/json/lay_report.schema.json"
                }
            }
            """;
        return MeshQL.configureFromString(script);
    }

    @Test
    void groovyDslProducesSamePortAsJavaBuilder() {
        assertEquals(buildJavaConfig().port(), buildGroovyConfig().port());
    }

    @Test
    void groovyDslProducesSameNumberOfGraphlettes() {
        assertEquals(buildJavaConfig().graphlettes().size(), buildGroovyConfig().graphlettes().size());
    }

    @Test
    void groovyDslProducesSameNumberOfRestlettes() {
        assertEquals(buildJavaConfig().restlettes().size(), buildGroovyConfig().restlettes().size());
    }

    @Test
    void farmGraphletteMatchesJavaBuilder() {
        assertGraphletteEquals(buildJavaConfig().graphlettes().get(0),
                buildGroovyConfig().graphlettes().get(0));
    }

    @Test
    void coopGraphletteMatchesJavaBuilder() {
        assertGraphletteEquals(buildJavaConfig().graphlettes().get(1),
                buildGroovyConfig().graphlettes().get(1));
    }

    @Test
    void henGraphletteMatchesJavaBuilder() {
        assertGraphletteEquals(buildJavaConfig().graphlettes().get(2),
                buildGroovyConfig().graphlettes().get(2));
    }

    @Test
    void layReportGraphletteMatchesJavaBuilder() {
        assertGraphletteEquals(buildJavaConfig().graphlettes().get(3),
                buildGroovyConfig().graphlettes().get(3));
    }

    @Test
    void restlettePathsMatchJavaBuilder() {
        Config java = buildJavaConfig();
        Config groovy = buildGroovyConfig();
        for (int i = 0; i < java.restlettes().size(); i++) {
            assertEquals(java.restlettes().get(i).path(), groovy.restlettes().get(i).path(),
                    "Restlette path mismatch at index " + i);
            assertEquals(java.restlettes().get(i).port(), groovy.restlettes().get(i).port(),
                    "Restlette port mismatch at index " + i);
            assertMongoConfigEquals(java.restlettes().get(i).storage(),
                    groovy.restlettes().get(i).storage());
        }
    }

    @Test
    void customPortIsRespected() {
        String script = """
            meshql {
                port 4055
                mongo "mongodb://localhost:27017", "test_db"
                entity("test") {
                    graph "/test/graph", "test.graphql", {
                        query "getById", id: "id"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        assertEquals(4055, config.port());
    }

    @Test
    void defaultPortIs3033() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "test_db"
                entity("test") {
                    graph "/test/graph", "test.graphql", {
                        query "getById", id: "id"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        assertEquals(3033, config.port());
    }

    @Test
    void platformUrlOverrideWorks() {
        String script = """
            meshql {
                port 3033
                platform "http://myhost:8080"
                mongo "mongodb://localhost:27017", "test_db"
                entity("test") {
                    graph "/test/graph", "test.graphql", {
                        query "getById", id: "id"
                        resolve "related", via: "getByTest", at: "/other/graph"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        VectorResolverConfig resolver = config.graphlettes().get(0).rootConfig().vectorResolvers().get(0);
        assertEquals(URI.create("http://myhost:8080/other/graph"), resolver.url());
    }

    @Test
    void defaultCollectionNaming() {
        String script = """
            meshql {
                mongo "mongodb://localhost:27017", "farm_development"
                entity("hen") {
                    graph "/hen/graph", "hen.graphql", {
                        query "getById", id: "id"
                    }
                }
            }
            """;
        Config config = MeshQL.configureFromString(script);
        MongoConfig storage = (MongoConfig) config.graphlettes().get(0).storage();
        assertEquals("farm-development-hen", storage.collection);
    }

    private void assertGraphletteEquals(GraphletteConfig expected, GraphletteConfig actual) {
        assertEquals(expected.path(), actual.path());
        assertEquals(expected.schema(), actual.schema());
        assertMongoConfigEquals(expected.storage(), actual.storage());
        assertRootConfigEquals(expected.rootConfig(), actual.rootConfig());
    }

    private void assertMongoConfigEquals(StorageConfig expected, StorageConfig actual) {
        assertInstanceOf(MongoConfig.class, expected);
        assertInstanceOf(MongoConfig.class, actual);
        MongoConfig e = (MongoConfig) expected;
        MongoConfig a = (MongoConfig) actual;
        assertEquals(e.uri, a.uri, "MongoConfig.uri");
        assertEquals(e.db, a.db, "MongoConfig.db");
        assertEquals(e.collection, a.collection, "MongoConfig.collection");
    }

    private void assertRootConfigEquals(RootConfig expected, RootConfig actual) {
        assertQueryConfigs("singletons", expected.singletons(), actual.singletons());
        assertQueryConfigs("vectors", expected.vectors(), actual.vectors());
        assertSingletonResolvers(expected.singletonResolvers(), actual.singletonResolvers());
        assertVectorResolvers(expected.vectorResolvers(), actual.vectorResolvers());
        assertEquals(expected.internalSingletonResolvers().size(),
                actual.internalSingletonResolvers().size(), "internalSingletonResolvers count");
        assertEquals(expected.internalVectorResolvers().size(),
                actual.internalVectorResolvers().size(), "internalVectorResolvers count");
    }

    private void assertQueryConfigs(String label, List<QueryConfig> expected, List<QueryConfig> actual) {
        assertEquals(expected.size(), actual.size(), label + " count");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).name(), actual.get(i).name(),
                    label + "[" + i + "].name");
            assertEquals(expected.get(i).query(), actual.get(i).query(),
                    label + "[" + i + "].query");
        }
    }

    private void assertSingletonResolvers(List<SingletonResolverConfig> expected,
                                          List<SingletonResolverConfig> actual) {
        assertEquals(expected.size(), actual.size(), "singletonResolvers count");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).name(), actual.get(i).name());
            assertEquals(expected.get(i).id(), actual.get(i).id());
            assertEquals(expected.get(i).queryName(), actual.get(i).queryName());
            assertEquals(expected.get(i).url(), actual.get(i).url());
        }
    }

    private void assertVectorResolvers(List<VectorResolverConfig> expected,
                                       List<VectorResolverConfig> actual) {
        assertEquals(expected.size(), actual.size(), "vectorResolvers count");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).name(), actual.get(i).name());
            assertEquals(expected.get(i).id(), actual.get(i).id());
            assertEquals(expected.get(i).queryName(), actual.get(i).queryName());
            assertEquals(expected.get(i).url(), actual.get(i).url());
        }
    }
}
