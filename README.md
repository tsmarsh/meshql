# MeshQL

A modular Java framework for building scalable data applications with consistent APIs across multiple datastores.

## Overview

MeshQL enables developers to create robust data services with:

- **Multiple Database Support**: PostgreSQL, MongoDB, SQLite, in-memory
- **Dual API Endpoints**: REST (Restlette) and GraphQL (Graphlette)
- **Pluggable Authentication**: JWT, Casbin, and NoAuth implementations
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

## Requirements

- Java 17+
- Maven 3.8+

## Documentation

See the [Wiki](https://github.com/username/meshql/wiki) for complete documentation.

## License

MIT
