---
title: Tuning
layout: default
parent: Architecture
nav_order: 5
---

# Tuning SQLite for MeshQL

MeshQL stores JSON payloads in SQLite and queries them with `json_extract()` expressions. Without indexes on these expressions, every filtered query performs a full table scan. This page shows how expression indexes eliminate that bottleneck and close the gap between SQLite and MongoDB.

---

## The Problem: Unindexed JSON Queries

MeshQL's GraphQL resolvers generate WHERE clauses like:

```sql
SELECT * FROM coop
WHERE json_extract(payload, '$.farm_id') = '{{id}}'
AND deleted = 0
ORDER BY created_at DESC
```

Without an index on `json_extract(payload, '$.farm_id')`, SQLite scans every row in the `coop` table. This is invisible at small data volumes but becomes the dominant cost as tables grow and as federation chains multiply queries.

### Why It Matters for Federation

A depth-3 federation query like `farm { coops { hens } }` executes:

1. `getById` on `farm` — indexed on `id` column (fast)
2. `getByFarm` on `coop` — `json_extract(payload, '$.farm_id')` (full scan)
3. `getByCoop` on `hen` — `json_extract(payload, '$.coop_id')` (full scan per coop)

Steps 2 and 3 are the bottleneck. With 6 coops, step 3 runs 6 times. Each full scan compounds.

---

## The Fix: Expression Indexes

SQLite supports indexes on expressions, not just columns. MeshQL's `SQLiteConfig` accepts a list of JSON paths to index:

```java
// Before: no indexes on JSON fields
SQLiteConfig coopDB = new SQLiteConfig(actorsDb, "coop");

// After: index the fields used in WHERE clauses
SQLiteConfig coopDB = new SQLiteConfig(actorsDb, "coop", List.of("$.farm_id"));
```

This generates:

```sql
CREATE INDEX IF NOT EXISTS idx_coop_farm_id
ON coop(json_extract(payload, '$.farm_id'))
```

SQLite's query planner uses this index for any query that filters on `json_extract(payload, '$.farm_id')`, turning full scans into B-tree lookups.

### Which Fields to Index

Index fields that appear in GraphQL query WHERE clauses — these are the fields used in `RootConfig` vector queries:

```java
// RootConfig defines the queries:
.vector("getByFarm", "json_extract(payload, '$.farm_id') = '{{id}}'")

// SQLiteConfig should index the same field:
new SQLiteConfig(actorsDb, "coop", List.of("$.farm_id"))
```

The rule is simple: **if a field appears in a vector query predicate, index it.**

### Full Egg-Economy Index Map

| Entity | Indexed Fields | Used By |
|:-------|:---------------|:--------|
| farm | `$.zone` | `getByZone` |
| coop | `$.farm_id` | `getByFarm` |
| hen | `$.coop_id` | `getByCoop` |
| container | `$.zone` | `getByZone` |
| consumer | `$.zone` | `getByZone` |
| lay_report | `$.hen_id`, `$.farm_id` | `getByHen`, `getByFarm` |
| storage_deposit | `$.container_id` | `getByContainer` |
| storage_withdrawal | `$.container_id` | `getByContainer` |
| container_transfer | `$.source_container_id`, `$.dest_container_id` | `getBySourceContainer`, `getByDestContainer` |
| consumption_report | `$.consumer_id`, `$.container_id` | `getByConsumer`, `getByContainer` |
| container_inventory | `$.container_id` | `getByContainer` |
| hen_productivity | `$.hen_id` | `getByHen` |
| farm_output | `$.farm_id` | `getByFarm` |

---

## Benchmark Results

All measurements use the same k6 test suite, same hardware, same 13-entity API. The only difference is whether `SQLiteConfig` includes indexed fields.

### Single Client (1 VU, Smoke Profile)

#### GraphQL Query Latency (p95, milliseconds)

```
getById
  Before   ████▋                            4.6 ms
  After    ███                               3.0 ms

getAll
  Before   ████████▍                        8.4 ms
  After    █████                             5.0 ms

Filtered (getByZone, getByFarm)
  Before   ████                              4.0 ms
  After    ███                               3.0 ms
```

#### Federation Depth (p95, milliseconds)

```
Depth 2 (farm → coops)
  Before   ██████████████████████▍         22.4 ms
  After    ███████▍                          7.4 ms       3.0x faster

Depth 3 (farm → coops → hens)
  Before   ████████████████████████████████ 32.0 ms  (extrapolated)
  After    █████████████                    13.1 ms       2.4x faster

Depth 3+parallel
  Before   ████████████████████████████████ 32.0 ms  (extrapolated)
  After    ██████████████▏                  14.2 ms       2.3x faster
```

{: .tip }
> Federation sees the largest improvement because each resolver level multiplies the number of filtered queries. An index that saves 1ms per query saves N ms at depth 2 (one query per parent) and N×M ms at depth 3.

