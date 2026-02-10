---
title: Architecture
layout: default
nav_order: 2
has_children: true
---

# Architecture Overview

MeshQL is built on a simple premise: **the unit of architecture is the entity, not the service**.

Traditional microservice frameworks organize around business capabilities (user service, order service, payment service). MeshQL organizes around **data entities** — each entity becomes a *meshobj* that owns its storage, exposes dual APIs, and federates with other entities through explicit contracts.

---

## The Meshobj

A meshobj is the fundamental building block. It consists of:

```mermaid
graph TB
    subgraph meshobj["Meshobj: 'Hen'"]
        direction TB

        subgraph apis["API Layer"]
            direction LR
            graphlette["Graphlette<br/>/hen/graph<br/><i>GraphQL queries + federation</i>"]
            restlette["Restlette<br/>/hen/api<br/><i>CRUD + bulk + Swagger</i>"]
        end

        subgraph core["Core"]
            direction LR
            searcher["Searcher<br/><i>Template queries</i>"]
            repo["Repository<br/><i>CRUD operations</i>"]
            auth["Auth<br/><i>Token extraction + authz</i>"]
        end

        subgraph storage["Storage"]
            plugin["Plugin<br/><i>Factory</i>"]
            db[("MongoDB / Postgres<br/>/ SQLite / Memory")]
        end

        graphlette --> searcher
        restlette --> repo
        searcher --> plugin
        repo --> plugin
        plugin --> db
        auth -.-> searcher
        auth -.-> repo
    end

    style graphlette fill:#4a9eff,stroke:#333,color:#fff
    style restlette fill:#34d399,stroke:#333,color:#fff
    style searcher fill:#818cf8,stroke:#333,color:#fff
    style repo fill:#818cf8,stroke:#333,color:#fff
    style auth fill:#f472b6,stroke:#333,color:#fff
    style plugin fill:#fbbf24,stroke:#333,color:#000
    style db fill:#fbbf24,stroke:#333,color:#000
```

Each meshobj is:

- **Self-contained** — owns its schema, storage, queries, and auth rules
- **Dual-API** — REST for CRUD consumers, GraphQL for relational queries
- **Independently deployable** — can run in the same JVM or as a separate service
- **Federated** — connects to other meshobjs through resolvers, never through shared storage

---

## System Composition

A MeshQL application is a collection of meshobjs registered with a server:

```mermaid
graph TB
    subgraph server["MeshQL Server (Jetty 12 + Virtual Threads)"]
        direction TB

        subgraph farm["Farm Meshobj"]
            fg["/farm/graph"]
            fr["/farm/api"]
            fd[("farms")]
        end

        subgraph coop["Coop Meshobj"]
            cg["/coop/graph"]
            cr["/coop/api"]
            cd[("coops")]
        end

        subgraph hen["Hen Meshobj"]
            hg["/hen/graph"]
            hr["/hen/api"]
            hd[("hens")]
        end

        fg -- "resolve coops" --> cg
        cg -- "resolve hens" --> hg
        cg -- "resolve farm" --> fg
        hg -- "resolve coop" --> cg
    end

    client["Client"] --> fg
    client --> cg
    client --> hg
    client --> fr
    client --> cr
    client --> hr

    style fg fill:#4a9eff,stroke:#333,color:#fff
    style cg fill:#4a9eff,stroke:#333,color:#fff
    style hg fill:#4a9eff,stroke:#333,color:#fff
    style fr fill:#34d399,stroke:#333,color:#fff
    style cr fill:#34d399,stroke:#333,color:#fff
    style hr fill:#34d399,stroke:#333,color:#fff
    style fd fill:#fbbf24,stroke:#333,color:#000
    style cd fill:#fbbf24,stroke:#333,color:#000
    style hd fill:#fbbf24,stroke:#333,color:#000
    style client fill:#f87171,stroke:#333,color:#fff
```

The federation arrows between graphlettes are **HTTP calls** — even when running in the same JVM. This means the transition from monolith to distributed is a configuration change, not a rewrite.

---

## Core Interfaces

MeshQL's architecture is defined by five interfaces:

| Interface | Role | Used By |
|:----------|:-----|:--------|
| **Repository** | CRUD operations on Envelopes | Restlette (REST API) |
| **Searcher** | Template-driven queries | Graphlette (GraphQL API) |
| **Auth** | Token extraction + authorization checks | Both APIs |
| **Plugin** | Factory for Repository + Searcher per storage backend | Server initialization |
| **Validator** | JSON Schema validation of incoming data | Restlette (REST API) |

This separation means:
- **Repository** handles writes and ID lookups (optimized for REST patterns)
- **Searcher** handles filtered queries with Handlebars templates (optimized for GraphQL)
- Both share the same storage through a **Plugin** that creates them as a pair
- **Auth** is orthogonal to storage — any auth strategy works with any backend

---

## Configuration-Driven

A complete meshobj is defined declaratively:

```java
Config.builder()
    .graphlette(
        GraphletteConfig.builder()
            .path("/hen/graph")
            .storage(mongoConfig)
            .schema("/config/hen.graphql")
            .rootConfig(
                RootConfig.builder()
                    .singleton("getById", "{\"id\": \"{{id}}\"}")
                    .vector("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")
                    .singletonResolver("coop", "coop_id", "getById",
                        platformUrl + "/coop/graph")
                    .build()
            )
            .build()
    )
    .restlette(
        RestletteConfig.builder()
            .path("/hen/api")
            .storage(mongoConfig)
            .schema(jsonSchema)
            .build()
    )
    .port(3033)
    .build();
```

No annotations. No classpath scanning. No magic. Every relationship, query, and endpoint is explicit in the configuration.
