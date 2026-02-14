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

| Clean Entity | SF Custom Object | Key SF Fields |
|---|---|---|
| Farm | Farm__c | Id, Name, Farm_Type__c, Zone__c, Owner_Name__c |
| Coop | Coop__c | Id, Name, Farm__c(lookup), Capacity__c, Coop_Type__c |
| Hen | Hen__c | Id, Name, Coop__c(lookup), Breed__c, Date_of_Birth__c, Status__c |
| Container | Storage_Container__c | Id, Name, Container_Type__c, Capacity__c, Zone__c |
| Consumer | Consumer_Account__c | Id, Name, Consumer_Type__c, Zone__c, Weekly_Demand__c |
| LayReport | Lay_Report__c | Id, Hen__c(FK), Farm__c(FK), Egg_Count__c, Quality__c |
| StorageDeposit | Storage_Deposit__c | Id, Container__c(FK), Source_Type__c, Egg_Count__c |
| StorageWithdrawal | Storage_Withdrawal__c | Id, Container__c(FK), Egg_Count__c, Reason__c |
| ContainerTransfer | Container_Transfer__c | Id, Source_Container__c(FK), Dest_Container__c(FK), Egg_Count__c |
| ConsumptionReport | Consumption_Report__c | Id, Consumer__c(FK), Container__c(FK), Egg_Count__c, Purpose__c |

## Processing Phases

FK dependencies between entities require ordered processing. The processor drains each phase's Kafka topics completely before advancing:

1. **Phase 1**: Farm, Container, Consumer — root entities with no FK dependencies
2. **Phase 2**: Coop — depends on Farm (resolves `Farm__c` lookup → `farm_id`)
3. **Phase 3**: Hen — depends on Coop (resolves `Coop__c` lookup → `coop_id`)
4. **Phase 4**: All 5 event topics — depend on all actors, plus inline projection updates
5. **Phase 5**: Continuous consumption of all 10 topics for ongoing CDC

## Migration Strategy

This example demonstrates a three-stage platform replacement path:

### Stage 1: Shadow (this example)
Salesforce remains the system of record. Debezium replicates changes in real-time via CDC. New applications are built against the clean MeshQL API. Existing Salesforce users, reports, dashboards, and workflows continue unchanged. **Zero risk, immediate value.**

### Stage 2: Parallel Operation
New capabilities are built directly in MeshQL. Salesforce functionality is progressively retired object by object. Each custom object decommissioned is a reduction in Salesforce license scope and platform complexity.

### Stage 3: Cutover
Salesforce is decommissioned. The CDC pipeline is removed. MeshQL is the sole system of record. Every application built against the clean API since Stage 1 continues working without modification. License costs drop to zero.

The critical advantage: **you stop building on Salesforce on day one.** Every feature built against the clean API is a feature that doesn't need to be migrated later.

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
