# Egg Economy Salesforce: Anti-Corruption Layer for Salesforce Migration

Demonstrates using MeshQL as a **transitional architecture** for migrating off Salesforce — extracting your data and business logic from a platform that was never designed to let go.

## The Enterprise Problem

Salesforce orgs grow organically. What starts as a CRM becomes the de facto application platform: custom objects (`__c`), Process Builder flows, Apex triggers, validation rules, formula fields — all tightly coupled to the Salesforce runtime. Eventually the org reaches a point where:

- **Platform costs scale with headcount**, not with value delivered — governor limits force architectural compromises
- **Custom object sprawl** means your data model is locked inside a proprietary schema with 18-character IDs and picklist dependencies
- **Integration complexity** compounds: every connected system must speak Salesforce's API, handle its pagination, respect its rate limits, and decode its naming conventions
- **Org migrations** (merging acquired companies, splitting business units) become multi-quarter projects because the data model is the platform

The anti-corruption layer lets you **decouple from Salesforce incrementally**. New applications consume a clean API. Salesforce data flows through automatically via CDC. When you're ready to cut over, the clean side is already running.

## Architecture

```
Salesforce PostgreSQL (CamelCase__c tables, 18-char IDs, picklist values)
  → Debezium CDC (zero-impact replication via WAL)
    → Kafka (10 topics, one per legacy table)
      → LegacyToCleanProcessor (5-phase consumer)
        → 10 transformers (Salesforce naming → clean domain)
        → MeshQL REST API → MongoDB (clean domain model)
        → Inline projection updates
          → 3 frontend apps (built against clean API, zero Salesforce knowledge)
```

**Salesforce is never modified.** It continues to serve existing users, workflows, and reports. New applications are built exclusively against the clean MeshQL API. When Salesforce is decommissioned, the clean side doesn't change — you swap the data source, not the applications.

## What Gets Transformed

| Salesforce (Legacy) | Clean (MeshQL) | Transformation |
|:--------------------|:---------------|:---------------|
| `Farm__c.Id: "a0B5f000001ABC01"` | `legacy_sf_id: "a0B5f000001ABC01"` | Preserved for traceability |
| `Farm__c.Farm_Type__c: "Megafarm"` | `farm_type: "megafarm"` | Picklist to lowercase |
| `Coop__c.Farm__c: "a0B5f000001ABC01"` | `farm_id: <meshql-uuid>` | SF lookup → ID cache resolution |
| `Hen__c.Breed__c: "Rhode Island Red"` | `breed: "Rhode Island Red"` | Picklist passthrough |
| `Hen__c.Status__c: "Active"` | `status: "active"` | Picklist to lowercase |
| `Lay_Report__c.Quality__c: "Double Yolk"` | `quality: "Double Yolk"` | Picklist passthrough |
| `Storage_Deposit__c.Source_Type__c: "Farm"` | `source_type: "farm"` | Picklist to lowercase |
| `Consumption_Report__c.Purpose__c: "Baking"` | `purpose: "baking"` | Picklist to lowercase |
| `*.IsDeleted: true` | *(filtered out)* | Salesforce soft-delete filter |
| `*.CreatedDate` | `createdAt` | ISO timestamp passthrough |

Salesforce naming conventions are more readable than SAP's, but the coupling is deeper: 18-character opaque IDs, lookup relationships as foreign key columns named after the parent object, picklist values that drive Apex logic, and `IsDeleted` soft-delete semantics that every consumer must understand.

## Salesforce Table Mapping

