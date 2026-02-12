# Event Processing Pipeline

A CDC-powered event processing system demonstrating MeshQL with Debezium and Kafka. Raw events enter via REST, flow through a change data capture pipeline, get enriched by a processor, and land as queryable processed events — all with GraphQL federation linking raw and processed events together.

## What It Does

```
POST /event/api ──► MongoDB ──► Debezium CDC ──► Kafka ──► Processor ──► POST /processedevent/api
                                                                              │
                                                                              ▼
                                                          Query via GraphQL or REST
```

1. **Create** a raw event via the REST API
2. **Debezium** captures the MongoDB change stream and publishes to Kafka
3. **Kafka** holds the raw event message
4. **Processor** (Java consumer) reads the message, enriches it with metadata, and POSTs the result back to MeshQL
5. **Query** the processed event via REST or GraphQL — with federation back to the original raw event

The entire pipeline runs in one `docker compose up`.

## Quick Start

```bash
cd examples/events
docker compose up
```

Wait ~30 seconds for all services to initialize (MongoDB replica set, Kafka, Debezium, MeshQL). Then:

```bash
# Create a raw event
curl -s -X POST http://localhost:4055/event/api \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user_login",
    "data": "{\"user_id\": \"u-42\", \"ip\": \"192.168.1.1\"}",
    "source": "auth-service",
    "version": "1.0",
    "timestamp": "2026-01-15T10:00:00Z",
    "correlationId": "req-abc-123"
  }'

# Wait a few seconds for CDC pipeline to process...
sleep 5

# Query processed events via GraphQL
curl -s -X POST http://localhost:4055/processedevent/graph \
  -H "Content-Type: application/json" \
  -d '{"query": "{ getByName(name: \"user_login\") { id name status processing_time_ms processed_data rawEvent { id name source } } }"}' | jq
```

## Endpoints

| Endpoint | Type | URL |
|:---------|:-----|:----|
| Raw Events | REST | http://localhost:4055/event/api |
| Raw Events | GraphQL | http://localhost:4055/event/graph |
| Processed Events | REST | http://localhost:4055/processedevent/api |
| Processed Events | GraphQL | http://localhost:4055/processedevent/graph |
| Health | HTTP | http://localhost:4055/health |

## Domain Model

### Event (Raw)

| Field | Type | Required | Description |
|:------|:-----|:---------|:------------|
| `name` | String | Yes | Event type (e.g., `user_login`, `order_created`) |
| `data` | String | Yes | JSON-encoded payload |
| `timestamp` | ISO 8601 | Yes | When the event occurred |
| `source` | String | No | Originating system |
| `version` | String | No | Schema version |
| `correlationId` | String | No | Tracing ID for related events |

### ProcessedEvent

| Field | Type | Required | Description |
|:------|:-----|:---------|:------------|
| `raw_event_id` | String | Yes | Reference to original event |
| `name` | String | Yes | Inherited from raw event |
| `processed_data` | String | Yes | JSON with enriched data |
| `processed_timestamp` | ISO 8601 | Yes | When processing completed |
| `status` | Enum | Yes | `SUCCESS`, `FAILED`, or `PARTIAL` |
| `processing_time_ms` | Float | No | Processing duration |
| `correlationId` | String | No | Inherited from raw event |
| `error_message` | String | No | Error details if failed |

### Federation

| Source | Field | Type | Target |
|:-------|:------|:-----|:-------|
| Event | `processedEvents` | Vector | `/processedevent/graph` via `getByRawEventId` |
| ProcessedEvent | `rawEvent` | Singleton | `/event/graph` via `getById` |

Query a raw event and get its processed versions:

```graphql
{
  getByName(name: "user_login") {
    id name source timestamp
    processedEvents {
      id status processing_time_ms processed_data
    }
  }
}
```

Or start from a processed event and trace back to the original:

```graphql
{
  getById(id: "processed-event-id") {
    id name status processed_data
    rawEvent {
      id name source data timestamp
    }
  }
}
```

## How the Pipeline Works

### Debezium CDC

Debezium connects to MongoDB's replica set change streams (not the legacy oplog). When a document is inserted into the `event` collection, Debezium captures the change and publishes the full document to a Kafka topic.

**Topic naming**: `{prefix}.{database}.{collection}` — e.g., `events.events_development.event`

Debezium is configured via environment variables in `docker-compose.yml` and a properties file. Key settings:

