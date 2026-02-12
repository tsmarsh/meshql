# SwiftShip Logistics

A full-stack package tracking application with 3 frontend apps, 4 entities, 8 federation resolvers, Docker Compose for development, and Kubernetes manifests for production. One backend serves all three apps with zero coordination between teams.

## What It Does

SwiftShip tracks packages through a logistics network: **Warehouses** hold **Shipments**, shipments contain **Packages**, and packages accumulate **Tracking Updates** as they move through the delivery pipeline.

Three frontend applications consume the same backend differently:

| App | URL | Stack | How It Uses MeshQL |
|:----|:----|:------|:-------------------|
| **Customer Tracking** | `/track/` | React + Tailwind | Single GraphQL query federates across 4 entities |
| **Admin Portal** | `/admin/` | Alpine.js + DaisyUI | REST for writes, GraphQL for reads |
| **Reporting Dashboard** | `/reports/` | Alpine.js + Chart.js | GraphQL `getAll` queries, client-side aggregation |

## Quick Start

```bash
cd examples/logistics
docker compose up
```

Once all services are healthy (~60 seconds), open http://localhost:8080.

| URL | App |
|:----|:----|
| http://localhost:8080/track/ | Customer tracking page |
| http://localhost:8080/admin/ | Admin portal |
| http://localhost:8080/reports/ | Reporting dashboard |
| http://localhost:8080/api/ | MeshQL API (direct access) |

Try tracking number `PKG-DEN1A001` in the customer app — it shows a full delivery lifecycle from Denver to Portland.

## Demo Tracking Numbers

The seed script populates 3 warehouses, 6 shipments, 12 packages, and 70+ tracking updates:

| Tracking Number | Status | Route |
|:----------------|:-------|:------|
| `PKG-DEN1A001` | Delivered | Denver, CO → Portland, OR (FedEx) |
| `PKG-DEN2C003` | In Transit | Denver, CO → Seattle, WA (UPS) |
| `PKG-CHI3E005` | Out for Delivery | Chicago, IL → New York, NY (USPS) |
| `PKG-CHI4G007` | Label Created | Chicago, IL → Detroit, MI (FedEx) |
| `PKG-ATL5I009` | Delivered | Atlanta, GA → Miami, FL (DHL) |
| `PKG-ATL6K011` | Delayed | Atlanta, GA → Nashville, TN (Amazon) |

## Domain Model

```
Warehouse
 ├── Shipment (warehouse_id)
 │    └── Package (shipment_id)
 │         └── TrackingUpdate (package_id)
 └── Package (warehouse_id)
```

| Entity | Key Fields | Validation |
|:-------|:-----------|:-----------|
| **Warehouse** | name, address, city, state, zip, capacity | State: 2 chars, zip: 5 digits |
| **Shipment** | destination, carrier, status, estimated_delivery, warehouse_id | Carrier: FedEx/UPS/USPS/DHL/Amazon |
| **Package** | tracking_number, description, weight, recipient, recipient_address | Tracking: `^PKG-[A-Z0-9]{8}$` |
| **TrackingUpdate** | status, location, timestamp, notes, package_id | Status: 10 enum values |

## Federation Map

8 resolvers connect the 4 entities:

| Source | Field | Type | Target Query | Target |
|:-------|:------|:-----|:-------------|:-------|
| Warehouse | `shipments` | Vector | `getByWarehouse` | `/shipment/graph` |
| Warehouse | `packages` | Vector | `getByWarehouse` | `/package/graph` |
| Shipment | `warehouse` | Singleton | `getById` | `/warehouse/graph` |
| Shipment | `packages` | Vector | `getByShipment` | `/package/graph` |
| Package | `warehouse` | Singleton | `getById` | `/warehouse/graph` |
| Package | `shipment` | Singleton | `getById` | `/shipment/graph` |
| Package | `trackingUpdates` | Vector | `getByPackage` | `/tracking_update/graph` |
| TrackingUpdate | `package` | Singleton | `getById` | `/package/graph` |

The customer app's single-query tracking lookup uses 3 of these resolvers in one request.

