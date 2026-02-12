# Springfield Electric: Legacy Anti-Corruption Layer

A MeshQL example demonstrating the **anti-corruption layer** pattern from Domain-Driven Design. A legacy PostgreSQL database with SCREAMING_SNAKE columns, YYYYMMDD dates as VARCHAR, amounts in cents, and single-character status codes gets transformed into clean domain entities via CDC.

## Architecture

```
Legacy PostgreSQL ──> Debezium CDC ──> Kafka ──> Java Processors ──> MeshQL REST API ──> MongoDB ──> GraphQL/REST
```

The legacy database is **never modified** — MeshQL becomes the clean API layer that new applications consume.

## What Gets Transformed

| Legacy | Clean | How |
|:-------|:------|:----|
| `CUST_NM_FIRST: "MARGARET"` | `first_name: "Margaret"` | Title case |
| `CONN_DT: "19980315"` | `connected_date: "1998-03-15"` | YYYYMMDD to ISO |
| `TOT_AMT: 9840` | `total_amount: 98.40` | Cents to dollars |
| `STAT_CD: "A"` | `status: "active"` | Code to word |
| `RT_CD: "RES"` | `rate_class: "residential"` | Code to word |
| `PYMT_MTHD: "W"` | `method: "web"` | Code to word |
| `EST_FLG: "Y"` | `estimated: true` | Char to boolean |

## Key Differentiators

- **Internal resolvers**: First MeshQL example to use `internalSingletonResolver`/`internalVectorResolver` (no HTTP overhead between graphlettes)
- **PostgreSQL CDC**: Debezium with WAL-level replication (vs MongoDB change streams in events example)
- **Multi-table transformation**: 4 legacy tables with FK resolution across entities
- **3 frontend apps**: Customer portal, operations dashboard, analytics dashboard

## Quick Start

```bash
docker compose up --build
```

Wait for all services to start (Debezium captures legacy data automatically via `snapshot.mode=initial`).

Then visit:
- **Customer Portal**: http://localhost:8080/portal/ — Look up account `100100-00001`
- **Operations Dashboard**: http://localhost:8080/ops/
- **Analytics Dashboard**: http://localhost:8080/analytics/
- **API directly**: http://localhost:4066/customer/graph

## Services

| Service | Port | Purpose |
|:--------|:-----|:--------|
| `legacy-postgres` | 5433 | Legacy database (wal_level=logical) |
| `mongodb` | internal | Clean MeshQL storage |
| `kafka` | 9092 | Message broker (KRaft) |
| `debezium` | 8085 | CDC connector (PG to Kafka) |
| `legacy-app` | 4066 | MeshQL server + processors |
| `customer-portal` | via nginx | React customer-facing app |
| `operations-app` | via nginx | Alpine.js ops dashboard |
| `analytics-app` | via nginx | Alpine.js + Chart.js analytics |
| `nginx` | 8080 | Reverse proxy |

## API Endpoints

### GraphQL
- `POST /customer/graph` — Customer queries with federated meterReadings, bills, payments
- `POST /meter_reading/graph` — Meter reading queries with federated customer
- `POST /bill/graph` — Bill queries with federated customer and payments
- `POST /payment/graph` — Payment queries with federated customer and bill

### REST
- `/customer/api` — CRUD for customers
- `/meter_reading/api` — CRUD for meter readings
- `/bill/api` — CRUD for bills
- `/payment/api` — CRUD for payments

## Example Query

```graphql
{
  getByAccountNumber(account_number: "100100-00001") {
    first_name
    last_name
    rate_class
    status
    connected_date
    meterReadings {
      reading_date
      kwh_used
      reading_type
      estimated
    }
    bills {
      bill_date
      total_amount
      kwh_used
      status
    }
    payments {
      payment_date
      amount
      method
    }
  }
}
```

## Seed Data

4 customers representing different scenarios:

| Account | Name | Rate Class | Status |
|:--------|:-----|:-----------|:-------|
| 100100-00001 | Margaret Henderson | Residential | Active |
| 200200-00002 | Kenji Nakamura | Commercial | Active |
| 100100-00003 | Siobhan O'Connell | Residential | Suspended |
| 300300-00004 | James Whitfield | Industrial | Active |

## Running Tests

```bash
# Start the stack
docker compose up -d

# Run BDD tests (14 scenarios)
mvn test -pl examples/legacy -Dtest=LegacyBddTest

# Or with a custom API base
API_BASE=http://localhost:4066 mvn test -pl examples/legacy -Dtest=LegacyBddTest
```

## Build

```bash
# Compile
mvn compile -pl examples/legacy -am

# Package (uber JAR)
mvn package -pl examples/legacy -am -DskipTests
```
