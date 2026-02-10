---
title: Federation
layout: default
parent: Architecture
nav_order: 2
---

# Federation

Federation is how meshobjs connect to each other. When a Coop needs to resolve its list of Hens, it doesn't query the Hens table directly — it calls the Hen graphlette through a resolver.

This indirection is the foundation of MeshQL's scalability story.

---

## The Single-Hop Constraint

MeshQL enforces a critical architectural rule: **federation never goes beyond one hop**.

```mermaid
graph LR
    subgraph "Allowed"
        direction LR
        A1[Farm] -->|"resolve coops"| B1[Coop]
        B1 -->|"resolve hens"| C1[Hen]
    end

    style A1 fill:#34d399,stroke:#333,color:#fff
    style B1 fill:#34d399,stroke:#333,color:#fff
    style C1 fill:#34d399,stroke:#333,color:#fff
```

```mermaid
graph LR
    subgraph "Not Allowed"
        direction LR
        A2[Farm] -->|"resolve coops"| B2[Coop] -->|"resolve hens"| C2[Hen] -->|"resolve coop"| B2
    end

    style A2 fill:#f87171,stroke:#333,color:#fff
    style B2 fill:#f87171,stroke:#333,color:#fff
    style C2 fill:#f87171,stroke:#333,color:#fff
```

When a client queries Farm and asks for nested Coops and Hens, the *client* initiates each hop. The Farm resolver returns Coops; the client's query then triggers the Coop resolver to fetch Hens. No resolver ever triggers another resolver transitively.

This prevents:
- **Cascading failures** — a slow Hen service can't cascade through Coop to Farm
- **Unbounded latency** — each hop is explicit and measurable
- **Circular dependencies** — A calls B calls A can't happen
- **Hidden coupling** — every dependency is visible in the resolver configuration

---

## Resolver Types

MeshQL provides four resolver types, organized by cardinality and deployment:

```mermaid
quadrantChart
    title Resolver Types
    x-axis "Same JVM" --> "Separate Services"
    y-axis "One-to-One" --> "One-to-Many"
    quadrant-1 External Vector
    quadrant-2 Internal Vector
    quadrant-3 Internal Singleton
    quadrant-4 External Singleton
```

### External Resolvers (HTTP)

For meshobjs that may be deployed as separate services:

```java
// 1:1 — Hen resolves its Coop
new SingletonResolverConfig(
    "coop",                    // Field name in GraphQL schema
    "coop_id",                 // Foreign key in this entity
    "getById",                 // Query on the target graphlette
    URI.create("http://localhost:3033/coop/graph")
)

// 1:N — Coop resolves its Hens
new VectorResolverConfig(
    "hens",                    // Field name in GraphQL schema
    "id",                      // This entity's ID (passed as parameter)
    "getByCoop",               // Query on the target graphlette
    URI.create("http://localhost:3033/hen/graph")
)
```

### Internal Resolvers (In-Process)

For meshobjs that share a JVM — same semantics, zero HTTP overhead:

```java
// 1:1 — Hen resolves its Coop (in-process)
new InternalSingletonResolverConfig(
    "coop", "coop_id", "getById", "/coop/graph"
)

// 1:N — Coop resolves its Hens (in-process)
new InternalVectorResolverConfig(
    "hens", "id", "getByCoop", "/hen/graph"
)
```

```mermaid
sequenceDiagram
    participant Client
    participant Farm as Farm Graphlette
    participant Coop as Coop Graphlette

    rect rgb(220, 240, 255)
        note right of Farm: External Resolver
        Client->>Farm: query { farm { coops { name } } }
        Farm->>Coop: HTTP POST /coop/graph<br/>{ getByFarm(id: "farm-1") { name } }
        Coop-->>Farm: [{ name: "Main Coop" }]
        Farm-->>Client: { farm: { coops: [{ name: "Main Coop" }] } }
    end

    rect rgb(220, 255, 220)
        note right of Farm: Internal Resolver
        Client->>Farm: query { farm { coops { name } } }
        Farm->>Coop: Direct method call<br/>executeInternal(query)
        Coop-->>Farm: [{ name: "Main Coop" }]
        Farm-->>Client: { farm: { coops: [{ name: "Main Coop" }] } }
    end
```

{: .note }
> Internal resolvers aren't just a performance optimization. They're a **granularity integrator** — they declare that two entities are coupled enough to share a deployment unit. Use them when entities have high affinity (frequent cross-queries, shared lifecycle, same team ownership).

---

## DataLoader Batching

