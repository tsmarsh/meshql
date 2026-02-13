# Mesher

Point at a legacy database. Get a complete MeshQL anti-corruption layer service.

Mesher is a CLI tool that introspects a PostgreSQL database, uses Claude to design a clean domain model, and generates a full MeshQL project — transformers, GraphQL schemas, JSON schemas, CDC processor, Docker infrastructure, and all the wiring.

## What It Does

You have a legacy database with `CUST_NM_FIRST`, `STAT_CD`, `TOT_AMT` in cents, and dates stored as `VARCHAR(8)`. You want a clean API that serves `first_name`, `status: "active"`, `total_amount: 98.40`, and ISO dates.

Mesher generates the entire translation layer:

```
Legacy PostgreSQL → introspect → Claude → domain model → code generation → Complete MeshQL service
```

The output is a ready-to-run project with the same structure as the [legacy example](../examples/legacy/) — Debezium CDC pipeline, Kafka, entity transformers, phased processing, internal federation resolvers, Docker Compose, and all config files.

## Commands

| Command | What It Does | Requires API Key |
|:--------|:-------------|:----------------|
| `introspect` | Connect to PostgreSQL, extract schema metadata | No |
| `convert` | Send introspection data to Claude, get domain model | Yes |
| `generate` | Generate full project from domain model | No |
| `run` | All three steps in one | Yes |

### Introspect

Connect to a PostgreSQL database and extract its schema — tables, columns, primary keys, foreign keys, unique constraints, check constraints.

```bash
java -jar mesher.jar introspect \
    --jdbc-url jdbc:postgresql://localhost:5432/springfield_electric \
    --username postgres --password postgres \
    --output introspection.json
```

Output is a JSON file with the full schema metadata. No API key needed — this is pure JDBC.

### Convert

Send the introspection data to Claude, which analyzes the schema and produces a domain model — clean names, field transformations, code maps, relationships, processing phases.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
java -jar mesher.jar convert introspection.json \
    --project-name springfield-electric \
    --package com.meshql.examples.legacy \
    --port 4066 \
    --output domain-model.json
```

The domain model is the intermediate representation that drives code generation. You can edit it before generating.

### Generate

Generate a complete MeshQL project from the domain model.

```bash
java -jar mesher.jar generate domain-model.json --output ./generated-project
```

### Run

All three steps in one command:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
java -jar mesher.jar run \
    --jdbc-url jdbc:postgresql://localhost:5432/springfield_electric \
    --username postgres --password postgres \
    --project-name springfield-electric \
    --output ./generated-project
```

## What Gets Generated

```
generated-project/
├── config/
│   ├── graph/
│   │   ├── customer.graphql          # GraphQL schema per entity
│   │   ├── bill.graphql
│   │   ├── meter_reading.graphql
│   │   └── payment.graphql
│   └── json/
│       ├── customer.schema.json      # JSON Schema per entity (REST validation)
│       ├── bill.schema.json
│       ├── meter_reading.schema.json
│       └── payment.schema.json
├── src/main/java/com/.../
│   ├── Main.java                     # Server + processor bootstrap
│   ├── LegacyTransformer.java        # Transformer interface
│   ├── LegacyToCleanProcessor.java   # Phased CDC processor
│   ├── IdResolver.java               # Legacy-to-MeshQL ID cache
│   ├── CustomerTransformer.java      # Per-entity transformer
│   ├── BillTransformer.java
│   ├── MeterReadingTransformer.java
│   └── PaymentTransformer.java
├── legacy-db/
│   └── init.sql                      # DDL + REPLICA IDENTITY + publication
├── debezium/
│   └── application.properties        # Debezium CDC config
├── veriforged/
│   ├── schema.pl                     # Prolog schema for test data
│   └── gen.toml                      # Generation config
├── docker-compose.yml                # Full stack: PG, Mongo, Kafka, Debezium, app
├── Dockerfile                        # Multi-stage build
├── nginx.conf                        # Reverse proxy
└── pom.xml                           # Maven project
```

Every file follows the patterns established in `examples/legacy/`.

## Domain Model (IR)

The intermediate representation is a JSON file that Claude produces and the generators consume. You can inspect, edit, or handcraft it.

