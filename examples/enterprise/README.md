# The Enterprise: Multi-Source Anti-Corruption Layer with Data Lake

The capstone MeshQL example. Combines **two legacy sources** (SAP + Distribution PostgreSQL)
feeding the same clean domain, adds a **data lake** (MinIO + DuckDB) for BI/reporting, and
demonstrates **full bidirectional write-back** with data ownership maintained by the legacy systems.

## Architecture

```
SAP PostgreSQL ──► Debezium CDC ──► Kafka ──┐
(Farm, Coop, Hen, LayReport)                │
                                            ▼
                                   EnterpriseLegacyProcessor (5-phase)
                                     ├─► MeshQL REST ──► MongoDB (13 entities)
Distribution PostgreSQL ──► Debezium │   └─► Projections (inline)
(Container, Consumer, Storage,       │
 Transfers, Consumption)     ────────┘
                                            │
                                            ▼
                              Kafka clean topics ──► LakeWriter ──► MinIO (JSON lines)
                                                                        │
                              Lake API (Python + DuckDB) ◄──────────────┘
                              Dashboard ◄── Lake API (BI/analytics)

MongoDB change streams ──► CleanToSapWriter ──► SAP PostgreSQL (write-back)
                       └─► CleanToDistroWriter ──► Distro PostgreSQL (write-back)
```

## Data Ownership

| System | Entities | Rationale |
|--------|----------|-----------|
| **SAP** (production ERP) | Farm, Coop, Hen, LayReport | Asset/equipment master data and production events |
| **Distribution PG** (logistics) | Container, Consumer, StorageDeposit, StorageWithdrawal, ContainerTransfer, ConsumptionReport | Warehouse operations and customer management |
| **Projections** (computed) | HenProductivity, FarmOutput, ContainerInventory | Computed inline from events |

## Services (13)

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | sap-postgres | 5434 | SAP-style legacy DB (4 tables) |
| 2 | distro-postgres | 5436 | Distribution system DB (6 tables) |
| 3 | mongodb | — | Clean domain storage (replica set) |
| 4 | kafka | — | Event backbone (KRaft) |
| 5 | debezium-sap | — | SAP PostgreSQL CDC |
| 6 | debezium-distro | — | Distribution PostgreSQL CDC |
| 7 | minio | 9000/9001 | Data lake storage (S3-compatible) |
| 8 | enterprise-app | 5091 | MeshQL + processor + lake writer + write-back |
| 9 | lake-api | 5092 | DuckDB query service for BI |
| 10 | dashboard-app | — | BI dashboard (reads from Lake API) |
| 11 | homesteader-app | — | Operational app (from egg-economy) |
| 12 | corporate-app | — | Operational app (from egg-economy) |
| 13 | nginx | 8091 | Reverse proxy |

## Quick Start

```bash
cd examples/enterprise
docker compose up --build
```

## Endpoints

| URL | Description |
|-----|-------------|
| http://localhost:8091/dashboard/ | BI dashboard (data lake) |
| http://localhost:8091/homestead/ | Homesteader operational app |
| http://localhost:8091/corporate/ | Corporate operational app |
| http://localhost:8091/api/ | Clean MeshQL API (13 entities) |
| http://localhost:8091/lake-api/ | Lake API (DuckDB analytics) |
| http://localhost:9001/ | MinIO console (minioadmin/minioadmin) |

## Lake API Endpoints

| Endpoint | Source | Computation |
|----------|--------|-------------|
| `GET /api/v1/farm_output` | lay_report events | Eggs aggregated by farm |
| `GET /api/v1/hen_productivity` | lay_report events | Eggs + quality rate by hen |
| `GET /api/v1/container_inventory` | deposit/withdrawal/transfer/consumption events | Net inventory per container |
| `GET /api/v1/summary` | All entity/event files | Cross-system overview |

## Write-Back Flow

1. User POSTs a new farm via MeshQL REST → MongoDB (immediate)
2. MongoDB change stream fires → `CleanToSapWriter` detects no `legacy_werks` field
3. Writer reverse-transforms and INSERTs into SAP PostgreSQL
4. Debezium detects the new row → forward pipeline → dedup check → PUT (update) existing entity

Same flow for distribution entities via `CleanToDistroWriter`.

## Legacy Database Contrasts

| Aspect | SAP PostgreSQL | Distribution PostgreSQL |
|--------|---------------|------------------------|
| PK style | VARCHAR composite (MANDT + WERKS) | SERIAL integer |
| Date format | YYYYMMDD VARCHAR | TIMESTAMP |
| Column naming | German abbreviations (STALL_NR) | English readable (container_id) |
| Table naming | Singular with prefix (ZFARM_MSTR) | Plural (containers) |
| FK style | VARCHAR (BEHAELT_NR) | INTEGER REFERENCES |
| Multi-tenant | MANDT column | Single-tenant |
