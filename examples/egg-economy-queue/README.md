# egg-economy-queue: Zero-Infrastructure Egg Economy

Same 13-entity domain model as [egg-economy](../egg-economy) (farms, coops, hens, eggs, containers, consumers, events, projections) with the same REST + GraphQL APIs — but **zero external infrastructure**. No databases, no message brokers, no CDC connectors.

| | egg-economy | egg-economy-queue |
|:---|:---|:---|
| **Storage** | 3x MongoDB (sharded replicas) | MerkSQL (embedded, JNI) |
| **Events** | Kafka + Debezium CDC | Direct REST (no pipeline) |
| **Docker services** | 10 (3x Mongo, Kafka, Debezium, app, 3x frontend, nginx) | 5 (app, 3x frontend, nginx) |
| **External dependencies** | MongoDB, Kafka, Debezium | None |
| **Startup time** | ~90s (containers + CDC init) | Instant |
| **Data persistence** | MongoDB volumes | Local directory |

## Architecture

```
                    ┌─────────────────────────────┐
                    │     MeshQL Server (JVM)      │
                    │                              │
                    │  13 Restlettes (REST CRUD)   │
                    │  13 Graphlettes (GraphQL)    │
                    │  19 Internal Resolvers       │
                    │                              │
                    │  ┌─────────────────────────┐ │
                    │  │   MerkSqlPlugin (JNI)   │ │
                    │  │                         │ │
                    │  │  farm broker ──► ./data/farm/
                    │  │  coop broker ──► ./data/coop/
                    │  │  hen broker  ──► ./data/hen/
                    │  │  ... (13 independent brokers)
                    │  └─────────────────────────┘ │
                    └─────────────────────────────┘
```

### Per-Topic Broker Isolation

Each entity type gets its own Rust broker via JNI. A write to `farm` never blocks a write to `hen`. This is the key design decision that makes MerkSQL competitive with MongoDB under concurrent load:

- **13 independent brokers** — one per entity type (farm, coop, hen, container, consumer, lay_report, storage_deposit, storage_withdrawal, container_transfer, consumption_report, container_inventory, hen_productivity, farm_output)
- **Write contention is per-entity-type**, not global — with realistic traffic spread across entity types, actual contention is low
- **Reads are always non-blocking** — the ConcurrentHashMap allows lock-free reads from any thread

### Query Translation

MerkSQL queries use simple `field = 'value'` syntax instead of MongoDB's JSON:

| Query | MongoDB (egg-economy) | MerkSQL (egg-economy-queue) |
|:------|:----------------------|:----------------------------|
| getById | `{"id": "{{id}}"}` | `id = '{{id}}'` |
| getAll | `{}` | `` (empty string) |
| getByFarm | `{"payload.farm_id": "{{id}}"}` | `farm_id = '{{id}}'` |

No `payload.` prefix needed — MerkSQL stores and queries payload fields directly.

## Performance

