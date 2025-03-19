# MeshQL SQLite Repository

This module provides a SQLite implementation of the MeshQL Repository interface. It allows you to store and retrieve data using a SQLite database, with support for authentication tokens.

## Features

- Full implementation of the Repository interface
- Persistent storage using SQLite
- In-memory database option
- Token-based authentication
- ACID transactions

## Usage

### Creating a Repository

```java
// Create a SQLite repository with a file-based database
Repository repo = SQLiteRepositoryFactory.create("path/to/database.db", "documents");

// Or create an in-memory repository
Repository repo = SQLiteRepositoryFactory.createInMemory("documents");
```

### Basic CRUD Operations

```java
// Create a document
Envelope document = new Envelope();
document.setPayload(Map.of("name", "Example", "value", 42));
List<String> tokens = List.of("token1", "token2");
Envelope created = repo.create(document, tokens);

// Read a document
Optional<Envelope> found = repo.read(created.getId(), tokens, Instant.now());

// List all documents
List<Envelope> allDocs = repo.list(tokens);

// Remove a document
Boolean removed = repo.remove(created.getId(), tokens);
```

### Bulk Operations

```java
// Create multiple documents
List<Envelope> documents = List.of(
    createEnvelope("doc1"),
    createEnvelope("doc2"),
    createEnvelope("doc3")
);
List<Envelope> created = repo.createMany(documents, tokens);

// Read multiple documents
List<String> ids = created.stream().map(Envelope::getId).collect(Collectors.toList());
List<Envelope> found = repo.readMany(ids, tokens);

// Remove multiple documents
Map<String, Boolean> results = repo.removeMany(ids, tokens);
```

## Implementation Notes

- Documents are stored in a SQLite table with JSON serialized payload
- Authentication tokens are stored as JSON arrays
- Unique constraint on (id, created_at) prevents duplicate documents
- Soft delete is used (documents are marked as deleted rather than actually removed)
- The repository implements optimistic concurrency control with automatic retry

## Dependencies

- sqlite-jdbc: JDBC driver for SQLite
- jackson-databind: JSON serialization/deserialization
- java-uuid-generator: Time-based UUID generation 