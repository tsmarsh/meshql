# MeshQL Development Guide

## Build Commands
- Full build: `mvn clean install`
- Run all tests: `mvn test`
- Run single test: `mvn test -Dtest=ClassName`
- Run specific method: `mvn test -Dtest=ClassName#methodName`

## Code Style Guidelines
- **Java Version**: Java 17
- **Naming**: 
  - Classes: PascalCase (e.g., `CrudHandler`)
  - Methods: camelCase (e.g., `getAuthTokens`)
  - Interfaces: Noun-based (e.g., `Repository`)
- **Formatting**:
  - 4-space indentation
  - Opening braces on same line
- **Architecture**:
  - Interface-based design
  - Composition over inheritance
  - Immutable objects when possible
  - Functional programming style with UnderBar utilities
- **Error Handling**:
  - Use specific exceptions with clear messages
  - Log errors with SLF4J
  - Return appropriate HTTP status codes
  - Format errors consistently

  # MeshQL Integration Guide for AI Agents

This document helps AI agents integrate MeshQL into projects. Add this to your project's CLAUDE.md when using MeshQL as a dependency.

## Overview

MeshQL is a Java 21+ framework for building data APIs with:
- **Dual APIs**: REST (Restlette) and GraphQL (Graphlette)
- **Multiple datastores**: PostgreSQL, MongoDB, SQLite, in-memory
- **Pluggable auth**: JWT (decode-only), Casbin RBAC, NoAuth
- **Federation**: Internal/external resolvers between graphlettes

## Core Concepts

### Envelope
All data is wrapped in an Envelope with metadata:
```java
public record Envelope(
    String id,
    Stash payload,           // User data (key-value map)
    Instant createdAt,
    boolean deleted,
    List<String> authorizedTokens  // For access control
) {}
```

### Key Interfaces
| Interface | Purpose |
|-----------|---------|
| `Repository` | CRUD operations on documents |
| `Searcher` | GraphQL query execution |
| `Auth` | Authentication & authorization |
| `Plugin` | Storage backend factory |
| `Validator` | JSON schema validation |

## Quick Start Pattern

```java
// 1. Create storage config
MongoConfig storageConfig = new MongoConfig();
storageConfig.uri = "mongodb://localhost:27017";
storageConfig.db = "myapp";
storageConfig.collection = "items";

// 2. Create auth
Auth auth = new NoAuth();  // Development
// Auth auth = new JWTSubAuthorizer();  // Production

// 3. Create query configs
List<QueryConfig> singletons = List.of(
    new QueryConfig("getById", "{\"id\": \"{{id}}\"}")
);
List<QueryConfig> vectors = List.of(
    new QueryConfig("getByCategory", "{\"category\": \"{{category}}\"}")
);

// 4. Create RootConfig (GraphQL configuration)
RootConfig rootConfig = new RootConfig(
    singletons,           // Single-object queries
    vectors,              // List queries
    List.of(),            // External singleton resolvers
    List.of(),            // External vector resolvers
    List.of(),            // Internal singleton resolvers
    List.of()             // Internal vector resolvers
);

// 5. Create GraphletteConfig
GraphletteConfig graphlette = new GraphletteConfig(
    "/item/graph",        // Path
    storageConfig,
    "/path/to/schema.graphql",
    rootConfig
);

// 6. Initialize server
Map<String, Plugin> plugins = Map.of("mongo", new MongoPlugin(auth));
Config config = new Config(null, List.of(graphlette), 3033, List.of());
Server server = new Server(plugins);
server.init(config);
```

## Configuration Records

### QueryConfig
Maps GraphQL query names to database query templates:
```java
new QueryConfig("getById", "{\"id\": \"{{id}}\"}}")
new QueryConfig("getByName", "{\"name\": {\"$regex\": \"{{name}}\"}}")
```
Templates use Handlebars syntax. Parameters come from GraphQL query arguments.

### Resolver Configs

**External 1:1 relationship** (HTTP call to another service):
```java
new SingletonResolverConfig(
    "author",                    // Field name in schema
    "author_id",                 // Foreign key in this entity
    "getById",                   // Query on remote service
    URI.create("http://localhost:3033/author/graph")
)
```

**External 1:N relationship**:
```java
new VectorResolverConfig(
    "books",                     // Field name
    "id",                        // This entity's ID field
    "getByAuthor",               // Query on remote service
    URI.create("http://localhost:3033/book/graph")
)
```

**Internal resolvers** (between graphlettes in same server - no HTTP overhead):
```java
new InternalSingletonResolverConfig("coop", "coop_id", "getById", "/coop/graph")
new InternalVectorResolverConfig("hens", "id", "getByCoop", "/hen/graph")
```

### Storage Configs

**MongoDB**:
```java
MongoConfig config = new MongoConfig();
config.uri = "mongodb://localhost:27017";
config.db = "database_name";
config.collection = "collection_name";
```

