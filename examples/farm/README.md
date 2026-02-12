# Farm Management System

A farm management API demonstrating MeshQL's core pattern: define schemas, wire resolvers, get a full data API with GraphQL federation and REST out of the box.

4 entities. 8 resolvers. 1 JVM. No boilerplate.

## What It Does

This application manages a farm hierarchy: **Farms** contain **Coops**, coops contain **Hens**, and hens produce **Lay Reports**. Each entity has its own MongoDB collection, its own REST endpoint, and its own GraphQL endpoint. Federation resolvers connect them so you can query the full graph in a single request.

```
Farm
 └── Coop (farm_id)
      └── Hen (coop_id)
           └── LayReport (hen_id)
```

A single GraphQL query traverses the entire hierarchy:

```graphql
{
  getById(id: "farm-123") {
    name
    coops {
      name
      hens {
        name
        eggs
        layReports {
          time_of_day
          eggs
        }
      }
    }
  }
```

That query hits 4 graphlettes — Farm resolves Coops, Coops resolve Hens, Hens resolve LayReports — all via HTTP federation, all from one server.

## Quick Start

```bash
cd examples/farm
docker compose up
```

Once the health check passes (~30 seconds), the API is live:

| Endpoint | Type | URL |
|:---------|:-----|:----|
| Farms | GraphQL | http://localhost:3033/farm/graph |
| Coops | GraphQL | http://localhost:3033/coop/graph |
| Hens | GraphQL | http://localhost:3033/hen/graph |
| Lay Reports | GraphQL | http://localhost:3033/lay_report/graph |
| Farms | REST + Swagger | http://localhost:3033/farm/api |
| Coops | REST + Swagger | http://localhost:3033/coop/api |
| Hens | REST + Swagger | http://localhost:3033/hen/api |
| Lay Reports | REST + Swagger | http://localhost:3033/lay_report/api |
| Health | HTTP | http://localhost:3033/ready |

## Try It

Create some data via REST, then query it via GraphQL:

```bash
# Create a farm
FARM_ID=$(curl -s -X POST http://localhost:3033/farm/api \
  -H "Content-Type: application/json" \
  -d '{"name": "Emerdale"}' \
  -w '\n' | jq -r '.id // empty')

# If no id in response, check the Location header
FARM_ID=${FARM_ID:-$(curl -s -X POST http://localhost:3033/farm/api \
  -H "Content-Type: application/json" \
  -d '{"name": "Emerdale"}' \
  -D - -o /dev/null | grep -i location | awk -F/ '{print $NF}' | tr -d '\r')}

# Create a coop in that farm
curl -s -X POST http://localhost:3033/coop/api \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"Red Barn\", \"farm_id\": \"$FARM_ID\"}"

# Query the farm with nested coops via GraphQL
curl -s -X POST http://localhost:3033/farm/graph \
  -H "Content-Type: application/json" \
  -d "{\"query\": \"{ getById(id: \\\"$FARM_ID\\\") { name coops { name hens { name eggs } } } }\"}" | jq
```

## Domain Model

| Entity | Fields | Relationships |
|:-------|:-------|:--------------|
| **Farm** | `name` | has many Coops |
| **Coop** | `name`, `farm_id` | belongs to Farm, has many Hens |
| **Hen** | `name`, `eggs` (0-10), `dob`, `coop_id` | belongs to Coop, has many LayReports |
| **LayReport** | `time_of_day` (morning/afternoon/evening), `eggs` (0-3), `hen_id` | belongs to Hen |

## Federation Map

| Source | Field | Type | Target Query | Target |
|:-------|:------|:-----|:-------------|:-------|
| Farm | `coops` | Vector | `getByFarm` | `/coop/graph` |
| Coop | `farm` | Singleton | `getById` | `/farm/graph` |
| Coop | `hens` | Vector | `getByCoop` | `/hen/graph` |
| Hen | `coop` | Singleton | `getById` | `/coop/graph` |
| Hen | `layReports` | Vector | `getByHen` | `/lay_report/graph` |
| LayReport | `hen` | Singleton | `getById` | `/hen/graph` |

Every relationship is navigable in both directions. Farm → Coops → Hens → LayReports and back.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MeshQL Server (:3033)                 │
│                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ /farm/graph  │──│ /coop/graph  │──│ /hen/graph   │    │
│  │ /farm/api    │  │ /coop/api    │  │ /hen/api     │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
│         │                 │                 │            │
│         │    ┌────────────┘     ┌────────────┘           │
│         │    │    ┌─────────────┘                        │
│  ┌──────┴────┴────┴──────────────────────────────┐      │
│  │  /lay_report/graph  ·  /lay_report/api        │      │
│  └───────────────────────────────────────────────┘      │
└───────────────────────────┬─────────────────────────────┘
                            │
                     ┌──────┴──────┐
                     │   MongoDB   │
                     │  4 collections
                     └─────────────┘
```

All 4 entities live in the same JVM, the same MongoDB instance, and are connected via HTTP federation resolvers. In production, each graphlette could be extracted to its own service with no code changes — only `PLATFORM_URL` changes.

## Configuration

The entire backend is configured in `Main.java`. Environment variables:

| Variable | Default | Purpose |
|:---------|:--------|:--------|
| `MONGO_URI` | `mongodb://localhost:27017` | MongoDB connection |
| `PORT` | `3033` | Server port |
| `PREFIX` | `farm` | Database/collection prefix |
| `ENV` | `development` | Environment name |
| `PLATFORM_URL` | `http://localhost:3033` | Base URL for federation resolvers |

Database name: `{PREFIX}_{ENV}` (e.g., `farm_development`)
Collection names: `{PREFIX}-{ENV}-{entity}` (e.g., `farm-development-hen`)

## Testing

BDD tests use Cucumber with Testcontainers:

```bash
npm test
```

Tests spin up the full Docker stack, create data via REST, and verify GraphQL federation across all 4 entities.

## Performance

Stress testing showed that **proper database indexing outperforms DataLoader batching by 100x** for this workload:

| Query | Without Index | With Index | Improvement |
|:------|:-------------|:-----------|:------------|
| Hen with Lay Reports | 550-608ms | **6ms** | **100x** |
| Overall Throughput | 25.7 req/s | **39.1 req/s** | 52% higher |

The critical indexes:

```javascript
db['farm-development-lay_report'].createIndex({'payload.hen_id': 1});
db['farm-development-hen'].createIndex({'payload.coop_id': 1});
db['farm-development-coop'].createIndex({'payload.farm_id': 1});
```

See `performance/PERFORMANCE.md` for full analysis and JMeter test plans.

## Project Structure

```
examples/farm/
├── src/main/java/.../Main.java    # Server configuration
├── config/
│   ├── graph/                     # GraphQL schemas (4 files)
│   └── json/                      # JSON schemas for REST validation (4 files)
├── test/                          # Cucumber BDD tests (TypeScript)
├── performance/                   # JMeter test plans + analysis
├── docker-compose.yml             # MongoDB + MeshQL
├── Dockerfile                     # Multi-stage build (Maven → JRE 21)
└── pom.xml
```

## What This Demonstrates

- **Hierarchical federation** — 4 levels of nesting resolved in a single query
- **Bidirectional navigation** — every relationship works in both directions
- **REST + GraphQL** — same entity, same storage, both protocols
- **Configuration-driven** — no annotations, no code generation, just `Main.java`
- **Indexing > DataLoader** — proper indexes beat query batching by 100x
