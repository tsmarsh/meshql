# MeshQL Groovy DSL

A Groovy closure-delegate DSL for MeshQL configuration. Same `Config` records, no builder ceremony, no escaped JSON.

## Why

A platform built on MeshQL might ship new features monthly, but the individual service configs — potentially hundreds of them — change on their own schedules. A new query added to a customer entity, a resolver wired between two services that didn't talk before, a collection renamed during a data migration. These are small, precise changes made by people who understand the domain but shouldn't need to understand Java builder patterns or escaped JSON template syntax.

The Groovy DSL makes these configs **editable by power users who don't think of themselves as programmers**. The grammar is constrained enough to be teachable in a short session: `query`, `resolve`, `entity`, named parameters. There are no generics, no type annotations, no semicolons, no imports. A domain expert can read a config, understand what it does, and make a change with confidence.

This opens up operational models that Java builder code can't support:

- **An editing console** where authorised users modify configs through a web UI, with syntax validation and a preview environment to test changes before promoting them to production — immediately or on a schedule.
- **Audit trails** where every config change is versioned and attributed to the user who made it, because the config is a text file, not compiled code.
- **Delegation without deployment**. A platform team ships the server. Domain teams own their entity configs. Changes don't require a rebuild or a release — just a validated config swap and a server restart.

The DSL produces the exact same `Config` records as the Java builders. Nothing in MeshQL core changes. It's an alternative front door for people who need to read and edit configurations more often than they need to write Java.

## Two Cadences, Two Environments

Code and config change at different speeds and are owned by different people. The environment model should reflect that.

**QA** is where software engineering tests releases. A monthly cycle: new Java code, new features, schema changes, dependency upgrades. QA configs are typically abstract and test-case-centric — minimal entity definitions wired to in-memory or containerised databases, shaped to exercise specific code paths rather than model production reality. Engineers own these configs and they ship alongside the code.

**UAT** is where authorised power users — domain experts, operations leads, data analysts — work on production-shaped configs in a safe sandbox. UAT runs the same server binary as production but loads its own Groovy DSL configs pointing at its own databases. The configs model real entities, real relationships, real query patterns. Power users can:

- Add a query to an entity they're responsible for
- Wire a new resolver between two services
- Rename a collection as part of a data migration
- Test the result against real (or realistic) data before promoting

Config changes promoted out of UAT don't wait for monthly releases. They follow their own approval workflow: edit, validate, test in UAT, promote to production — on whatever schedule the business requires. A new query might go live the same afternoon. A resolver change might sit in UAT for a week while downstream teams verify.

```
                          monthly releases
                                │
    ┌─────┐    code + config    │    ┌──────┐    config only    ┌──────┐
    │ Dev │ ──────────────────► │ ──►│  QA  │                   │ UAT  │
    └─────┘                     │    └──────┘                   └──────┘
                                │        │                         │
                                │        │ release                 │ promote
                                │        ▼                         ▼
                                │    ┌──────────────────────────────────┐
                                └──► │           Production            │
                                     └──────────────────────────────────┘
```

This separation works because the DSL configs are text files, not compiled code. They can be versioned in their own repository (or subdirectory), governed by their own access controls, and diffed in a pull request by someone who has never opened an IDE. The audit trail is the commit history. The approval gate is the review process. The rollback is `git revert`.

The grammar is deliberately constrained to make this safe. A power user can't introduce arbitrary code execution — they can only declare entities, queries, and resolvers using the fixed vocabulary of the DSL. Training is a short session, not a Java bootcamp.

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
