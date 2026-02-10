---
title: Scalability Story
layout: default
parent: Architecture
nav_order: 3
---

# From MVP to Scale

MeshQL is designed for the startup trajectory: build fast, prove the business, then scale without rewriting.

---

## Phase 1: MVP (Week 1)

**Goal**: Ship something that works.

Deploy everything as a single JAR. All meshobjs share one JVM, one port, one deployment pipeline. Use MongoDB (flexible schema, fast iteration) and NoAuth (no auth overhead during development).

```mermaid
graph TB
    subgraph "Single JVM — One Docker Container"
        direction TB
        server["Jetty 12 (port 3033)"]

        subgraph meshobjs["All Meshobjs"]
            direction LR
            users["/users"]
            posts["/posts"]
            comments["/comments"]
        end

        server --> meshobjs
        meshobjs --> mongo[("MongoDB<br/>Single Instance")]
    end

    client["Clients"] --> server

    style server fill:#4a9eff,stroke:#333,color:#fff
    style users fill:#34d399,stroke:#333,color:#fff
    style posts fill:#34d399,stroke:#333,color:#fff
    style comments fill:#34d399,stroke:#333,color:#fff
    style mongo fill:#fbbf24,stroke:#333,color:#000
    style client fill:#f87171,stroke:#333,color:#fff
```