Without batching, resolving N Hens for a Coop means N separate queries. MeshQL uses the DataLoader pattern to batch these into a single request:

```mermaid
sequenceDiagram
    participant GQL as GraphQL Engine
    participant DL as DataLoader
    participant Target as Target Graphlette

    GQL->>DL: load("hen-1")
    GQL->>DL: load("hen-2")
    GQL->>DL: load("hen-3")
    Note over DL: Batch window closes

    DL->>Target: Single request:<br/>{ a: getById(id:"hen-1") { ... }<br/>  b: getById(id:"hen-2") { ... }<br/>  c: getById(id:"hen-3") { ... } }
    Target-->>DL: { a: {...}, b: {...}, c: {...} }

    DL-->>GQL: hen-1 result
    DL-->>GQL: hen-2 result
    DL-->>GQL: hen-3 result
```

Key details:
- **Max batch size**: 100 IDs per request
- **Request-scoped**: Fresh DataLoaderRegistry per request (no cache pollution)
- **Works for both**: External (HTTP batched) and internal (in-process batched) resolvers
- **Configurable**: `dataLoaderEnabled` flag in RootConfig
- **Aliased queries**: Uses GraphQL aliases (`item_0`, `item_1`, ...) to batch multiple lookups into one query

{: .tip }
> DataLoader batching helps, but **database indexing helps more**. A properly indexed foreign key query (e.g., `payload.coop_id`) provides 100x improvement over batching. Always index your foreign key fields first, then enable DataLoader for the remaining benefit.

---

## Contract Ownership

Each meshobj defines its own view of foreign types:

```mermaid
graph TB
    subgraph coop_schema["coop.graphql (Coop's view)"]
        direction TB
        coop_type["type Coop {<br/>  id: ID!<br/>  name: String<br/>  farm: Farm<br/>  hens: [Hen]<br/>}"]
        coop_farm["type Farm {<br/>  id: ID!<br/>  name: String<br/>}"]
        coop_hen["type Hen {<br/>  id: ID!<br/>  name: String<br/>}"]
    end

    subgraph farm_schema["farm.graphql (Farm's view)"]
        direction TB
        farm_type["type Farm {<br/>  id: ID!<br/>  name: String<br/>  coops: [Coop]<br/>}"]
        farm_coop["type Coop {<br/>  id: ID!<br/>  name: String<br/>}"]
    end

    style coop_type fill:#4a9eff,stroke:#333,color:#fff
    style coop_farm fill:#e2e8f0,stroke:#333
    style coop_hen fill:#e2e8f0,stroke:#333
    style farm_type fill:#34d399,stroke:#333,color:#fff
    style farm_coop fill:#e2e8f0,stroke:#333
```

The blue/green boxes are **canonical types** — owned by that schema. The gray boxes are **projections** — the minimum the consuming service needs to know.

This is **not duplication**. It's deliberate. Each meshobj declares:
- "I own `Coop` with all its fields"
- "I need `Farm` with just `id` and `name`"
- "I need `Hen` with just `id` and `name`"

If the Farm team adds 10 new fields, the Coop schema doesn't change. If the Farm team removes a field that Coop uses, the break is localized to Coop's schema — not a shared type library that breaks everyone.

This is the **consumer-driven contract** pattern from *Architecture: The Hard Parts*.

---

## The Federation Workflow

In practice, federation follows a team workflow:

```mermaid
graph TB
    A["1. Planning Meeting<br/>Define entity interfaces"] --> B["2. Publish Schemas<br/>Each team publishes<br/>GraphQL + JSON schema"]
    B --> C["3. Integrate<br/>Other teams create<br/>projections of your types"]
    C --> D["4. Configure Resolvers<br/>Wire federation<br/>in RootConfig"]
    D --> E["5. Deploy<br/>Same JVM or separate —<br/>your choice"]

    style A fill:#4a9eff,stroke:#333,color:#fff
    style B fill:#818cf8,stroke:#333,color:#fff
    style C fill:#34d399,stroke:#333,color:#fff
    style D fill:#fbbf24,stroke:#333,color:#000
    style E fill:#f87171,stroke:#333,color:#fff
```

1. **Planning meeting**: Teams agree on entity boundaries and interfaces
2. **Schema publication**: Each team publishes their canonical GraphQL and JSON schemas
3. **Integration**: Other teams define slim projections of your types in their schemas
4. **Configuration**: Resolvers wired in code — explicit, visible, no magic
5. **Deployment**: Start co-located, split later when needed