- Capture mode: `change_streams` (MongoDB 3.6+)
- Publish mode: full document only (not deltas)
- Sink: Kafka with string serialization

### Kafka

Runs in KRaft mode (no ZooKeeper). Single-broker setup for development. Topics are auto-created.

### Processor (`RawToProcessedProcessor.java`)

A Kafka consumer thread that:

1. Polls the raw events topic
2. Parses the Debezium envelope (extracts `payload.after` — double-encoded JSON)
3. Extracts the document ID (handles both UUID and MongoDB ObjectId formats)
4. Enriches the data with processing metadata:
   - `enriched: true`
   - `processed_at` timestamp
   - `processor_version: 1.0.0`
   - `processing_time_ms`
5. POSTs the processed event to `/processedevent/api`

Uses a timestamped consumer group ID so each restart gets a fresh consumer (no stale offset issues in development).

## Docker Compose Services

| Service | Image | Port | Purpose |
|:--------|:------|:-----|:--------|
| **mongodb** | mongo:8 | 27017 | Document store (replica set mode for CDC) |
| **kafka** | apache/kafka:3.7.0 | 9092, 9093 | Message broker (KRaft mode) |
| **debezium** | debezium/server:2.6 | 8085 | CDC connector (MongoDB → Kafka) |
| **events** | Built from Dockerfile | 4055 | MeshQL server + Kafka processor |

## Configuration

| Variable | Default | Purpose |
|:---------|:--------|:--------|
| `MONGO_URI` | `mongodb://localhost:27017/?replicaSet=rs0` | MongoDB (must be replica set) |
| `PORT` | `4055` | Server port |
| `PREFIX` | `events` | Database/collection/topic prefix |
| `ENV` | `development` | Environment name |
| `PLATFORM_URL` | `http://localhost:4055` | Federation resolver base URL |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server |
| `RAW_TOPIC` | `events.events_development.event` | Topic for raw event CDC messages |
| `PROCESSED_API_BASE` | `http://localhost:4055/processedevent/api` | Where processor POSTs results |

Database: `{PREFIX}_{ENV}` (e.g., `events_development`)

## Testing

BDD tests use JUnit 5 with Testcontainers to spin up the full stack:

```bash
mvn test
```

**Note**: Tests are skipped in CI (`CI=true` env var) because they require Docker and take 60-90 seconds for container startup.

4 test scenarios cover:

1. **Event → Kafka**: Create event via REST, verify CDC message in Kafka
2. **ProcessedEvent → Kafka**: POST processed event, verify CDC propagation
3. **Processor end-to-end**: Create event, wait for processor to consume, enrich, and POST
4. **Full pipeline**: Create event with complex data, verify enrichment (status, metadata, original fields preserved)

## Performance

End-to-end CDC latency benchmarks (from `performance/RESULTS.md`):

| Metric | Value |
|:-------|:------|
| HTTP API response (avg) | 1.5-2.2ms |
| HTTP API response (p95) | 2.2-3.8ms |
| Batch submit (100 events) | 179ms |
| CDC end-to-end (100 events) | 1,251ms |
| Per-event latency | 12-13ms |

k6 load test scripts are in `performance/`. The HTTP-based test (`cdc-latency-http.k6.js`) works with standard k6; the Kafka-based test requires the xk6-kafka extension.

## Project Structure

```
examples/events/
├── src/main/java/.../
│   ├── Main.java                  # Server + processor startup
│   └── RawToProcessedProcessor.java  # Kafka consumer + enrichment
├── src/test/java/.../
│   └── EventsBddTest.java        # Testcontainers BDD tests
├── config/
│   ├── graph/                     # GraphQL schemas (event, processedevent)
│   └── json/                      # JSON schemas for REST validation
├── performance/                   # k6 load tests + benchmarks
├── docker-compose.yml             # Full CDC stack
├── Dockerfile                     # Multi-stage build
└── pom.xml
```

## What This Demonstrates

- **CDC with MeshQL** — Debezium captures MongoDB changes automatically; no application-level event publishing needed
- **Event sourcing pattern** — raw events are immutable, processed events are derived
- **Federation across event types** — query a raw event and get its processed versions, or trace a processed event back to its source
- **Kafka integration** — standard consumer/producer patterns alongside MeshQL's REST/GraphQL APIs
- **Same server, two roles** — MeshQL serves the API while the processor consumes from Kafka, all in one JVM