#### Overall HTTP p95 (milliseconds)

| Suite | Before Indexes | After Indexes | Improvement |
|:------|---------------:|--------------:|:------------|
| REST CRUD | 17.5 | 16.7 | 5% |
| GraphQL Queries | 16.4 | 16.5 | — |
| Federation | 22.4 | 17.1 | 24% |
| Mixed Workload | 16.2 | 17.2 | — |

{: .note }
> REST CRUD and overall HTTP p95 show minimal change because they include write operations and `getById` queries (which were already indexed on the `id` column). The impact of expression indexes is concentrated in filtered reads and federation chains.

### Under Load (10 VUs, Load Profile)

This is where indexes transform SQLite from "struggling" to "competitive."

#### GraphQL Queries at 10 VUs

| Metric | Before Indexes | After Indexes | Improvement |
|:-------|---------------:|--------------:|:------------|
| **p95** | 16.5 ms | **3.2 ms** | **5.2x faster** |
| **avg** | 8.6 ms | **1.7 ms** | **5.1x faster** |
| **throughput** | 74.8 req/s | **110.7 req/s** | **1.5x higher** |

```
GraphQL p95 at 10 VUs
  Before   ████████████████▌              16.5 ms
  After    ███▏                            3.2 ms
```

#### Federation at 10 VUs

| Depth | Before p95 | After p95 | Improvement |
|:------|----------:|---------:|:------------|
| Depth 2 | 7.4 ms | **5.0 ms** | 1.5x faster |
| Depth 3 | 13.1 ms | **6.0 ms** | 2.2x faster |
| Depth 3+parallel | 14.2 ms | **7.0 ms** | 2.0x faster |

```
Federation Depth 3 p95 at 10 VUs
  Before   █████████████▏                 13.1 ms
  After    ██████                           6.0 ms
```

{: .architecture }
> With indexes, SQLite federation at 10 VUs (6ms at depth 3) is faster than MongoDB's federation at 1 VU (13.4ms at depth 3). The in-process resolver advantage plus indexed lookups beats network-separated queries.

---

## SQLite vs MongoDB After Tuning

With expression indexes, the picture changes significantly:

### Single Client Comparison

| Metric | SQLite (indexed) | MongoDB | Gap |
|:-------|-----------------:|--------:|:----|
| GraphQL getById p95 | 3.0 ms | 4.2 ms | SQLite wins |
| GraphQL getAll p95 | 5.0 ms | 8.0 ms | SQLite wins |
| GraphQL filtered p95 | 3.0 ms | 5.0 ms | SQLite wins |
| Federation depth 2 p95 | 7.4 ms | 4.2 ms | MongoDB wins |
| Federation depth 3 p95 | 13.1 ms | 13.4 ms | Tied |

{: .tip }
> After indexing, SQLite matches or beats MongoDB for individual query latency at 1 VU. SQLite's in-process advantage means zero network overhead per query. The remaining gap in aggregate HTTP metrics (p95 ~17ms) comes from write operations hitting SQLite's single-writer lock.

### 10 VUs Comparison

| Metric | SQLite (indexed) | MongoDB |
|:-------|-----------------:|--------:|
| GraphQL p95 | 3.2 ms | 8.7 ms |
| Federation depth 3 p95 | 6.0 ms | 13.4 ms |
| REST CRUD p95 | 83.6 ms | 8.7 ms |
| REST CRUD error rate | 1.39% | 0.96% |

The divergence is clear: **SQLite reads are faster, SQLite writes are slower.** GraphQL (read-only) and federation (read-only) are dominated by indexed lookups where SQLite's in-process advantage shines. REST CRUD includes writes, which hit the single-writer lock under concurrency.

---

## WAL Mode

MeshQL enables WAL (Write-Ahead Logging) mode automatically when creating SQLite connections:

```java
stmt.execute("PRAGMA journal_mode=WAL");
```

WAL mode allows concurrent readers while a write is in progress. Without WAL, readers would block on writes too, making concurrency even worse. This is already enabled by default — no tuning needed.

---

## Recommendations

| Scenario | Action |
|:---------|:-------|
| **Any SQLite deployment** | Always specify indexed fields in `SQLiteConfig` |
| **Read-heavy workload** | SQLite with indexes matches MongoDB for single-user and small-team use |
| **Write-heavy or concurrent** | Move to MongoDB — SQLite's single-writer lock is the bottleneck |
| **Deep federation chains** | SQLite is optimal — in-process resolvers avoid per-hop network cost |
| **Edge / embedded** | SQLite with indexes — competitive performance with zero infrastructure |

{: .architecture }
> Expression indexes are not optional. They are the difference between SQLite performing 5x worse than MongoDB and SQLite matching or beating it. Always index the JSON fields used in your vector query predicates.