**What you get for free**:
- REST CRUD + Swagger for every entity
- GraphQL with federation between entities
- Temporal versioning (you'll want this later for auditing)
- Document-level auth tokens (ready when you add auth)

**What you defer**:
- Authentication (use NoAuth)
- Distributed deployment
- Performance optimization

**Performance at this stage**: Good enough. Jetty 12 with virtual threads handles thousands of concurrent connections. External resolvers add ~1ms of HTTP overhead per hop within the same JVM. For most MVPs, this is invisible.

---

## Phase 2: Product-Market Fit (Month 3)

**Goal**: Add security, handle real users.

Swap NoAuth for JWT (your API gateway already validates tokens). Add JSON Schema validation on REST endpoints. Index your foreign key fields in MongoDB.

```mermaid
graph TB
    subgraph "Single JVM — Behind API Gateway"
        direction TB

        gw["API Gateway<br/>(Kong / Istio / AWS ALB)<br/>JWT Validation"]

        subgraph server["MeshQL Server"]
            direction TB
            jwt["JWTSubAuthorizer<br/>Extracts sub claim"]
            meshobjs["All Meshobjs<br/>(with indexed queries)"]
        end

        gw --> jwt
        jwt --> meshobjs
        meshobjs --> mongo[("MongoDB<br/>Replica Set<br/>Indexed FKs")]
    end

    client["Clients"] --> gw

    style gw fill:#f472b6,stroke:#333,color:#fff
    style jwt fill:#f472b6,stroke:#333,color:#fff
    style meshobjs fill:#34d399,stroke:#333,color:#fff
    style mongo fill:#fbbf24,stroke:#333,color:#000
    style client fill:#f87171,stroke:#333,color:#fff
```

**Changes from Phase 1**:

| Change | Code Impact |
|:-------|:-----------|
| Add JWT auth | Change `new NoAuth()` to `new JWTSubAuthorizer()` |
| Add RBAC | Wrap with `CasbinAuth.create(model, policy, jwtAuth)` |
| Index foreign keys | Database operation, zero code changes |
| MongoDB replica set | Change connection URI |

**Performance optimization**: Index your foreign key fields. This is the single highest-impact change you can make:

```javascript
// These indexes provide ~100x improvement over unindexed queries
db.comments.createIndex({'payload.post_id': 1});
db.posts.createIndex({'payload.user_id': 1});
```

{: .tip }
> Database indexing provides 100x improvement. DataLoader batching provides 3-5x. Always index first.

---

## Phase 3: Growth (Month 6-12)

**Goal**: Handle increasing load. Some entities are hot, others are not.

Switch hot-path resolvers from external (HTTP) to internal (in-process). Split the truly hot meshobjs into separate services. Maybe move some entities to PostgreSQL for ACID transactions.

```mermaid
graph TB
    subgraph "Service A: User-facing"
        direction TB
        users2["/users"]
        posts2["/posts"]
        users2 ---|"internal resolver"| posts2
        db_a[("MongoDB")]
        users2 --> db_a
        posts2 --> db_a
    end

    subgraph "Service B: Comment Service"
        direction TB
        comments2["/comments"]
        db_b[("PostgreSQL<br/>ACID for moderation")]
        comments2 --> db_b
    end

    posts2 -. "external resolver<br/>(HTTP)" .-> comments2

    client2["Clients"] --> users2
    client2 --> comments2

    style users2 fill:#4a9eff,stroke:#333,color:#fff
    style posts2 fill:#4a9eff,stroke:#333,color:#fff
    style comments2 fill:#34d399,stroke:#333,color:#fff
    style db_a fill:#fbbf24,stroke:#333,color:#000
    style db_b fill:#818cf8,stroke:#333,color:#fff
    style client2 fill:#f87171,stroke:#333,color:#fff
```

**Changes from Phase 2**:

| Change | Code Impact |
|:-------|:-----------|
| External → internal resolvers | Change `SingletonResolverConfig` to `InternalSingletonResolverConfig` |
| Split to separate service | Change resolver URL from `localhost` to `comments-service:3033` |
| MongoDB → PostgreSQL | Change `MongoConfig` to `PostgresConfig`, change plugin registration |
| Add DataLoader batching | Already enabled by default |

**The key insight**: None of these changes require restructuring your code. They're configuration changes. The architectural boundaries were there from day one.

---

## Phase 4: Scale (Year 2+)

**Goal**: Handle the hockey stick. Multiple teams, independent deployment, real-time analytics.

Full distributed deployment. Each team owns their meshobjs. CDC pipelines feed analytics. Temporal queries power compliance reporting.

```mermaid
graph TB
    subgraph "Team A: User Platform"
        direction TB
        users3["/users<br/>Service"]
        profiles3["/profiles<br/>Service"]
        users3 ---|"internal"| profiles3
    end

    subgraph "Team B: Content"
        direction TB
        posts3["/posts<br/>Service"]
        media3["/media<br/>Service"]
        posts3 ---|"internal"| media3
    end

    subgraph "Team C: Engagement"
        direction TB
        comments3["/comments<br/>Service"]
        reactions3["/reactions<br/>Service"]
        comments3 ---|"internal"| reactions3
    end

    subgraph "Analytics Pipeline"
        direction TB
        debezium["Debezium CDC"]
        kafka["Kafka"]
        analytics["Analytics<br/>Consumers"]
        debezium --> kafka --> analytics
    end

    users3 -. "HTTP" .-> posts3
    posts3 -. "HTTP" .-> comments3
    users3 -. "HTTP" .-> comments3

    posts3 --> debezium
    comments3 --> debezium

    style users3 fill:#4a9eff,stroke:#333,color:#fff
    style profiles3 fill:#4a9eff,stroke:#333,color:#fff
    style posts3 fill:#34d399,stroke:#333,color:#fff
    style media3 fill:#34d399,stroke:#333,color:#fff
    style comments3 fill:#fbbf24,stroke:#333,color:#000
    style reactions3 fill:#fbbf24,stroke:#333,color:#000
    style debezium fill:#818cf8,stroke:#333,color:#fff
    style kafka fill:#818cf8,stroke:#333,color:#fff
    style analytics fill:#f472b6,stroke:#333,color:#fff
```

**What's happening**:
- Teams own their meshobjs independently
- Internal resolvers within team boundaries (high affinity)
- External resolvers across team boundaries (low coupling)
- CDC feeds analytics without impacting operational APIs
- Temporal queries power compliance and audit reporting
- Each team chooses their own storage backend

---

## What Changes at Each Phase

| Concern | MVP | PMF | Growth | Scale |
|:--------|:----|:----|:-------|:------|
| **Auth** | NoAuth | JWT | JWT + Casbin | JWT + Casbin |
| **Storage** | MongoDB | MongoDB (indexed) | Polyglot | Polyglot |
| **Resolvers** | External | External | Mixed | Mixed |
| **Deployment** | Single JVM | Single JVM | 2-3 services | N services |
| **Teams** | 1 | 1-2 | 2-4 | N |
| **Analytics** | None | None | Optional CDC | CDC pipelines |
| **Code changes** | N/A | 1 line (auth) | Config only | Config only |

The pattern is clear: **infrastructure complexity grows linearly with business complexity, and almost never requires code changes.**

---

## Performance Characteristics

MeshQL on Jetty 12 with virtual threads (Project Loom):

- **Concurrency**: Thousands of concurrent requests without thread pool exhaustion
- **I/O efficiency**: Virtual threads waiting on DB or HTTP don't consume platform thread resources
- **Federation overhead**: ~1ms per hop within same JVM (external), ~0ms (internal)
- **DataLoader batching**: Up to 100 IDs per batch request
- **Temporal queries**: Marginal overhead (~5-10%) over non-temporal queries

The bottleneck is almost always the database, not the framework. Invest in indexing first, topology second.
