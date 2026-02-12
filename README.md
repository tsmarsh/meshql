# MeshQL

Define schemas. Wire resolvers. Get a full data API with REST, GraphQL, and federation — no boilerplate.

MeshQL is a Java 21 framework for building data services where every entity gets its own REST endpoint, its own GraphQL endpoint, and federation resolvers that connect them. You write configuration, not plumbing.

## What You Get

```java
Config config = Config.builder()
    .port(3033)
    .graphlette(GraphletteConfig.builder()
        .path("/item/graph")
        .storage(mongoConfig)
        .schema("/config/item.graphql")
        .rootConfig(RootConfig.builder()
            .singleton("getById", "{\"id\": \"{{id}}\"}")
            .vector("getByCategory", "{\"payload.category\": \"{{category}}\"}")
            .vectorResolver("reviews", null, "getByItem",
                platformUrl + "/review/graph")))
    .restlette(RestletteConfig.builder()
        .path("/item/api")
        .storage(mongoConfig)
        .schema(loadJsonSchema("/config/item.schema.json")))
    .build();

Server server = new Server(Map.of("mongo", new MongoPlugin(auth)));
server.init(config);
```

That gives you:

- **GraphQL** at `/item/graph` with `getById`, `getByCategory`, and federated `reviews`
- **REST** at `/item/api` with `POST`, `GET`, `PUT`, `DELETE`, bulk operations, and Swagger docs
- **JSON Schema validation** on all REST writes
- **Temporal queries** — every query supports `at: Float` for point-in-time reads
- **Health checks** at `/health` and `/ready`

## Core Concepts

| Concept | What It Does |
|:--------|:-------------|
| **Graphlette** | GraphQL endpoint for an entity — queries, federation resolvers |
| **Restlette** | REST endpoint for an entity — CRUD, bulk ops, Swagger, JSON Schema validation |
| **Resolver** | Connects entities across graphlettes (singleton for 1:1, vector for 1:N) |
| **Envelope** | Internal wrapper: `{id, payload, createdAt, deleted, authorizedTokens}` |
| **Plugin** | Storage backend factory (MongoDB, PostgreSQL, SQLite, in-memory) |

## Features

- **Dual APIs**: REST and GraphQL from the same entity definition
- **Federation**: Resolvers connect entities across graphlettes via HTTP or in-process calls
- **Multiple datastores**: PostgreSQL, MongoDB, SQLite, in-memory — mix and match
- **Pluggable auth**: JWT (decode-only, for gateway-validated tokens), Casbin RBAC, NoAuth
- **Temporal queries**: Point-in-time reads on any query (`at` parameter)
- **JSON Schema validation**: REST writes validated against schema files
- **Virtual threads**: Jetty 12 + Project Loom for efficient concurrency
- **Swagger/OpenAPI**: Auto-generated docs on every REST endpoint

## Examples

Three complete applications, each demonstrating different aspects of MeshQL:

| Example | Entities | Key Demonstration |
|:--------|:---------|:-----------------|
| [**Farm**](examples/farm/) | Farm, Coop, Hen, LayReport | 4-level hierarchical federation, performance benchmarks (indexing beats DataLoader 100x) |
| [**Events**](examples/events/) | Event, ProcessedEvent | CDC pipeline with Debezium + Kafka, event enrichment processor |
| [**Logistics**](examples/logistics/) | Warehouse, Shipment, Package, TrackingUpdate | 3 frontend apps (React, Alpine.js, Chart.js), Docker + Kubernetes deployment |

Each example runs with `docker compose up` and includes full test suites.

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker (for examples and integration tests)

### Build

```bash
mvn clean install
```

### Run an Example

```bash
cd examples/farm
docker compose up
# GraphQL: http://localhost:3033/farm/graph
# REST:    http://localhost:3033/farm/api
```

## Authentication

MeshQL provides pluggable authentication designed for enterprise deployment behind API gateways:

### JWT (`JWTSubAuthorizer`)

Extracts the `sub` claim from Bearer tokens. **Does not verify signatures** — assumes an upstream gateway (Kong, Istio, etc.) has already validated the token.

```java
Auth auth = new JWTSubAuthorizer();
```

### Casbin RBAC (`CasbinAuth`)

Wraps JWT to provide role-based access control:

```java
Auth jwtAuth = new JWTSubAuthorizer();
CasbinAuth auth = CasbinAuth.create("model.conf", "policy.csv", jwtAuth);
// Returns roles: ["admin", "editor"] instead of raw user ID
```

### NoAuth (Development)

```java
Auth auth = new NoAuth();  // Always authorizes
```

## Server Architecture

MeshQL runs on Jetty 12 with virtual threads (Project Loom):

- Each request runs on a lightweight virtual thread
- Thousands of concurrent requests without thread pool exhaustion
- I/O-bound workloads (database queries, federation HTTP calls) don't block platform threads

## Project Structure

```
meshql/
├── core/           # Interfaces: Repository, Searcher, Auth, Plugin, Validator
├── api/
│   ├── graphlette/ # GraphQL endpoint implementation
│   └── restlette/  # REST endpoint implementation
├── auth/
│   ├── jwt/        # JWT sub-claim extraction
│   ├── casbin/     # Casbin RBAC wrapper
│   └── noop/       # NoAuth for development
├── repositories/
│   ├── mongo/      # MongoDB plugin
│   ├── postgres/   # PostgreSQL plugin (HikariCP)
│   ├── sqlite/     # SQLite plugin
│   └── mem/        # In-memory plugin
├── server/         # Jetty 12 server assembly
└── examples/
    ├── farm/       # Hierarchical federation
    ├── events/     # CDC pipeline
    └── logistics/  # Full-stack application
```

## Documentation

Full documentation at [tsmarsh.github.io/meshql](https://tsmarsh.github.io/meshql/).

## License

[Business Source License 1.1](LICENSE.template)