## Architecture

```
┌───────────────────── nginx (:8080) ──────────────────────┐
│                                                          │
│  /track/           /admin/            /reports/           │
│  React+Tailwind    Alpine+DaisyUI     Alpine+Chart.js    │
│       │                 │                   │             │
└───────┼─────────────────┼───────────────────┼─────────────┘
        │                 │                   │
        └────────┬────────┴───────────────────┘
                 │ /api/
   ┌─────────────┴──────────────────────────────────┐
   │              MeshQL Server (:3044)              │
   │                                                 │
   │  GraphQL:  /warehouse/graph  /shipment/graph    │
   │            /package/graph    /tracking_update/graph
   │                                                 │
   │  REST:     /warehouse/api    /shipment/api      │
   │            /package/api      /tracking_update/api
   │                                                 │
   │  Federation: 8 resolvers linking all 4 entities │
   └──────────────────────┬──────────────────────────┘
                          │
                    ┌─────┴─────┐
                    │  MongoDB  │
                    └───────────┘
```

## The Three Apps

### Customer App (React)

Enters a tracking number, fires one GraphQL query, gets everything:

```graphql
{
  getByTrackingNumber(tracking_number: "PKG-DEN1A001") {
    id description weight recipient recipient_address
    warehouse { name city state }
    shipment { destination carrier status estimated_delivery }
    trackingUpdates { status location timestamp notes }
  }
}
```

### Admin App (Alpine.js)

**Writes** via REST — `POST /package/api`, `POST /shipment/api`, `POST /tracking_update/api` — with JSON Schema validation.

**Reads** via GraphQL — warehouse dashboard loads shipments and packages in a single federated query:

```graphql
{
  getById(id: "warehouse-id") {
    name city state
    shipments { id destination carrier status }
    packages { id tracking_number description weight }
  }
}
```

### Reporting App (Alpine.js + Chart.js)

Fetches flat lists via GraphQL `getAll` queries on all 4 entities, then aggregates client-side into charts (packages per warehouse, shipment status breakdown, packages by carrier).

```javascript
const [warehouses, shipments, packages, updates] = await Promise.all([
    gqlAll('/warehouse/graph', '{ getAll { id name city state } }', 'getAll'),
    gqlAll('/shipment/graph', '{ getAll { id carrier status warehouse_id } }', 'getAll'),
    gqlAll('/package/graph', '{ getAll { id weight warehouse_id shipment_id } }', 'getAll'),
    gqlAll('/tracking_update/graph', '{ getAll { id status timestamp } }', 'getAll')
]);
```

## API Reference

### GraphQL Queries

| Endpoint | Query | Returns |
|:---------|:------|:--------|
| `/warehouse/graph` | `getById(id)` | Single warehouse |
| `/warehouse/graph` | `getAll` | All warehouses |
| `/warehouse/graph` | `getByCity(city)` | Warehouses in city |
| `/warehouse/graph` | `getByState(state)` | Warehouses in state |
| `/shipment/graph` | `getById(id)` | Single shipment |
| `/shipment/graph` | `getAll` | All shipments |
| `/shipment/graph` | `getByWarehouse(id)` | Shipments for warehouse |
| `/shipment/graph` | `getByStatus(status)` | Shipments by status |
| `/shipment/graph` | `getByCarrier(carrier)` | Shipments by carrier |
| `/package/graph` | `getById(id)` | Single package |
| `/package/graph` | `getByTrackingNumber(tracking_number)` | Package by tracking number |
| `/package/graph` | `getAll` | All packages |
| `/package/graph` | `getByWarehouse(id)` | Packages for warehouse |
| `/package/graph` | `getByShipment(id)` | Packages in shipment |
| `/package/graph` | `getByRecipient(recipient)` | Packages by recipient (regex) |
| `/tracking_update/graph` | `getById(id)` | Single update |
| `/tracking_update/graph` | `getAll` | All updates |
| `/tracking_update/graph` | `getByPackage(id)` | Updates for package |

All queries support `at: Float` for temporal queries (point-in-time reads).

### REST Endpoints

