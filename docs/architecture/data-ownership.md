---
title: Data Ownership
layout: default
parent: Architecture
nav_order: 1
---

# Data Ownership

The hardest problem in distributed architecture isn't communication — it's data. Who owns it? Who can change it? What happens when two services need the same data?

MeshQL answers these questions at the framework level through the **Envelope** pattern.

---

## The Envelope

Every document in MeshQL is wrapped in an Envelope:

```mermaid
graph TB
    subgraph envelope["Envelope"]
        direction TB
        id["id: 'hen-42'"]
        payload["payload: { name: 'Henrietta', eggs: 3 }"]
        created["createdAt: 2024-01-15T10:30:00Z"]
        deleted["deleted: false"]
        tokens["authorizedTokens: ['farmer-bob', 'vet-alice']"]
    end

    style id fill:#4a9eff,stroke:#333,color:#fff
    style payload fill:#34d399,stroke:#333,color:#fff
    style created fill:#818cf8,stroke:#333,color:#fff
    style deleted fill:#fbbf24,stroke:#333,color:#000
    style tokens fill:#f472b6,stroke:#333,color:#fff
```

```java
public record Envelope(
    String id,                      // Document identity
    Stash payload,                  // Your data — opaque to the framework
    Instant createdAt,              // Version timestamp
    boolean deleted,                // Soft delete flag
    List<String> authorizedTokens   // Who can access this document
)
```

The Envelope enforces three architectural properties:

### 1. Ownership Boundary

The `payload` is opaque to MeshQL. Only the owning meshobj interprets its contents. The framework handles metadata (identity, versioning, authorization) — your business data is yours.

### 2. Authorization at the Data Level

Access control tokens live **on the document**, not in a centralized service:

```mermaid
sequenceDiagram
    participant Client
    participant Auth
    participant Repository
    participant Storage

    Client->>Auth: Request with Bearer token
    Auth->>Auth: Extract credentials<br/>(e.g., JWT sub claim)
    Auth-->>Repository: credentials: ["user-123"]
    Repository->>Storage: Query with token filter
    Storage-->>Repository: Only documents where<br/>authorizedTokens contains "user-123"
    Repository-->>Client: Filtered results
```

This means:
- No separate authorization database to maintain
- Each document controls its own access
- Authorization travels with the data, even across service boundaries
- Different documents in the same collection can have different access rules

### 3. Immutable Version History

Every write creates a new version. Deletes are soft (the `deleted` flag). This gives you:

```mermaid
graph LR
    subgraph "Document: hen-42"
        direction TB
        v1["v1: { name: 'Hen' }<br/>created: 10:00"]
        v2["v2: { name: 'Henrietta' }<br/>created: 11:00"]
        v3["v3: { name: 'Henrietta', eggs: 3 }<br/>created: 14:00"]
    end

    q1["read(hen-42)"] --> v3
    q2["read(hen-42, at=11:30)"] --> v2
    q3["read(hen-42, at=10:30)"] --> v1

    style v1 fill:#e2e8f0,stroke:#333
    style v2 fill:#cbd5e1,stroke:#333
    style v3 fill:#94a3b8,stroke:#333,color:#fff
    style q1 fill:#34d399,stroke:#333,color:#fff
    style q2 fill:#fbbf24,stroke:#333,color:#000
    style q3 fill:#f87171,stroke:#333,color:#fff
```

- **Point-in-time queries** — read data as of any timestamp
- **Audit trails** — built into the storage layer, not bolted on
- **No lost data** — soft deletes preserve history

---

## Database Per Entity

Each meshobj stores its data in its own collection or table. There is no shared storage:

```mermaid
graph TB
    subgraph "MeshQL Server"
        farm[Farm Meshobj]
        coop[Coop Meshobj]
        hen[Hen Meshobj]
    end

    farm --> farmdb[("farms collection")]
    coop --> coopdb[("coops collection")]
    hen --> hendb[("hens collection")]

    style farm fill:#4a9eff,stroke:#333,color:#fff
    style coop fill:#4a9eff,stroke:#333,color:#fff
    style hen fill:#4a9eff,stroke:#333,color:#fff
    style farmdb fill:#fbbf24,stroke:#333,color:#000
    style coopdb fill:#fbbf24,stroke:#333,color:#000
    style hendb fill:#fbbf24,stroke:#333,color:#000
```

This is the **database-per-service** pattern from *Architecture: The Hard Parts*. Each entity:

- Has its own storage (collection, table, or in-memory store)
- Can use a different storage backend than its neighbors
- Manages its own schema evolution
- Cannot access another entity's data directly — only through federation

---

## Polyglot Persistence

Because data ownership is enforced through the Plugin interface, different entities can use different storage backends:

```mermaid
graph LR
    subgraph "Same MeshQL Server"
        users["Users Meshobj"]
        sessions["Sessions Meshobj"]
        audit["Audit Meshobj"]
        cache["Cache Meshobj"]
    end

    users --> pg[("PostgreSQL<br/>ACID transactions")]
    sessions --> mongo[("MongoDB<br/>Flexible schema")]
    audit --> sqlite[("SQLite<br/>Edge deployment")]
    cache --> mem[("In-Memory<br/>Zero latency")]

    style users fill:#4a9eff,stroke:#333,color:#fff
    style sessions fill:#4a9eff,stroke:#333,color:#fff
    style audit fill:#4a9eff,stroke:#333,color:#fff
    style cache fill:#4a9eff,stroke:#333,color:#fff
    style pg fill:#818cf8,stroke:#333,color:#fff
    style mongo fill:#34d399,stroke:#333,color:#fff
    style sqlite fill:#fbbf24,stroke:#333,color:#000
    style mem fill:#f472b6,stroke:#333,color:#fff
```

A **certification test suite** (Cucumber BDD) guarantees that all backends behave identically:

- Same CRUD semantics
- Same temporal versioning
- Same authorization filtering
- Same batch operations

Swap PostgreSQL for MongoDB without changing a line of application code. The certification suite proves it works.

---

## What This Means in Practice

| Concern | How MeshQL Handles It |
|:--------|:---------------------|
| **Who owns the data?** | The meshobj that defines the schema and storage config |
| **Who can read it?** | Determined by `authorizedTokens` on each document |
| **How do others access it?** | Through GraphQL federation — never through shared storage |
| **What about historical data?** | Built-in temporal versioning — every backend, automatically |
| **What about different storage needs?** | Polyglot persistence — choose the right backend per entity |
| **How do I know backends are compatible?** | Certification test suite guarantees identical behavior |
