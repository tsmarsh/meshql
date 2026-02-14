# Egg Economy SAP: Anti-Corruption Layer for SAP Migration

Demonstrates using MeshQL as a **transitional architecture** for migrating away from an over-customized SAP system — without a big-bang cutover and without modifying the legacy system.

## The Enterprise Problem

SAP implementations accumulate decades of customization. Z-tables, custom transaction codes, abbreviated German column names, MANDT-scoped tenancy, YYYYMMDD date strings, single-character code maps — the system works, but it has become the bottleneck:

- **Vendor lock-in**: Every new feature requires SAP consultants at SAP rates
- **Integration tax**: Every downstream system must understand SAP's internal data model
- **Upgrade paralysis**: Heavy customization makes SAP upgrades multi-year projects
- **Talent scarcity**: Finding developers who understand both SAP ABAP and modern architectures is increasingly difficult

The anti-corruption layer pattern lets you **start migrating today** without touching SAP. New applications consume a clean API while legacy data flows through automatically.

## Architecture

```
SAP PostgreSQL (Z-prefix tables, abbreviated columns, MANDT tenancy)
  → Debezium CDC (zero-impact replication via WAL)
    → Kafka (10 topics, one per legacy table)
      → LegacyToCleanProcessor (5-phase consumer)
        → 10 transformers (SAP naming → clean domain)
        → MeshQL REST API → MongoDB (clean domain model)
        → Inline projection updates
          → 3 frontend apps (built against clean API, zero SAP knowledge)
```

**The legacy system is never modified.** SAP continues to operate exactly as before. New applications are built exclusively against the clean MeshQL API. When SAP is eventually decommissioned, you swap the data source — the clean API, the frontends, and all downstream integrations remain unchanged.

## What Gets Transformed

| SAP (Legacy) | Clean (MeshQL) | Transformation |
|:-------------|:---------------|:---------------|
| `ZFARM_MSTR.FARM_TYP_CD: "M"` | `farm_type: "megafarm"` | Single-char code map |
| `ZFARM_MSTR.ERDAT: "20150301"` | `createdAt: "2015-03-01T08:00:00"` | YYYYMMDD to ISO-8601 |
| `ZFARM_MSTR.MANDT: "100"` | *(filtered out)* | Client 100 only |
| `ZSTALL_MSTR.WERKS` | `farm_id: <meshql-uuid>` | FK resolution via ID cache |
| `ZEQUI_HEN.RASSE_CD: "RIR"` | `breed: "Rhode Island Red"` | Breed code map |
| `ZEQUI_HEN.STAT_CD: "A"` | `status: "active"` | Status code map |
| `ZAFRU_LEGE.QUAL_CD: "A"` | `quality: "Grade A"` | Quality code map |
| `ZMSEG_101.QUELL_TYP_CD: "F"` | `source_type: "farm"` | Source type code map |
| `ZMSEG_261.ZWECK_CD: "C"` | `purpose: "cooking"` | Purpose code map |
| `ZMSEG_301.QUELL_BEH_NR` | `source_container_id: <uuid>` | Dual FK resolution |

Every transformer handles the specific encoding patterns that accumulate in long-lived SAP systems: abbreviated column names (`BEHAELT_NR` for container number), packed dates (`ERFAS_DT` + `ERFAS_ZT`), single-character codes that only exist in tribal knowledge or ancient ABAP data elements.

## SAP Table Mapping

| Clean Entity | SAP Table | Key SAP Columns |
|---|---|---|
| Farm | ZFARM_MSTR | MANDT, WERKS, FARM_NM, FARM_TYP_CD, ZONE_CD, EIGR, ERDAT |
| Coop | ZSTALL_MSTR | STALL_NR, WERKS(FK), STALL_NM, KAPZT, STALL_TYP_CD |
| Hen | ZEQUI_HEN | EQUNR, STALL_NR(FK), HENNE_NM, RASSE_CD, GEB_DT, STAT_CD |
| Container | ZBEHAELT_MSTR | BEHAELT_NR, BEHAELT_NM, BEHAELT_TYP_CD, KAPZT, ZONE_CD |
| Consumer | ZKUNDE_VBR | KUNNR, KUND_NM, VBR_TYP_CD, ZONE_CD, WOCH_BEDARF |
| LayReport | ZAFRU_LEGE | AUFNR, EQUNR(FK), WERKS(FK), EI_MENGE, ERFAS_DT+ZT, QUAL_CD |
| StorageDeposit | ZMSEG_101 | MBLNR, BEHAELT_NR(FK), QUELL_TYP_CD, EI_MENGE |
| StorageWithdrawal | ZMSEG_201 | MBLNR, BEHAELT_NR(FK), EI_MENGE, ERFAS_DT+ZT |
| ContainerTransfer | ZMSEG_301 | MBLNR, QUELL_BEH_NR(FK), ZIEL_BEH_NR(FK), EI_MENGE |
| ConsumptionReport | ZMSEG_261 | MBLNR, KUNNR(FK), BEHAELT_NR(FK), EI_MENGE, ZWECK_CD |

## Processing Phases

FK dependencies between entities require ordered processing. The processor drains each phase's Kafka topics completely before advancing:

1. **Phase 1**: Farm, Container, Consumer — root entities with no FK dependencies
2. **Phase 2**: Coop — depends on Farm (resolves `WERKS` → `farm_id`)
3. **Phase 3**: Hen — depends on Coop (resolves `STALL_NR` → `coop_id`)
4. **Phase 4**: All 5 event topics — depend on all actors, plus inline projection updates
5. **Phase 5**: Continuous consumption of all 10 topics for ongoing CDC

## Migration Strategy

This example demonstrates a three-stage vendor replacement path:

### Stage 1: Shadow (this example)
SAP remains the system of record. Debezium replicates changes in real-time. New applications are built against the clean API. Legacy applications continue unchanged. **Zero risk to production.**

### Stage 2: Dual-Write
New applications write to MeshQL directly. A reverse sync pushes changes back to SAP for legacy consumers. Both systems are authoritative during the transition window.

### Stage 3: Cutover
SAP is decommissioned. The Debezium pipeline is removed. MeshQL becomes the sole system of record. All applications already work — they've been consuming the clean API since Stage 1.

The key insight: **Stage 1 costs almost nothing and delivers immediate value.** Every new application built against the clean API is one fewer application that needs migration at cutover.

## Running

```bash
cd examples/egg-economy-sap
docker compose up --build
```

Visit:
- Dashboard: http://localhost:8089/dashboard/
- Homesteader: http://localhost:8089/homestead/
- Corporate: http://localhost:8089/corporate/
- API: http://localhost:8089/api/

## Services

| Service | Port | Purpose |
|---|---|---|
| MeshQL App | 5089 | Clean API (13 entities, REST + GraphQL) |
| nginx | 8089 | Reverse proxy (3 frontends + API) |
| PostgreSQL | 5434 | SAP-style legacy database |
| MongoDB | internal | Clean domain storage |
| Kafka | internal | CDC event streaming (KRaft) |
| Debezium | internal | PostgreSQL WAL replication |

## Example Query

The clean API exposes the same domain model regardless of whether data originates from SAP, Salesforce, or native MeshQL. Frontends never see `ZFARM_MSTR` or `WERKS`:

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
- [**Egg Economy Salesforce**](../egg-economy-salesforce/) — same domain, Salesforce as legacy source
- [**Springfield Electric (Legacy)**](../legacy/) — the foundational anti-corruption layer pattern
- [**Mesher**](../../mesher/) — CLI tool that generates anti-corruption layers automatically from any PostgreSQL database