All benchmarks run with [k6](https://k6.io/) against the same 13-entity API with 19 internal resolvers.

### Baseline (1 VU, smoke profile)

| Suite | p95 | avg | throughput |
|:------|----:|----:|-----------:|
| REST CRUD | 4.5 ms | 2.1 ms | 30 req/s |
| GraphQL queries | 2.0 ms | 1.0 ms | 191 req/s |
| Federation depth | 1.2 ms | 1.1 ms | 275 req/s |
| Mixed workload | 1.2 ms | 1.3 ms | 133 req/s |

### Under Load (10 VUs, load profile)

| Metric | MerkSQL | MongoDB | SQLite |
|:-------|--------:|--------:|-------:|
| **p95** | **11.8 ms** | 8.7 ms | 82.6 ms |
| **avg** | 5.6 ms | 3.1 ms | 24.6 ms |
| **max** | 40.0 ms | 48.1 ms | 257.2 ms |
| **throughput** | 95 req/s | 159.4 req/s | 96.5 req/s |
| **error rate** | **0%** | 0.96% | 1.19% |

MerkSQL's p95 under load (11.8ms) is competitive with MongoDB (8.7ms) — remarkable for an embedded store with zero infrastructure. The 0% error rate at all concurrency levels means no requests are dropped under load.

### The Per-Topic Broker Effect

Before per-topic isolation (single shared broker), MerkSQL's 10 VU p95 was **88.3ms** — all 13 entity types contended on one write lock. After giving each entity its own broker:

| Metric | Shared broker | Per-topic broker | Improvement |
|:-------|-------------:|------------------:|:------------|
| p95 | 88.3 ms | **11.8 ms** | **7.5x** |
| max | 312.4 ms | **40.0 ms** | **7.8x** |
| avg | 9.8 ms | **5.6 ms** | **1.8x** |

### Federation Depth (avg, 1 VU)

| Depth | What it resolves | Latency |
|:------|:-----------------|--------:|
| 2 | farm -> coops | 4.3 ms |
| 3 | farm -> coops -> hens | 25.1 ms |
| 3+parallel | depth 3 + farmOutput | 21.3 ms |

Depth-2 federation is the fastest of any backend (4.3ms) because the entire resolver chain stays in-process. Deeper chains grow with data volume due to linear scan (no B-tree indexes).

## Running Locally

```bash
# Build
mvn package -pl examples/egg-economy-queue -am -Dmaven.test.skip=true

# Run (point to config and native library)
CONFIG_DIR=examples/egg-economy-queue/config \
DATA_DIR=/tmp/egg-queue-data \
java --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=/path/to/merksql-jni/target/release \
  -jar examples/egg-economy-queue/target/egg-economy-queue-example-0.2.0.jar

# Seed data
./examples/egg-economy-queue/scripts/seed.sh http://localhost:5088

# Verify
curl -s http://localhost:5088/farm/graph \
  -H 'Content-Type: application/json' \
  -d '{"query": "{ getAll { id name zone } }"}' | jq
```

### Environment Variables

| Variable | Default | Description |
|:---------|:--------|:------------|
| `PORT` | `5088` | HTTP server port |
| `DATA_DIR` | `./data` | Directory for MerkSQL broker data (one subdirectory per entity) |
| `CONFIG_DIR` | `/app/config` | Directory containing `graph/` and `json/` schema files |

## Running with Docker Compose

```bash
cd examples/egg-economy-queue
docker compose up --build

# Seed
./scripts/seed.sh http://localhost:5088

# Frontends (via nginx)
# Dashboard:   http://localhost:8088/dashboard/
# Homesteader: http://localhost:8088/homestead/
# Corporate:   http://localhost:8088/corporate/
```

5 containers total. No database containers, no message broker containers.

## Upgrade Path

When you outgrow MerkSQL, the upgrade is a configuration change — not a rewrite:

1. **MerkSQL -> SQLite**: Swap `MerkSqlPlugin` for `SqlitePlugin`. Same jar, same API.
2. **SQLite -> MongoDB**: Swap for `MongoPlugin`, add connection URIs. Same jar, same API.
3. **MongoDB -> Full enterprise**: Add Kafka + Debezium for CDC. The clean API doesn't slow down.

The upgrade trigger is write contention: when concurrent writes to the **same entity type** cause unacceptable tail latency, graduate to a backend with connection pooling. With 13 entity types and realistic traffic patterns, MerkSQL handles more concurrency than you'd expect.

## Shared Assets

Config files and frontend apps are storage-agnostic — symlinked from egg-economy to avoid duplication:

- `config/graph/` -> `../egg-economy/config/graph/` (13 GraphQL schemas)
- `config/json/` -> `../egg-economy/config/json/` (13 JSON schemas)
- `homesteader-app/` -> `../egg-economy/homesteader-app/`
- `corporate-app/` -> `../egg-economy/corporate-app/`
- `dashboard-app/` -> `../egg-economy/dashboard-app/`

## Projections

Unlike egg-economy (which uses Debezium CDC to automatically materialize projections), egg-economy-queue computes projections in the seed script and POSTs them directly via REST. The projection entities (hen_productivity, farm_output, container_inventory) are regular MerkSQL-backed restlettes — they just happen to store computed data rather than source data.

For production use, you'd compute projections in your application logic after writing events, or use MerkSQL's streaming query capabilities.
