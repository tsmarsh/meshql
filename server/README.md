# MeshQL Server

The MeshQL Server module provides a deployable service that unpacks Config objects into running REST and GraphQL endpoints.

## Overview

The Server class initializes and manages:
- **REST APIs** (Restlettes) - Auto-generated CRUD endpoints with Swagger documentation
- **GraphQL APIs** (Graphlettes) - Domain-driven GraphQL queries with cross-service resolution
- **Health & Ready endpoints** - Kubernetes-compatible health checks
- **CORS support** - Cross-origin resource sharing for web clients
- **Plugin-based storage** - Pluggable database backends (MongoDB, PostgreSQL, SQLite, etc.)

## Architecture

Inspired by [meshobj](../../../meshobj), the Server uses:
- **Spark Java** as the web framework (similar to Express in meshobj)
- **Jakarta Servlets** for handling HTTP requests
- **Plugin system** for database abstraction
- **NoAuth** as default (with Casbin support planned)

## Usage

### Basic Example

```java
import com.meshql.server.Server;
import com.meshql.core.Config;
import com.meshql.core.Plugin;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // Create plugin registry
        Map<String, Plugin> plugins = new HashMap<>();
        plugins.put("memory", new MemoryPlugin());  // Example plugin

        // Create server
        Server server = new Server(plugins);

        // Initialize with configuration
        Config config = loadConfig("config.conf");  // Load your config
        server.init(config);

        // Server is now running on configured port
        // Press Ctrl+C or call server.stop() to shutdown
    }
}
```

### Configuration

The Server processes a `Config` object containing:

```java
public record Config(
    List<String> casbinParams,      // Optional: Casbin auth configuration
    List<GraphletteConfig> graphlettes,  // GraphQL endpoints
    int port,                        // Server port
    List<RestletteConfig> restlettes     // REST endpoints
)
```

### Endpoints

Once initialized, the server provides:

- **Health Check**: `GET /health` - Returns `{"status":"ok"}`
- **Ready Check**: `GET /ready` - Returns `{"status":"ok"}` (Kubernetes-compatible)
- **GraphQL endpoints**: Configured via `GraphletteConfig` (typically `POST /api/graph`)
- **REST endpoints**: Configured via `RestletteConfig` (typically `/api/rest/*`)
  - `GET /api/rest/` - List all entities
  - `POST /api/rest/` - Create entity
  - `GET /api/rest/{id}` - Read entity
  - `PUT /api/rest/{id}` - Update entity
  - `DELETE /api/rest/{id}` - Delete entity
  - `GET /api/rest/api-docs` - Swagger UI
  - `GET /api/rest/api-docs/swagger.json` - OpenAPI spec

## Implementation Details

### Servlet Adapters

The Server uses adapter classes to bridge between:
- **javax.servlet** (used by Spark/Jetty)
- **jakarta.servlet** (used by Graphlette/Restlette)

See:
- `ServletRequestAdapter` - Wraps javax.servlet.http.HttpServletRequest
- `ServletResponseAdapter` - Wraps javax.servlet.http.HttpServletResponse

### Plugin System

Storage backends are implemented as `Plugin` interface:

```java
public interface Plugin {
    Searcher createSearcher(StorageConfig config);
    Repository createRepository(StorageConfig config, Auth auth);
    void cleanUp();
}
```

Register plugins when creating the Server:

```java
Map<String, Plugin> plugins = new HashMap<>();
plugins.put("mongodb", new MongoPlugin());
plugins.put("postgres", new PostgresPlugin());
plugins.put("sqlite", new SqlitePlugin());
plugins.put("memory", new MemoryPlugin());

Server server = new Server(plugins);
```

### Authentication

Currently supports:
- **NoAuth** - Default, no authentication
- **Casbin** (planned) - Role-based access control

Configure authentication via `Config.casbinParams`.

## Shutdown

To gracefully shutdown the server:

```java
server.stop();
```

This will:
1. Stop the Spark web server
2. Clean up all registered plugins
3. Release resources

## Differences from meshobj

While inspired by meshobj, this Java implementation:
- Uses **Spark Java** instead of Express
- Uses **Jakarta Servlets** for HTTP handling
- Requires **Plugin registration** at startup
- Uses **Java 21** features (records, sealed types)
- Provides **servlet adapters** for API compatibility

## Dependencies

Key dependencies (managed in parent pom.xml):
- `spark-core` - Web framework
- `graphlette` - GraphQL endpoint generation
- `restlette` - REST API generation with Swagger
- `core` - Domain models and interfaces
- `noop` - Default authentication
- `jakarta.servlet-api` - Servlet specification
- `logback-classic` - Logging

## Future Enhancements

- [ ] Full Casbin authentication support
- [ ] WebSocket support for subscriptions
- [ ] Metrics and monitoring endpoints
- [ ] Configuration hot-reload
- [ ] SSL/TLS configuration
- [ ] Rate limiting and throttling