```json
{
  "projectName": "springfield-electric",
  "package": "com.meshql.examples.legacy",
  "port": 4066,
  "prefix": "legacy",
  "legacyDbName": "springfield_electric",
  "entities": [{
    "legacyTable": "cust_acct",
    "cleanName": "customer",
    "className": "Customer",
    "legacyPrimaryKey": "acct_id",
    "legacyIdField": "legacy_acct_id",
    "isRoot": true,
    "processingPhase": 1,
    "fields": [{
      "legacyName": "acct_num",
      "cleanName": "account_number",
      "legacyType": "VARCHAR(12)",
      "cleanType": "String",
      "graphqlType": "String",
      "jsonSchemaType": "string",
      "required": true,
      "transformation": "direct"
    }],
    "codeMaps": [{
      "legacyColumn": "stat_cd",
      "cleanField": "status",
      "mapping": {"A": "active", "S": "suspended", "C": "closed"}
    }],
    "relationships": {
      "children": [{"targetEntity": "meter_reading", "fieldName": "meterReadings",
                     "foreignKeyInChild": "customer_id", "queryName": "getByCustomer"}],
      "parents": []
    },
    "queries": {
      "singletons": [{"name": "getById", "template": "{\"id\": \"{{id}}\"}"}],
      "vectors": [{"name": "getAll", "template": "{}"}]
    }
  }],
  "processingPhases": [
    {"phase": 1, "entities": ["cust_acct"], "cachePopulation": ["customer"]}
  ]
}
```

### Field Transformations

Claude assigns one of these transformations to each field based on column type, naming patterns, and check constraints:

| Transformation | What It Does | Example |
|:--------------|:-------------|:--------|
| `direct` | Copy as-is | `acct_num` → `account_number` |
| `titleCase` | UPPERCASE → Title Case | `MARGARET` → `Margaret` |
| `toLower` | Lowercase | `EMAIL@CAPS.COM` → `email@caps.com` |
| `parseDate` | YYYYMMDD → ISO 8601 | `19980315` → `1998-03-15` |
| `flagToBoolean` | Y/N → true/false | `Y` → `true` |
| `centsToDouble` | Integer cents → float dollars | `9840` → `98.40` |
| `codeMap` | Code → expanded word | `A` → `active` |

## Architecture

```
┌─────────────────┐
│  introspect     │ JDBC → PostgreSQL → IntrospectionResult
├─────────────────┤
│  convert        │ IntrospectionResult → Claude API → DomainModel
├─────────────────┤
│  generate       │ DomainModel → Handlebars templates → Project files
└─────────────────┘
```

**Introspection** uses `information_schema` queries (ported from [veriforged](../../veriforged)) to extract tables, columns, PKs, FKs, unique constraints, and check constraints.

**Conversion** sends the introspection JSON to Claude with a detailed prompt specifying the exact domain model schema. Claude analyzes naming patterns, column types, FK relationships, and constraint hints to produce clean names, transformation assignments, code maps, and processing phase ordering.

**Generation** uses 15 Handlebars templates with custom helpers (`pascalCase`, `camelCase`, `upperSnake`, `eq`, `neq`) to produce all project files.

## Build

```bash
# Build the module
mvn package -pl mesher

# Run the CLI
java -jar mesher/target/mesher-0.2.0.jar --help
```

## Tests

```bash
# Unit tests (no external dependencies)
mvn test -pl mesher

# Integration test with Testcontainers (requires Docker)
TESTCONTAINERS=1 mvn test -pl mesher
```

19 tests covering model deserialization, response parsing, SQL query validation, and full project generation with file content assertions.

## Demo

The [`demo/`](demo/) directory contains a complete walkthrough using the [WRI Global Power Plant Database](https://datasets.wri.org/dataset/globalpowerplantdatabase) — 34,936 power plants across 167 countries loaded into a deliberately ugly legacy schema with `CNTRY_REF`, `PWR_PLT`, `PLT_FUEL_ASGN`, `GEN_DATA` tables.

```bash
cd mesher/demo
docker compose up -d    # Start legacy DB + load 34,936 plants
# ... then run mesher against it
```

See [demo/README.md](demo/README.md) for the full walkthrough.
