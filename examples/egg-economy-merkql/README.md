# Egg Economy with merkql + SQLite

Same 13-entity egg economy domain as `egg-economy`, but with **zero infrastructure dependencies**. No MongoDB, no Kafka, no Debezium, no Docker containers for data.

## Architecture

```
Client --> REST POST --> Java MeshQL (SQLite) <-- GraphQL <-- Client
                              | writes to events.db
                              v
                  Rust CDC Poller (reads events.db via WAL)
                              | publishes to merkql
                              v
                  merkql (on-disk event log)
                              | consumer reads
                              v
                  Rust Projection Processor
                              | HTTP calls
                              v
                  Java MeshQL (SQLite) --> updates projections.db
```

Two processes, three SQLite files, one merkql data directory.

## Data Layout

| File | Contents | Access |
|------|----------|--------|
| `data/actors.db` | farm, coop, hen, container, consumer | Java R/W |
| `data/events.db` | lay_report, storage_deposit, storage_withdrawal, container_transfer, consumption_report | Java R/W, Rust R/O |
| `data/projections.db` | container_inventory, hen_productivity, farm_output | Java R/W |
| `data/merkql/` | merkql event log (topics, consumer groups) | Rust R/W |

SQLite WAL mode enables concurrent Java writes + Rust reads on `events.db`.

## Build

```bash
# Java server
mvn package -pl examples/egg-economy-merkql -am -DskipTests

# Rust processor
cargo build --manifest-path examples/egg-economy-merkql/processor/Cargo.toml
```

## Run

Start the Java server first, then the Rust processor.

```bash
# Terminal 1: Java MeshQL server
cd examples/egg-economy-merkql
java -jar target/egg-economy-merkql-example-0.2.0.jar

# Terminal 2: Rust CDC processor
cd examples/egg-economy-merkql
./processor/target/debug/egg-economy-processor
```

### Processor CLI Options

```
--meshql-url <URL>        MeshQL server URL (default: http://localhost:5088)
--events-db <PATH>        Path to events.db (default: data/events.db)
--merkql-dir <PATH>       Path to merkql data directory (default: data/merkql)
--poll-interval-ms <MS>   CDC poll interval (default: 500)
--replay                  Reset watermarks and re-process all events
```

## How It Works

1. **Write actors** via REST: `POST /farm/api`, `POST /hen/api`, etc.
2. **Write events** via REST: `POST /lay_report/api`, `POST /storage_deposit/api`, etc.
3. **Rust polls** `events.db` for new rows (using `_id` watermark), publishes to merkql topics
4. **Rust consumes** from merkql, routes events to projection updaters
5. **Projection updaters** read current state via GraphQL, compute deltas, write back via REST
6. **Query projections** via GraphQL: `POST /hen_productivity/graph`, `POST /farm_output/graph`, etc.

### CDC Mechanism

MeshQL's SQLite tables have `_id INTEGER PRIMARY KEY AUTOINCREMENT` -- a monotonically increasing log position. The Rust poller queries `WHERE _id > ? ORDER BY _id ASC` to get new rows since the last watermark. Watermarks are persisted to `data/merkql/watermarks.json`.

## Comparison with egg-economy

| Aspect | egg-economy | egg-economy-merkql |
|--------|-------------|-------------------|
| Storage | MongoDB (3 shards) | SQLite (3 files) |
| CDC | Debezium + Kafka | Rust poller + merkql |
| Projection processor | Java (in-process Kafka consumer) | Rust (separate process) |
| Infrastructure | Docker Compose: 3 MongoDB + Kafka + Debezium | None |
| Query syntax | `{"payload.field": "{{id}}"}` | `json_extract(payload, '$.field') = '{{id}}'` |
| Event processing | Same | Same |
| GraphQL federation | 19 internal resolvers | 19 internal resolvers |
| REST API | 13 restlettes | 13 restlettes |

## Entities

### Actors (5)
farm, coop, hen, container, consumer

### Events (5)
lay_report, storage_deposit, storage_withdrawal, container_transfer, consumption_report

### Projections (3)
container_inventory, hen_productivity, farm_output
