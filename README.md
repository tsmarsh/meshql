# MeshQL

A modular Java framework for building scalable data applications with consistent APIs across multiple datastores.

## Overview

MeshQL enables developers to create robust data services with:

- **Multiple Database Support**: PostgreSQL, MongoDB, SQLite, in-memory
- **Dual API Endpoints**: REST (Restlette) and GraphQL (Graphlette)
- **Pluggable Authentication**: JWT (decode-only for gateway-validated tokens), Casbin, and NoAuth implementations
- **Event-Driven Architecture**: Kafka integration for data synchronization

## Architecture

![Components](./diagrams/components.mermaid)

MeshQL follows a modular, plugin-based architecture:

- **Core**: Interfaces and shared components
- **Repositories**: Database-specific implementations
- **API**: REST and GraphQL endpoints
- **Auth**: Authentication and authorization components

## Quick Start

```bash
# Build the project
mvn clean install

# Run tests
mvn test
```

## Example Usage

```java
// Create a repository
Repository repo = new PostgresRepository(config);

// Store data
Envelope data = new Envelope("user", Map.of("name", "Alice"));
repo.create(data);

// Query data
SearchResult results = repo.search("user", Query.where("name").is("Alice"));
```

## Features

- Strong typing with generic interfaces
- JSON schema validation
- Swagger/OpenAPI documentation
- ACID transaction support
- Query templating
- Document-level access control

## Authentication

MeshQL provides pluggable authentication designed for enterprise deployment behind API gateways:

### JWT Authentication (`JWTSubAuthorizer`)

The JWT implementation **does not verify signatures** - it only decodes and extracts the `sub` claim. This is intentional:

- **Enterprise Pattern**: JWT validation is handled by upstream services (API gateway, Istio, Kong, etc.)
- **No Secrets Required**: The application doesn't need access to JWT signing keys
- **Performance**: No cryptographic verification overhead

```java
// The authorizer extracts 'sub' from pre-validated Bearer tokens
Auth auth = new JWTSubAuthorizer();
List<String> credentials = auth.getAuthToken(context);  // Returns ["user-id-from-sub"]

// Authorization checks against document's authorized_tokens
boolean allowed = auth.isAuthorized(credentials, envelope);
```

### Casbin Authorization (`CasbinAuth`)

Casbin provides role-based access control (RBAC) by wrapping another Auth (typically JWT):

1. Delegates to wrapped auth to get user identity (e.g., JWT sub claim)
2. Looks up roles for that user in Casbin
3. Returns roles as credentials for authorization checks

```java
// Create with model and policy files
Auth jwtAuth = new JWTSubAuthorizer();
CasbinAuth auth = CasbinAuth.create("model.conf", "policy.csv", jwtAuth);

// Or with an existing Enforcer
Enforcer enforcer = new Enforcer("model.conf", "policy.csv");
CasbinAuth auth = new CasbinAuth(enforcer, jwtAuth);

// getAuthToken returns roles: ["admin", "editor"] instead of ["user-id"]
List<String> roles = auth.getAuthToken(context);
```

### NoAuth (Development/Testing)

```java
Auth auth = new NoAuth();  // Always returns ["Token"], always authorizes
```

## Requirements

- Java 17+
- Maven 3.8+

## Documentation

See the [Wiki](https://github.com/username/meshql/wiki) for complete documentation.

## License

MIT