| Clean Entity | SF Custom Object | Relationship | Key Custom Fields |
|:--|:--|:--|:--|
| Farm | Farm__c | Top-level (has OwnerId) | Farm_Type__c, Zone__c, Owner_Name__c |
| Coop | Coop__c | Master-Detail to Farm | Farm__c(MD), Capacity__c, Coop_Type__c |
| Hen | Hen__c | Master-Detail to Coop | Coop__c(MD), Breed__c, Date_of_Birth__c, Status__c |
| Container | Storage_Container__c | Top-level (has OwnerId) | Container_Type__c, Capacity__c, Zone__c |
| Consumer | Consumer_Account__c | Top-level (has OwnerId) | Consumer_Type__c, Zone__c, Weekly_Demand__c |
| LayReport | Lay_Report__c | Lookup (has OwnerId) | Name(auto-number), Hen__c, Farm__c, Egg_Count__c, Quality__c |
| StorageDeposit | Storage_Deposit__c | Lookup (has OwnerId) | Name(auto-number), Container__c, Source_Type__c, Egg_Count__c |
| StorageWithdrawal | Storage_Withdrawal__c | Lookup (has OwnerId) | Name(auto-number), Container__c, Egg_Count__c, Reason__c |
| ContainerTransfer | Container_Transfer__c | Lookup (has OwnerId) | Name(auto-number), Source_Container__c, Dest_Container__c, Egg_Count__c |
| ConsumptionReport | Consumption_Report__c | Lookup (has OwnerId) | Name(auto-number), Consumer__c, Container__c, Egg_Count__c, Purpose__c |

All tables include Salesforce standard fields: Id (18-char), Name, CreatedDate, CreatedById, LastModifiedDate, LastModifiedById, SystemModstamp, IsDeleted.

## Schema Fidelity

This example models a realistic Salesforce org as it would appear after replication to PostgreSQL (e.g., via Heroku Connect or ETL). The schema follows real Salesforce conventions:

- **Standard fields on every object** — CreatedDate, CreatedById, LastModifiedDate, LastModifiedById, SystemModstamp, IsDeleted. Tools like Heroku Connect depend on SystemModstamp for sync; its absence would be immediately obvious to a Salesforce developer.
- **OwnerId modeled per relationship type** — Top-level objects and Lookup children have their own OwnerId. Master-Detail children (Coop, Hen) do not — they inherit ownership from their master, which is how Salesforce actually enforces it.
- **Master-Detail vs Lookup relationships** — Coop is Master-Detail to Farm (cascade delete, shared owner, required FK). Hen is Master-Detail to Coop. Transaction records use Lookup relationships (independent ownership, optional cascading).
- **Auto-number Name fields** on transaction records — `LR-00001`, `SD-00001`, `SW-00001`, `CT-00001`, `CR-00001`. This is the standard Salesforce pattern for objects where users don't manually name records.
- **18-character Salesforce IDs** with correct key prefixes per object type — `a0B` for Farm, `a0C` for Coop, `a0D` for Hen, etc. User IDs use the `005` prefix, which is the real Salesforce key prefix for User records.
- **Picklist values in Title Case** — `Megafarm`, `Free Range`, `Grade A`, `Double Yolk`. These mirror how picklist values appear in the Salesforce UI and API.
- **Lowercase table and column names** — PostgreSQL lowercases unquoted identifiers. This matches what tools like Heroku Connect produce when replicating Salesforce objects to PostgreSQL.

## CDC in Production: Getting Data Out of Salesforce

This example uses Debezium on a PostgreSQL replica to keep things simple and self-contained. In a real Salesforce integration, you have several options — including Salesforce's own CDC platform feature, which is one of the more mature vendor-provided CDC implementations available.

### Salesforce's Native CDC

**Change Data Capture** is a first-class Salesforce platform feature. When enabled for an object, Salesforce monitors DML operations and publishes change events — JSON payloads containing the operation type (CREATE, UPDATE, DELETE, UNDELETE), the changed fields, and metadata including the committing user, transaction key, and commit timestamp.

Events are published to the Salesforce Event Bus and consumed via two APIs:

