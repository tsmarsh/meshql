# MeshQL Groovy DSL

A Groovy closure-delegate DSL for MeshQL configuration. Same `Config` records, no builder ceremony, no escaped JSON.

## Why

The farm topology changes monthly. The people who change it are developers who want to see the entity graph at a glance, not puzzle through builder nesting. This DSL reads like a specification document.

## Side-by-Side

**Java Builder** (from `farm/Main.java`):

```java
Config config = Config.builder()
    .port(3033)
    .graphlette(GraphletteConfig.builder()
        .path("/hen/graph")
        .storage(henDB)
        .schema("/app/config/graph/hen.graphql")
        .rootConfig(RootConfig.builder()
            .singleton("getById", "{\"id\": \"{{id}}\"}")
            .vector("getByName", "{\"payload.name\": \"{{name}}\"}")
            .vector("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")
            .singletonResolver("coop", "coop_id", "getById",
                platformUrl + "/coop/graph")
            .vectorResolver("layReports", null, "getByHen",
                platformUrl + "/lay_report/graph")))
    // ... 3 more entities, 4 restlettes
    .build();
```

**Groovy DSL** (from `farm.groovy`):

```groovy
meshql {
    port 3033
    mongo "mongodb://localhost:27017", "farm_development"

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

    // ... farm, coop, lay_report
}
```

The line count is similar. The win is readability:
- **No escaped JSON**: `id: "id"` instead of `"{\"id\": \"{{id}}\"}"`
- **Topology at a glance**: queries and resolvers read top-to-bottom, not buried in nested builder calls
- **One declaration per entity**: `mongo` + `collection` replaces 4 repeated `MongoConfig` constructions

## DSL Reference

### Top-Level

```groovy
meshql {
    port 3033                                          // Server port (default: 3033)
    mongo "mongodb://localhost:27017", "my_database"   // Default Mongo connection
    platform "http://myhost:8080"                      // Override resolver base URL

    entity("name") { ... }
}
```

### Entity

```groovy
entity("hen") {
    collection "my-collection-name"  // Override default collection naming

    graph "/hen/graph", "config/graph/hen.graphql", { ... }
    rest  "/hen/api",   "config/json/hen.schema.json"
}
```

### Queries

```groovy
graph "/path", "schema.graphql", {
    // Auto-detect: id-only queries become singletons, others become vectors
    query "getById",   id: "id"                    // singleton
    query "getByCoop", "payload.coop_id": "id"     // vector

    // Force singleton/vector explicitly
    singleton "getByName", "payload.name": "id"    // forced singleton
    vector    "getBySlug", "payload.slug": "slug"  // forced vector
}
```

Map keys are MongoDB field paths. Map values are Handlebars template parameter names. `id: "id"` generates `{"id": "{{id}}"}`.

### Resolvers

```groovy
graph "/path", "schema.graphql", {
    // Singleton resolver (has fk: — looks up parent's foreign key)
    resolve "coop", fk: "coop_id", via: "getById", at: "/coop/graph"

    // Vector resolver (no fk: — passes parent's id to target query)
    resolve "layReports", via: "getByHen", at: "/lay_report/graph"

    // Dotted names work as strings
    resolve "hens.layReports", via: "getByHen", at: "/lay_report/graph"

    // Internal resolvers (same JVM, no HTTP)
    resolve "coop", fk: "coop_id", via: "getById", at: "/coop/graph", internal: true
}
```

## Usage from Java

```java
// From a file
Config config = MeshQL.configure(new File("farm.groovy"));

// From classpath
Config config = MeshQL.configureFromClasspath("farm.groovy");

// From a string
Config config = MeshQL.configureFromString(groovyScript);
```

The returned `Config` is the same record the Java builders produce. Pass it to `Server.init()` as usual.

## Running Tests

```bash
mvn test -pl examples/farm-groovy-dsl -am
```

27 tests verify that the Groovy DSL produces identical `Config` objects to the Java builder for the full 4-entity farm.
