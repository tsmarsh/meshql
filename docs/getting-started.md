---
title: Getting Started
layout: default
nav_order: 4
---

# Getting Started

Build your first MeshQL application: a simple entity with REST and GraphQL APIs, backed by MongoDB.

---

## Prerequisites

- Java 21+
- Maven 3.8+
- Docker (for MongoDB via Testcontainers, or a running MongoDB instance)

---

## 1. Add Dependencies

```xml
<dependencies>
    <!-- Core framework -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>core</artifactId>
        <version>0.2.0</version>
    </dependency>

    <!-- Server (Jetty 12 + virtual threads) -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>server</artifactId>
        <version>0.2.0</version>
    </dependency>

    <!-- Storage backend -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>mongo</artifactId>
        <version>0.2.0</version>
    </dependency>

    <!-- Auth (choose one) -->
    <dependency>
        <groupId>com.meshql</groupId>
        <artifactId>noop</artifactId>
        <version>0.2.0</version>
    </dependency>
</dependencies>
```

---

## 2. Define Your Entity Schemas

### GraphQL Schema (`config/graph/item.graphql`)

```graphql
scalar Date

type Query {
    getById(id: ID!, at: Float): Item
    getByCategory(category: String!, at: Float): [Item]
}

type Item {
    id: ID!
    name: String!
    category: String
    price: Float
    createdAt: Date
}
```

### JSON Schema (`config/json/item.schema.json`)

```json
{
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "category": { "type": "string" },
    "price": { "type": "number", "minimum": 0 }
  },
  "required": ["name"]
}
```

---

## 3. Write the Server

```java
import com.meshql.core.*;
import com.meshql.core.config.*;
import com.meshql.auth.noop.NoAuth;
import com.meshql.repositories.mongo.*;
import com.meshql.server.Server;

public class Main {
    public static void main(String[] args) throws Exception {
        // Storage configuration
        MongoConfig storage = new MongoConfig();
        storage.uri = System.getenv().getOrDefault("MONGO_URI",
            "mongodb://localhost:27017");
        storage.db = "myapp";
        storage.collection = "items";

        int port = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "3033"));

        // Build the server configuration
        Config config = Config.builder()
            .graphlette(
                GraphletteConfig.builder()
                    .path("/item/graph")
                    .storage(storage)
                    .schema("/config/graph/item.graphql")
                    .rootConfig(
                        RootConfig.builder()
                            .singleton("getById",
                                "{\"id\": \"{{id}}\"}")
                            .vector("getByCategory",
                                "{\"payload.category\": \"{{category}}\"}")
                            .build()
                    )
                    .build()
            )
            .restlette(
                RestletteConfig.builder()
                    .path("/item/api")
                    .port(port)
                    .storage(storage)
                    .schema(jsonSchema)  // Load from file
                    .build()
            )
            .port(port)
            .build();

        // Register plugins and start
        Auth auth = new NoAuth();
        Map<String, Plugin> plugins = Map.of(
            "mongo", new MongoPlugin(auth)
        );

        Server server = new Server(plugins);
        server.init(config);

        System.out.println("MeshQL running on port " + port);
        Thread.currentThread().join();
    }
}
```

---

## 4. Run It

```bash
# Start MongoDB
docker run -d -p 27017:27017 mongo:7

# Build and run
mvn clean package
java -jar target/myapp.jar
```

---

## 5. Use It

### REST API

```bash
# Create an item
curl -X POST http://localhost:3033/item/api/ \
  -H "Content-Type: application/json" \
  -d '{"name": "Widget", "category": "tools", "price": 9.99}'

# List all items
curl http://localhost:3033/item/api/

# Read by ID
curl http://localhost:3033/item/api/{id}

# View Swagger docs
open http://localhost:3033/item/api/api-docs
```

### GraphQL API

```bash
# Query by ID
curl -X POST http://localhost:3033/item/graph \
  -H "Content-Type: application/json" \
  -d '{"query": "{ getById(id: \"ITEM_ID\") { name category price } }"}'

# Query by category
curl -X POST http://localhost:3033/item/graph \
  -H "Content-Type: application/json" \
  -d '{"query": "{ getByCategory(category: \"tools\") { name price } }"}'
```

---

## Next Steps

- **Add a second entity** with federation: see the [Farm Example](examples/farm)
- **Add authentication**: swap `NoAuth` for `JWTSubAuthorizer`
- **Try a different backend**: swap `MongoConfig` for `PostgresConfig`
- **Learn the architecture**: read [Architecture Overview](architecture/)