- **Pub/Sub API** (recommended): gRPC over HTTP/2 with Avro-encoded payloads. Supports `Subscribe` (pull-based streaming), `ManagedSubscribe` (server-side replay tracking, open beta), and `Publish`. Proto definition at [github.com/forcedotcom/pub-sub-api](https://github.com/forcedotcom/pub-sub-api).

- **CometD / Bayeux** (legacy): HTTP long-polling with JSON payloads. Channel format: `/data/ChangeEvents` (all selected entities) or `/data/<Object>ChangeEvent` (single entity). Salesforce provides the [EMP Connector](https://github.com/forcedotcom/EMP-Connector) as a Java reference implementation.

**What a change event looks like:**

```json
{
  "ChangeEventHeader": {
    "entityName": "Farm__c",
    "changeType": "CREATE",
    "recordIds": ["a0B5f000001ABC01"],
    "commitUser": "005RM000001vI54mYAE",
    "commitTimestamp": 1569443783000,
    "transactionKey": "00051c2e-a75a-3f97-03fc-cdf4e16d9d3c",
    "changedFields": [],
    "changeOrigin": "com/salesforce/api/soap/47.0"
  },
  "Name": "Sunny Valley Megafarm",
  "Farm_Type__c": "Megafarm",
  "Zone__c": "north"
}
```

For CREATE events, all writable fields are included. For UPDATE, only changed fields (the `changedFields` array lists which ones). For DELETE, only the record ID appears in the header.

**Allocations and limits:**

| | Enterprise Edition | Performance/Unlimited |
|:--|:--|:--|
| Max selected entities | 5 | 5 |
| Max event notifications / 24 hours | 25,000 | 50,000 |

With the CDC add-on license, the entity limit is removed and the 24-hour cap rises to 125,000–150,000. Change events are retained for 72 hours on the Event Bus, allowing replay within that window via `replayId`.

**Gap and overflow events**: When Salesforce can't generate normal change events (errors, overload), it sends gap events with no record data — subscribers must query Salesforce directly to reconcile. Transactions exceeding 100,000 changes produce overflow events after the first 100K.

### Event Relay to AWS

**Salesforce Event Relay** is a managed, no-code service that forwards CDC events (and Platform Events) to Amazon EventBridge. From there, you can route to Lambda, SQS, S3, Kinesis, or Step Functions. The relay handles health monitoring, checkpoint tracking, and automatic resume after connectivity issues. This is the simplest path for AWS-native architectures.

### Debezium and Salesforce

**Debezium does not have a Salesforce connector.** Salesforce is a SaaS platform with no direct database access — Debezium's log-based CDC model (reading WAL/binlog/redo logs) doesn't apply. Getting Salesforce CDC data into Kafka requires a different connector.

### Kafka Connect for Salesforce

The **Confluent Salesforce CDC Source Connector** is the primary way to get Salesforce change events into Kafka:

- Uses Salesforce Streaming API v45.0+ (CometD/Bayeux) under the hood
- Subscribes to change event channels (e.g., `/data/Farm__cChangeEvent`)
- Authentication via OAuth2, username/password + security token, or JWT bearer
- Delivery guarantee: at-least-once (may produce duplicates on restart)
- Limited to 1 task (no parallelism) — subject to Salesforce rate limits
- Available on both Confluent Cloud (fully managed) and Confluent Platform (self-managed)

### Other Integration Options

| Solution | Type | Latency | Best For |
|:---------|:-----|:--------|:---------|
| **Pub/Sub API** (direct) | gRPC streaming | Near real-time | Custom consumers in any language (Java, Python, Go, etc.) |
| **Confluent SF CDC Connector** | Kafka Connect | Near real-time | Kafka-native architectures |
| **Event Relay → EventBridge** | Managed relay | Near real-time | AWS-native, no code |
| **MuleSoft** (Salesforce-owned) | iPaaS | Near real-time | Replay Channel Listener with checkpoint persistence |
| **Heroku Connect** | Managed sync | Eventual (polling) | Heroku Postgres mirror of Salesforce data |
| **Fivetran / Airbyte** | Batch sync | 5 min – 24 hours | Data warehouse loading (Snowflake, BigQuery) |

### What This Example Models

This example simulates the Salesforce scenario using a PostgreSQL database with Salesforce naming conventions and Debezium for CDC. In a production deployment, you would replace the Debezium step with one of the approaches above — most likely the Pub/Sub API for a custom consumer, or the Confluent connector for a Kafka-based architecture. The anti-corruption layer (the transformers, the phased processor, the clean MeshQL API) remains identical regardless of how the change events arrive.

## Processing Phases

FK dependencies between entities require ordered processing. The processor drains each phase's Kafka topics completely before advancing:

1. **Phase 1**: Farm, Container, Consumer — root entities with no FK dependencies
2. **Phase 2**: Coop — depends on Farm (resolves `Farm__c` lookup → `farm_id`)
3. **Phase 3**: Hen — depends on Coop (resolves `Coop__c` lookup → `coop_id`)
4. **Phase 4**: All 5 event topics — depend on all actors, plus inline projection updates
5. **Phase 5**: Continuous consumption of all 10 topics for ongoing CDC

## Data Ownership

Before discussing migration stages, it's worth being explicit about what the anti-corruption layer does **not** do: it does not transfer data ownership.

During Stage 1 and Stage 2, Salesforce remains the **system of record**. Every Process Builder flow, Apex trigger, validation rule, approval process, report, dashboard, and connected integration that depends on Salesforce continues to depend on Salesforce. The clean MeshQL API is a **collection and presentation layer** — it gives new applications a modern interface to Salesforce's data, and it accepts input from new frontends, but it does not replace Salesforce's authority over that data.

This matters more than it sounds. Salesforce orgs accumulate years of automation: record-triggered flows, field updates, sharing rules, roll-up summary fields, cross-object formula fields. These automations expect data to enter through Salesforce's DML layer. If you bypass Salesforce and write directly to your own store, none of that automation fires. Roll-up summaries go stale. Approval processes never trigger. Compliance reports show gaps.

The anti-corruption layer's job is to **decouple the interface from the implementation**. New applications interact with a clean API. Whether that API is backed by Salesforce, by MeshQL's own storage, or by something else entirely is invisible to those applications. Data ownership is an organizational and governance decision, not a technical one, and the architecture must respect that decision at every stage.

## Migration Strategy

This example demonstrates a three-stage platform replacement path:

### Stage 1: Shadow (this example)

Salesforce remains the system of record. CDC replicates changes in real-time to the clean API. New applications read from MeshQL. Existing Salesforce users, reports, dashboards, and workflows continue unchanged. The new system **presents everything and collects nothing**. Zero risk, immediate value.

### Stage 2: Bidirectional Sync

New applications begin accepting writes through the clean MeshQL API. But **data ownership remains with Salesforce**. Every write to the clean API is published to a Kafka topic, consumed by a write-back service that translates the clean domain model back into Salesforce's native format and writes to Salesforce via its standard APIs. The change then flows back through CDC — Salesforce → CDC → Kafka → forward transformer → MongoDB — completing the circle.

```
Stage 2 Architecture:

New frontend → MeshQL REST API → MongoDB (immediate read-after-write)
                               → Kafka "clean.{entity}" topics
                                   → CleanToSalesforceProcessor (reverse transformer)
                                     → Salesforce REST API / Composite API
                                       → Salesforce org (triggers, flows, validation rules fire)
                                         → SF CDC → Kafka → forward pipeline → MongoDB (confirmed)
```

This means the clean system **collects and presents**. It collects user input through a modern API and presents a unified view of the domain. But it does not own the data. Salesforce's validation rules, Apex triggers, Process Builder flows, and sharing model still execute on every write. If Salesforce rejects a write — a required field missing, a validation rule failing, an Apex trigger throwing — the rejection is surfaced to the caller. The CDC round-trip confirms successful writes by updating the clean-side record with Salesforce-assigned values (18-character IDs, auto-number Names, SystemModstamp).

This has a critical benefit for license planning: **new applications don't need Salesforce licenses**. Users interact with the clean API. Only the write-back service needs a Salesforce integration user — a single API-only license, not per-seat licensing.

### Stage 3: Cutover

Data ownership transfers to MeshQL — **custom object by custom object, not all at once**. For each object, you verify that every Salesforce consumer (reports, dashboards, flows, connected apps, integration users) has been migrated to the clean API. Then you decommission the write-back consumer for that entity, retire its CDC topic, and disable CDC on that object in Salesforce Setup. MeshQL becomes the sole system of record for that entity.

Each custom object retired is a reduction in Salesforce platform complexity and a step toward reducing license scope. When the last object is migrated, the org can be decommissioned entirely.

The critical advantage: **you stop building on Salesforce on day one.** Every feature built against the clean API is a feature that doesn't need to be migrated later. Stage 2 gives new applications write access without transferring ownership. Stage 3 is the only step that requires organizational commitment — and it happens one object at a time.

## Writing Back to Salesforce

Stage 2 requires a reverse path: clean domain writes must reach Salesforce through Salesforce's standard APIs, respecting Salesforce's validation rules, triggers, and sharing model. The write-back is topic-driven — each clean domain entity has a corresponding Kafka topic, and a write-back consumer translates and forwards to Salesforce.

### Salesforce's Write Interfaces

**REST API (sObject CRUD)** is the most natural write path. Salesforce's REST API provides standard CRUD on any sObject:
- `POST /services/data/vXX.0/sobjects/Farm__c/` — create (returns 18-char ID)
- `PATCH /services/data/vXX.0/sobjects/Farm__c/{id}` — update
- `DELETE /services/data/vXX.0/sobjects/Farm__c/{id}` — delete

Authentication via OAuth 2.0 JWT bearer flow (server-to-server, no interactive login). Each successful create returns the new record's 18-character Salesforce ID, which populates the reverse ID cache for FK resolution on dependent writes.

**Composite API** batches up to 25 subrequests in a single HTTP call with dependency ordering. This is ideal for creating parent-child hierarchies in a single round-trip, with reference IDs linking dependent records:

```json
{
  "compositeRequest": [
    {
      "method": "POST",
      "url": "/services/data/v59.0/sobjects/Farm__c",
      "referenceId": "newFarm",
      "body": { "Name": "New Farm", "Farm_Type__c": "Megafarm", "Zone__c": "north" }
    },
    {
      "method": "POST",
      "url": "/services/data/v59.0/sobjects/Coop__c",
      "referenceId": "newCoop",
      "body": { "Farm__c": "@{newFarm.id}", "Name": "Coop A", "Capacity__c": 50 }
    }
  ]
}
```

**Bulk API 2.0** for high-volume write-back (>2000 records per batch). Asynchronous, job-based — submit a CSV, poll for completion. Best for batch reconciliation or initial load, not real-time write-back.

**Platform Events** offer the most Salesforce-native, decoupled approach. Define custom Platform Events in Salesforce (e.g., `Farm_Sync__e`), publish from the write-back consumer, and let Salesforce-side automation (Apex triggers, Flows) handle the actual DML. This keeps business logic in Salesforce where Salesforce admins can maintain it — a significant advantage in organizations where the Salesforce team and the development team are separate groups.

### Which Interface for Which Write Pattern

| Pattern | Recommended Interface | Rationale |
|:--|:--|:--|
| Single record creates/updates | REST API | Simple, synchronous, immediate ID return |
| Parent-child hierarchies | Composite API | Dependency ordering in a single call, reference IDs for FK resolution |
| High-volume batch | Bulk API 2.0 | Async job processing, handles governor limits internally |
| Decoupled with SF business logic | Platform Events | Salesforce-side triggers handle validation and side effects |

### Write-Back Topic Mapping

Each clean domain topic maps to a Salesforce sObject. The write-back consumer reverses the forward transformer's logic: lowercase values back to Title Case picklist values, MeshQL UUIDs back to 18-character Salesforce IDs via a reverse ID cache.

| Clean Topic | SF sObject | Key Reverse Transforms |
|:--|:--|:--|
| `clean.farm` | Farm__c | farm_type → Farm_Type__c ("megafarm"→"Megafarm"), zone → Zone__c |
| `clean.coop` | Coop__c | farm_id → Farm__c (reverse cache → 18-char SF ID) |
| `clean.hen` | Hen__c | coop_id → Coop__c, status → Status__c ("active"→"Active") |
| `clean.container` | Storage_Container__c | container_type → Container_Type__c ("barn"→"Barn") |
| `clean.consumer` | Consumer_Account__c | consumer_type → Consumer_Type__c ("household"→"Household") |
| `clean.lay_report` | Lay_Report__c | hen_id → Hen__c, farm_id → Farm__c, quality → Quality__c |
| `clean.storage_deposit` | Storage_Deposit__c | container_id → Container__c, source_type → Source_Type__c |
| `clean.storage_withdrawal` | Storage_Withdrawal__c | container_id → Container__c |
| `clean.container_transfer` | Container_Transfer__c | source/dest container_id → Source/Dest_Container__c |
| `clean.consumption_report` | Consumption_Report__c | consumer_id → Consumer__c, purpose → Purpose__c |

### Error Handling and Governor Limits

Salesforce API calls return structured error responses with an `errorCode` and `message`. Common failure modes:

- **FIELD_CUSTOM_VALIDATION_EXCEPTION**: A validation rule rejected the data. Surface the message to the caller.
- **REQUIRED_FIELD_MISSING**: A required field wasn't populated. The reverse transformer has a mapping gap.
- **DUPLICATE_VALUE**: An external ID or unique field constraint was violated.
- **REQUEST_LIMIT_EXCEEDED**: API call limits hit. Back off and retry with exponential delay.

Salesforce enforces API call limits per 24-hour period (varies by edition — 100,000 for Enterprise, 500,000 for Unlimited). The write-back consumer must track usage and throttle accordingly. For sustained high-volume write-back, Platform Events or Bulk API are preferable to individual REST calls.

Salesforce also enforces per-transaction governor limits on the receiving end: 150 DML statements per transaction, 100 SOQL queries, 6MB heap size. These are Salesforce's problem — they affect Apex triggers and flows that fire on the incoming DML — but they can cause write-back failures if the org's automation is heavy. Monitoring via the Salesforce Event Monitoring API or Login History helps catch these before they become systemic.

## Running

```bash
cd examples/egg-economy-salesforce
docker compose up --build
```

Visit:
- Dashboard: http://localhost:8090/dashboard/
- Homesteader: http://localhost:8090/homestead/
- Corporate: http://localhost:8090/corporate/
- API: http://localhost:8090/api/

## Services

| Service | Port | Purpose |
|---|---|---|
| MeshQL App | 5090 | Clean API (13 entities, REST + GraphQL) |
| nginx | 8090 | Reverse proxy (3 frontends + API) |
| PostgreSQL | 5435 | Salesforce-style legacy database |
| MongoDB | internal | Clean domain storage |
| Kafka | internal | CDC event streaming (KRaft) |
| Debezium | internal | PostgreSQL WAL replication |

## Example Query

The clean API exposes the same domain model regardless of whether data originates from Salesforce, SAP, or native MeshQL. Frontends never see `Farm__c` or 18-character Salesforce IDs:

```graphql
{
  getAll {
    name
    farm_type
    zone
    coops {
      name
      capacity
      coop_type
      hens {
        name
        breed
        status
        productivity { eggs_total eggs_week }
      }
    }
    farmOutput { eggs_week avg_per_hen_per_week }
  }
}
```

## See Also

- [**Egg Economy**](../egg-economy/) — the clean domain, native MeshQL (no legacy system)
- [**Egg Economy SAP**](../egg-economy-sap/) — same domain, SAP as legacy source
- [**Springfield Electric (Legacy)**](../legacy/) — the foundational anti-corruption layer pattern
- [**Mesher**](../../mesher/) — CLI tool that generates anti-corruption layers automatically from any PostgreSQL database