**PostgreSQL**:
```java
PostgresConfig config = new PostgresConfig();
config.uri = "jdbc:postgresql://localhost:5432/db";
config.table = "table_name";
config.username = "user";
config.password = "pass";
```

## Authentication

### JWT (Production)
Extracts `sub` claim from Bearer token. **Does NOT verify signatures** - assumes upstream gateway validates.

```java
Auth auth = new JWTSubAuthorizer();
// Authorization header: "Bearer eyJ..."
// Returns: ["user-id-from-sub-claim"]
```

### Casbin RBAC
Wraps another Auth to provide role-based access:
```java
Auth jwtAuth = new JWTSubAuthorizer();
CasbinAuth auth = CasbinAuth.create("model.conf", "policy.csv", jwtAuth);
// Returns: ["admin", "editor"] (roles for the user)
```

### NoAuth (Development only)
```java
Auth auth = new NoAuth();  // Always authorizes
```

## GraphQL Schema Pattern

```graphql
scalar Date

type Query {
  getById(id: ID!, at: Float): Item      # Singleton query
  getByCategory(category: String!): [Item]  # Vector query
}

type Item {
  id: ID!
  name: String!
  category: String
  createdAt: Date
  relatedItems: [Item]    # Resolved via VectorResolver
  author: Author          # Resolved via SingletonResolver
}
```

The `at: Float` parameter enables temporal queries (returns data as of that timestamp).

## REST API Endpoints

Restlette automatically provides:
| Method | Path | Description |
|--------|------|-------------|
| `POST /` | Create document |
| `GET /` | List all documents |
| `GET /{id}` | Read document |
| `PUT /{id}` | Update document |
| `DELETE /{id}` | Delete document |
| `POST /bulk` | Batch create |
| `GET /bulk?ids=1,2,3` | Batch read |
| `GET /api-docs` | Swagger UI |

## Common Patterns

### Adding a New Entity

1. Create storage config (Mongo/Postgres)
2. Write GraphQL schema file
3. Write JSON schema for REST validation
4. Create QueryConfigs for queries
5. Create ResolverConfigs for relationships
6. Create GraphletteConfig and/or RestletteConfig
7. Add to server Config

### Connecting Entities (Federation)

For `Author -> Books` relationship:

In Author graphlette:
```java
new VectorResolverConfig("books", "id", "getByAuthor",
    URI.create("http://localhost:3033/book/graph"))
```

In Book graphlette (the query being called):
```java
new QueryConfig("getByAuthor", "{\"author_id\": \"{{id}}\"}")
```

### Optimizing with Internal Resolvers

When graphlettes are in the same server, use internal resolvers:
```java
// Instead of:
new VectorResolverConfig("hens", "id", "getByCoop", URI.create("http://..."))

// Use:
new InternalVectorResolverConfig("hens", "id", "getByCoop", "/hen/graph")
```

Benefits: No HTTP overhead, better performance, maintains consistency.

## Project Structure

```
your-project/
├── src/main/java/
│   └── com/example/
│       └── Main.java           # Server initialization
├── config/
│   ├── graph/
│   │   ├── entity1.graphql     # GraphQL schemas
│   │   └── entity2.graphql
│   └── json/
│       ├── entity1.schema.json # JSON schemas for REST
│       └── entity2.schema.json
└── pom.xml
```

## Maven Dependencies

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>core</artifactId>
        <version>0.2.0</version>
    </dependency>

    <!-- Server -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>server</artifactId>
        <version>0.2.0</version>
    </dependency>

    <!-- Choose storage backend(s) -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>mongo</artifactId>
        <version>0.2.0</version>
    </dependency>
    <!-- or -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>postgres</artifactId>
        <version>0.2.0</version>
    </dependency>

    <!-- Choose auth -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>jwt</artifactId>
        <version>0.2.0</version>
    </dependency>
    <!-- optional RBAC -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>casbin</artifactId>
        <version>0.2.0</version>
    </dependency>
</dependencies>
```

## Performance Notes

- **Indexing > DataLoader batching**: Database indexes provide 100x improvement over batching
- **Internal resolvers**: Use for same-server federation to avoid HTTP overhead
- **Virtual threads**: Jetty 12 uses Project Loom for efficient concurrency
- **Connection pooling**: HikariCP for PostgreSQL, built-in for MongoDB

## Debugging

### GraphQL errors appear in response
```json
{
  "data": null,
  "errors": [{"message": "..."}]
}
```

### Check server logs
MeshQL uses SLF4J. Configure logback for detailed output:
```xml
<logger name="com.meshql" level="DEBUG"/>
```

### Verify query templates
Templates use Handlebars. Test with sample data:
```java
Template t = Handlebars.compile("{\"id\": \"{{id}}\"}");
t.apply(Map.of("id", "test-123"));  // {"id": "test-123"}
```

## Reference

- Farm example: `examples/farm/` - Complete 4-entity application
- Tests: `api/graphlette/src/test/` and `api/restlette/src/test/`
- Core interfaces: `core/src/main/java/com/meshql/core/`
