---
title: "Mesher CLI"
layout: default
parent: Reference
nav_order: 6
---

# Mesher CLI

Point at a legacy database. Get a complete MeshQL anti-corruption layer service.

Mesher is a code generation tool that introspects a PostgreSQL database, uses Claude to design a clean domain model, and generates a full MeshQL project — transformers, GraphQL schemas, JSON schemas, CDC processor, Docker infrastructure, and all the wiring. The output follows the same patterns as the [Springfield Electric example](../examples/legacy).

[View source on GitHub](https://github.com/tsmarsh/meshql/tree/main/mesher){: .btn .btn-outline }

---

## Quick Start

```bash
# Build
mvn package -pl mesher -DskipTests

# Introspect a database
java -jar mesher/target/mesher-0.2.0.jar introspect \
    --jdbc-url jdbc:postgresql://localhost:5432/my_legacy_db \
    --username postgres --password postgres \
    --output introspection.json

# Convert to domain model (requires ANTHROPIC_API_KEY)
export ANTHROPIC_API_KEY=sk-ant-...
java -jar mesher/target/mesher-0.2.0.jar convert introspection.json \
    --project-name my-service \
    --output domain-model.json

# Generate the project
java -jar mesher/target/mesher-0.2.0.jar generate domain-model.json \
    --output ./generated-project

# Or do it all at once
java -jar mesher/target/mesher-0.2.0.jar run \
    --jdbc-url jdbc:postgresql://localhost:5432/my_legacy_db \
    --username postgres --password postgres \
    --project-name my-service \
    --output ./generated-project
```

---

## Pipeline

Mesher works in three stages that can run independently or together:

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   introspect    │────▶│     convert      │────▶│    generate      │
│                 │     │                  │     │                  │
│ JDBC → PG       │     │ JSON → Claude    │     │ Model → Files    │
│ → introspection │     │ → domain model   │     │ → Full project   │
│   .json         │     │   .json          │     │                  │
└─────────────────┘     └──────────────────┘     └──────────────────┘
      No API key           Requires API key          No API key
```

Splitting the pipeline means you can:
- **Introspect** multiple databases and compare their schemas
- **Edit** the domain model before generating (adjust names, add fields, change transformations)
- **Regenerate** from the same model with different options
- **Version control** the domain model as part of your project

---

## Commands

### introspect

Connect to a PostgreSQL database and extract its schema metadata.

```bash
java -jar mesher.jar introspect \
    --jdbc-url jdbc:postgresql://host:5432/dbname \
    --username user \
    --password pass \
    --schema public \
    --output introspection.json
```

| Option | Required | Default | Description |
|:-------|:---------|:--------|:------------|
| `--jdbc-url` | Yes | | JDBC connection URL |
| `--username`, `-u` | No | | Database username |
| `--password`, `-p` | No | | Database password |
| `--schema`, `-s` | No | `public` | Schema to introspect |
| `--output`, `-o` | No | stdout | Output file path |

Extracts: tables, columns (with types, nullability, defaults), primary keys, foreign keys, unique constraints, check constraints. All queries filter `pg_catalog` and `information_schema`, results are ordered deterministically.

### convert

Send introspection data to Claude to produce a domain model.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
java -jar mesher.jar convert introspection.json \
    --project-name springfield-electric \
    --package com.meshql.examples.legacy \
    --port 4066 \
    --output domain-model.json
```

| Option | Required | Default | Description |
|:-------|:---------|:--------|:------------|
| `<file>` | Yes | | Path to introspection.json |
| `--project-name` | Yes | | Project name (used in Docker, pom.xml) |
| `--package` | No | `com.meshql.examples.legacy` | Java package name |
| `--port` | No | `4066` | Server port |
| `--output`, `-o` | No | stdout | Output file path |

Claude analyzes column names, types, FK relationships, and constraint patterns to produce:
- **Clean names**: `cust_acct` → `customer`, `cust_nm_first` → `first_name`
- **Transformations**: title case, date parsing, cents-to-dollars, code expansion
- **Code maps**: `{A: active, S: suspended, C: closed}`
- **Relationships**: parent/child detection from foreign keys
- **Processing phases**: topological ordering for FK resolution

### generate

Generate a complete MeshQL project from a domain model.

```bash
java -jar mesher.jar generate domain-model.json --output ./my-project
```

| Option | Required | Default | Description |
|:-------|:---------|:--------|:------------|
| `<file>` | Yes | | Path to domain-model.json |
| `--output`, `-o` | Yes | | Output directory |

### run

All three steps in one command.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
java -jar mesher.jar run \
    --jdbc-url jdbc:postgresql://host:5432/dbname \
    --username user --password pass \
    --project-name my-service \
    --port 4066 \
    --output ./my-project
```

Saves the intermediate `domain-model.json` in the output directory for inspection.

---

## Generated Project Structure

```
my-project/
├── config/
│   ├── graph/{entity}.graphql            # GraphQL schema per entity
│   └── json/{entity}.schema.json         # JSON Schema per entity (REST validation)
├── src/main/java/com/.../
│   ├── Main.java                         # Server + processor bootstrap
│   ├── LegacyTransformer.java            # Transformer interface
│   ├── LegacyToCleanProcessor.java       # Phased CDC processor
│   ├── IdResolver.java                   # Legacy→MeshQL ID resolution cache
│   └── {Entity}Transformer.java          # One per entity
├── legacy-db/init.sql                    # DDL + REPLICA IDENTITY + publication
├── debezium/application.properties       # CDC connector config
├── veriforged/schema.pl + gen.toml       # Test data generation
├── docker-compose.yml                    # Full stack
├── Dockerfile                            # Multi-stage build
├── nginx.conf                            # Reverse proxy
└── pom.xml                               # Maven project
```

Every generated file follows the patterns from `examples/legacy/`. The generated project can be built and run with `docker compose up`.

---

## Domain Model Schema

The intermediate representation (IR) is a JSON file that Claude produces and the generators consume. Key sections:

### Entity

```json
{
  "legacyTable": "cust_acct",
  "cleanName": "customer",
  "className": "Customer",
  "legacyPrimaryKey": "acct_id",
  "legacyIdField": "legacy_acct_id",
  "isRoot": true,
  "processingPhase": 1,
  "fields": [...],
  "codeMaps": [...],
  "relationships": { "children": [...], "parents": [...] },
  "queries": { "singletons": [...], "vectors": [...] }
}
```

| Field | Description |
|:------|:------------|
| `legacyTable` | Original table name (lowercase) |
| `cleanName` | Clean entity name (snake_case) |
| `className` | Java class name (PascalCase) |
| `legacyPrimaryKey` | Original PK column name |
| `legacyIdField` | Clean field name for the legacy ID (e.g., `legacy_acct_id`) |
| `isRoot` | True if entity has no FK to other entities |
| `processingPhase` | CDC processing order (parents before children) |

### Field Transformations

| Transformation | Input | Output | When Assigned |
|:--------------|:------|:-------|:-------------|
| `direct` | `acct_num` | `account_number` | Default for most columns |
| `titleCase` | `MARGARET` | `Margaret` | Name/address columns |
| `toLower` | `EMAIL@CAPS.COM` | `email@caps.com` | Email columns |
| `parseDate` | `19980315` | `1998-03-15` | `VARCHAR(8)` date columns |
| `flagToBoolean` | `Y` | `true` | `_flg` suffix columns |
| `centsToDouble` | `9840` | `98.40` | Amount columns stored as integers |
| `codeMap` | `A` | `active` | Short VARCHAR with `_cd`, `_type` suffix |

### Processing Phases

Entities are processed in dependency order so FK resolution works:

```json
"processingPhases": [
  {"phase": 1, "entities": ["cust_acct"], "cachePopulation": ["customer"]},
  {"phase": 2, "entities": ["bill_hdr"], "cachePopulation": ["bill"]},
  {"phase": 3, "entities": ["mtr_rdng", "pymt_hist"], "cachePopulation": []}
]
```

Phase 1 processes root entities (no FKs). After processing, GraphQL is queried to populate the legacy→MeshQL ID cache. Phase 2 processes entities with FKs to phase-1 entities. Phase 3 processes entities with FKs to phase-2 entities. Then continuous consumption of all topics begins.

---

## Code Generation Templates

Mesher uses 15 Handlebars templates with custom helpers:

| Template | Generates | Per-Entity |
|:---------|:----------|:-----------|
| `graphql-schema.hbs` | `config/graph/{entity}.graphql` | Yes |
| `json-schema.hbs` | `config/json/{entity}.schema.json` | Yes |
| `transformer.java.hbs` | `{Entity}Transformer.java` | Yes |
| `main.java.hbs` | `Main.java` | No |
| `legacy-transformer.java.hbs` | `LegacyTransformer.java` | No |
| `processor.java.hbs` | `LegacyToCleanProcessor.java` | No |
| `id-resolver.java.hbs` | `IdResolver.java` | No |
| `pom.xml.hbs` | `pom.xml` | No |
| `docker-compose.yml.hbs` | `docker-compose.yml` | No |
| `Dockerfile.hbs` | `Dockerfile` | No |
| `nginx.conf.hbs` | `nginx.conf` | No |
| `debezium-application.properties.hbs` | `debezium/application.properties` | No |
| `init.sql.hbs` | `legacy-db/init.sql` | No |
| `veriforged-schema.pl.hbs` | `veriforged/schema.pl` | No |
| `veriforged-gen.toml.hbs` | `veriforged/gen.toml` | No |

Custom Handlebars helpers: `pascalCase`, `camelCase`, `upperSnake`, `eq`, `neq`.

---

## How It Relates to the Legacy Example

The [Springfield Electric example](../examples/legacy) was built by hand. Mesher automates the same process:

| What | Legacy Example | Mesher |
|:-----|:---------------|:-------|
| Schema analysis | Human reads DDL, designs clean model | Claude analyzes introspection data |
| Naming decisions | Human chooses `cust_acct` → `customer` | Claude infers from patterns |
| Transformation rules | Human writes `titleCase`, `parseDate` | Claude assigns based on column types |
| Code map values | Human maps `A` → `active` | Claude infers from check constraints and naming |
| Processing phases | Human determines FK dependency order | Claude topologically sorts entities |
| Generated code | Hand-written | Template-generated, same patterns |

The generated output is structurally identical to the legacy example. The intent is that Mesher gets you 90% of the way — you can then refine the domain model JSON and regenerate, or edit the generated code directly.

[Back to Reference](/meshql/reference){: .btn .btn-outline }