Each entity exposes: `POST /`, `GET /`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`, `POST /bulk`, `GET /bulk?ids=...`

- `/warehouse/api`
- `/shipment/api`
- `/package/api`
- `/tracking_update/api`

## Docker Compose Services

| Service | Purpose | Port |
|:--------|:--------|:-----|
| `mongodb` | Document store | internal |
| `meshql` | MeshQL server (4 graphlettes + 4 restlettes) | 3044 |
| `customer-app` | React tracking SPA | internal |
| `admin-app` | Alpine.js admin portal | internal |
| `reporting-app` | Alpine.js + Chart.js dashboard | internal |
| `nginx` | Reverse proxy (routes by path) | 8080 |
| `seed` | Populates demo data (runs once) | — |

## Kubernetes

The same application deploys to Kubernetes with no code changes. The `k8s/` directory contains full manifests:

```
k8s/
├── namespace.yaml          # logistics namespace
├── ingress.yaml            # path-based routing (same as nginx)
├── meshql/
│   ├── deployment.yaml     # 2 replicas, health checks, resource limits
│   ├── service.yaml
│   └── configmap.yaml      # GraphQL + JSON schemas
├── mongodb/
│   ├── statefulset.yaml    # 1 replica, 1Gi PVC
│   └── service.yaml
├── customer-app/
├── admin-app/
├── reporting-app/
└── seed-job.yaml           # one-shot seed data
```

The only difference from Docker Compose: `PLATFORM_URL` changes from `http://meshql:3044` to `http://meshql.logistics.svc.cluster.local:3044`. Same `Main.java`. Same Docker images.

## Configuration

| Variable | Default | Purpose |
|:---------|:--------|:--------|
| `MONGO_URI` | `mongodb://localhost:27017` | MongoDB connection |
| `PORT` | `3044` | Server port |
| `PREFIX` | `logistics` | Database/collection prefix |
| `ENV` | `development` | Environment name |
| `PLATFORM_URL` | `http://localhost:3044` | Federation resolver base URL |

Database: `{PREFIX}_{ENV}` (e.g., `logistics_development`)
Collections: `{PREFIX}-{ENV}-{entity}` (e.g., `logistics-development-package`)

## Testing

13 Cucumber BDD scenarios cover REST CRUD, GraphQL queries, and federation:

```bash
mvn test
```

Tests use Testcontainers to spin up the full stack. The highlight is the "full customer journey" scenario — a single tracking number lookup that asserts data from all 4 entities arrives correctly through federation:

```gherkin
Scenario: Full customer journey — tracking number lookup returns complete data
  When I query the package graph with full federation for "PKG-BDDTEST1"
  Then the GraphQL result "tracking_number" should be "PKG-BDDTEST1"
  And the nested "warehouse" field "state" should be "CO"
  And the nested "shipment" field "status" should be "in_transit"
  And the nested "trackingUpdates" should contain an item with "status" equal to "delivered"
```

## Project Structure

```
examples/logistics/
├── src/main/java/.../Main.java       # Server configuration (single file)
├── src/test/
│   ├── java/.../                      # BDD step defs + hooks
│   └── resources/features/            # Cucumber scenarios
├── config/
│   ├── graph/                         # GraphQL schemas (4 entities)
│   └── json/                          # JSON schemas for REST validation
├── customer-app/                      # React 19 + Vite + Tailwind
├── admin-app/                         # Alpine.js + DaisyUI
├── reporting-app/                     # Alpine.js + Chart.js
├── seed/                              # Shell script + Dockerfile
├── k8s/                               # Kubernetes manifests
├── docker-compose.yml
├── nginx.conf
├── Dockerfile
└── pom.xml
```

## What This Demonstrates

- **One backend, three apps** — three teams, three stacks, zero coordination on the backend
- **REST for writes, GraphQL for reads** — each protocol does what it's good at
- **Federation in action** — a single query traverses 4 entities via 3 resolvers
- **MVP to production** — same `Main.java` runs in Docker Compose and Kubernetes
- **Every entity is a data product** — own schema, own endpoint, own validation, independently deployable
