---
title: Storage Backends
layout: default
parent: Reference
nav_order: 4
---

# Storage Backends

MeshQL supports four storage backends. All are certified against the same test suite to guarantee identical behavior.

---

## Backend Comparison

| Feature | MongoDB | PostgreSQL | SQLite | In-Memory |
|:--------|:--------|:-----------|:-------|:----------|
| **Use case** | General purpose, flexible schema | ACID transactions, complex queries | Edge deployment, embedded | Testing, caching |
| **Payload storage** | BSON subdocument | JSONB column | TEXT (JSON) | Java object |
| **Auth token storage** | Array in document | Separate relation table | JSON array column | List in Envelope |
| **Temporal precision** | Milliseconds | Microseconds | Milliseconds | Nanoseconds |
| **Connection pooling** | Built-in (driver) | HikariCP | Per-file | N/A |
| **Indexing** | MongoDB indexes | PostgreSQL indexes | SQLite indexes | N/A |

---

## MongoDB

Best for: flexible schemas, document-oriented data, rapid iteration.

### Configuration

```java
MongoConfig config = new MongoConfig();
config.uri = "mongodb://localhost:27017";
config.db = "myapp";
config.collection = "items";
```

### How It Stores Data

Each Envelope becomes a MongoDB document:

```json
{
  "_id": ObjectId("..."),
  "id": "item-42",
  "payload": {
    "name": "Widget",
    "category": "tools"
  },
  "created_at": ISODate("2024-01-15T10:30:00Z"),
  "deleted": false,
  "authorizedTokens": ["user-123", "admin"]
}
```

### Temporal Queries

Uses aggregation pipeline: `$group` by `id`, `$first` by `createdAt DESC`, filtered by `$lte` timestamp.

### Indexing

Always index foreign key fields used in vector queries:

```javascript
db.items.createIndex({'payload.category': 1});
db.items.createIndex({'payload.author_id': 1});
```

This provides ~100x improvement over unindexed queries.

---

## PostgreSQL

Best for: ACID guarantees, complex relational queries, enterprise environments.

### Configuration

```java
PostgresConfig config = new PostgresConfig();
config.uri = "jdbc:postgresql://localhost:5432/myapp";
config.table = "items";
config.username = "user";
config.password = "pass";
```

### How It Stores Data

Two tables per entity:

**Main table** (`items`):

| id | payload | created_at | deleted |
|:---|:--------|:-----------|:--------|
| item-42 | `{"name": "Widget"}` | 2024-01-15 10:30:00 | false |

**Auth tokens table** (`items_authtokens`):

| envelope_id | envelope_created_at | token | token_order |
|:------------|:-------------------|:------|:------------|
| item-42 | 2024-01-15 10:30:00 | user-123 | 0 |
| item-42 | 2024-01-15 10:30:00 | admin | 1 |

### Connection Pooling

Uses HikariCP with defaults:
- Max pool size: 10
- Min idle: 2

### Temporal Queries

Uses `DISTINCT ON (id) ORDER BY created_at DESC` with `WHERE created_at <= ?` filter.

### Write Consistency

Transactions with automatic retry on unique constraint violations (error code 23505).

---

## SQLite

Best for: embedded applications, edge deployment, single-user scenarios.

### Configuration

```java
SQLiteConfig config = new SQLiteConfig();
// File-based or in-memory (:memory:)
```

### How It Stores Data

Single table per entity:

```sql
CREATE TABLE items (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    id TEXT,
    payload TEXT,           -- JSON string
    created_at INTEGER,     -- Milliseconds
    deleted INTEGER,        -- 0 or 1
    authorized_tokens TEXT, -- JSON array
    UNIQUE (id, created_at)
);
```

### Auth Token Queries

Uses SQLite's `json_each()` function:

```sql
WHERE EXISTS (
    SELECT 1 FROM json_each(authorized_tokens)
    WHERE value IN (?, ?)
)
```

---

## In-Memory

Best for: unit testing, caching, prototyping.

### Configuration

No external configuration needed. Uses `ConcurrentHashMap` internally.

### How It Stores Data

```
Map<String, List<Envelope>>
  "item-42" → [v1, v2, v3]  (version history)
  "item-43" → [v1]
```

### Behavior

- Thread-safe via `ConcurrentHashMap.compute()`
- Full version history maintained
- Authorization filtering via Java streams
- No persistence — data lost on restart

---

## Certification Suite

All backends pass the same Cucumber BDD test suite:

### Repository Certification (9 scenarios)
- Create and read a document
- Create and list documents
- Create and remove (soft delete)
- Read returns empty for non-existent IDs
- Multiple versions of same ID
- Read at specific timestamp
- Batch create (createMany)
- Batch read (readMany)
- Batch remove (removeMany)

### Searcher Certification (8 scenarios)
- Find by ID
- Find by name
- Find all by type
- Find by name and type
- Authorization filtering
- Temporal queries
- Non-existent IDs return null
- Empty results for no matches

This suite guarantees that **swapping one backend for another produces identical application behavior**.
