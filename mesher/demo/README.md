# Mesher Demo: Global Power Plant Database

This demo shows Mesher converting a real legacy database into a clean MeshQL anti-corruption layer service. The dataset is the [WRI Global Power Plant Database](https://datasets.wri.org/dataset/globalpowerplantdatabase) — 34,936 power plants across 167 countries.

## The Legacy Database

The CSV gets loaded into a PostgreSQL database that looks like every legacy enterprise system you've ever seen:

| Legacy Table | What It Stores | Notable Ugliness |
|:-------------|:---------------|:-----------------|
| `CNTRY_REF` | Country reference | `CNTRY_CD`, `CNTRY_NM`, `ACTV_FLG` (Y/N) |
| `FUEL_TYPE_REF` | Fuel type codes | `FUEL_CD` (SOLR, HYDR, COAL), `FUEL_CAT_CD` (RE/FF/NR/OT) |
| `PWR_PLT` | Power plants | `CAP_KW` (capacity in kW as integer), `LAT_DEG`/`LON_DEG` (micro-degrees as integer), `COMM_DT` (YYYYMMDD varchar), `STAT_CD` (OP/CL/PL/UC) |
| `PLT_FUEL_ASGN` | Plant-to-fuel mapping | `FUEL_RNK` (1=primary, 2-4=other) |
| `GEN_DATA` | Annual generation | `ACT_GEN_MWH`/`EST_GEN_MWH` (integer MWh), `EST_MTHD_CD` |

5 tables, 8 foreign keys, check constraints, abbreviated everything.

## What Mesher Produces

Mesher introspects this database and generates a clean API service:

| Legacy | Clean |
|:-------|:------|
| `PWR_PLT.PLT_NM` | `power_plant.name` (title case) |
| `PWR_PLT.CAP_KW` (integer kW) | `power_plant.capacity_kw` |
| `PWR_PLT.LAT_DEG` (micro-degrees) | `power_plant.latitude` |
| `PWR_PLT.COMM_DT` (VARCHAR "19980101") | `power_plant.commissioning_date` (ISO "1998-01-01") |
| `PWR_PLT.STAT_CD` ("OP") | `power_plant.status` ("Operating") |
| `PWR_PLT.OWNR_NM` | `power_plant.owner_name` (title case) |
| `FUEL_TYPE_REF.FUEL_CAT_CD` ("RE") | `fuel_type.category` ("Renewable") |
| `CNTRY_REF.ACTV_FLG` ("Y") | `country.is_active` (true) |

Plus GraphQL federation, REST endpoints, CDC pipeline, Docker infrastructure — the whole stack.

## Running the Demo

### 1. Start the Legacy Database

```bash
cd mesher/demo
docker compose up -d
```

Wait for `load-data` to finish (~30 seconds for 34,936 plants):

```bash
docker compose logs -f load-data
# ... Loading power plants...
# ... 34936 plants loaded
# ... Done!
```

### 2. Explore the Legacy Schema

Connect to see what mesher will introspect:

```bash
docker compose exec legacy-db psql -U postgres -d power_plants
```

```sql
-- Check tables
\dt

-- See the ugliness
SELECT PLT_NM, CAP_KW, STAT_CD, COMM_DT FROM PWR_PLT LIMIT 5;

-- Check fuel assignments
SELECT p.PLT_NM, f.FUEL_DESC, a.FUEL_RNK
FROM PWR_PLT p
JOIN PLT_FUEL_ASGN a ON p.PLT_ID = a.PLT_ID
JOIN FUEL_TYPE_REF f ON a.FUEL_CD = f.FUEL_CD
WHERE p.CNTRY_CD = 'USA'
ORDER BY p.PLT_NM, a.FUEL_RNK
LIMIT 10;

-- Check generation data
SELECT p.PLT_NM, g.RPT_YR, g.ACT_GEN_MWH, g.EST_GEN_MWH
FROM PWR_PLT p
JOIN GEN_DATA g ON p.PLT_ID = g.PLT_ID
WHERE p.CNTRY_CD = 'GBR' AND g.RPT_YR = 2019
LIMIT 10;
```

### 3. Run Mesher

```bash
# Build mesher (from meshql root)
mvn package -pl mesher -DskipTests

# Option A: All-in-one
export ANTHROPIC_API_KEY=sk-ant-...
java -jar mesher/target/mesher-0.2.0.jar run \
    --jdbc-url jdbc:postgresql://localhost:5434/power_plants \
    --username postgres --password postgres \
    --project-name global-power-plants \
    --port 4077 \
    --output ./generated-power-plants

# Option B: Step by step (inspect the intermediate files)
java -jar mesher/target/mesher-0.2.0.jar introspect \
    --jdbc-url jdbc:postgresql://localhost:5434/power_plants \
    --username postgres --password postgres \
    --output introspection.json

# Look at what was introspected
cat introspection.json | python3 -m json.tool | head -50

java -jar mesher/target/mesher-0.2.0.jar convert introspection.json \
    --project-name global-power-plants \
    --port 4077 \
    --output domain-model.json

# Review and edit the domain model
cat domain-model.json | python3 -m json.tool | head -80

java -jar mesher/target/mesher-0.2.0.jar generate domain-model.json \
    --output ./generated-power-plants
```

### 4. Examine the Generated Project

```bash
find generated-power-plants -type f | sort
```

You'll see a complete MeshQL project:
- GraphQL schemas with clean field names
- JSON schemas for REST validation
- Java transformers that convert CAP_KW→capacity_mw, STAT_CD→status, COMM_DT→dates
- CDC processor with phased FK resolution
- Docker Compose for the full stack
- Ready to `docker compose up`

### 5. Clean Up

```bash
docker compose down -v
```

## Dataset Attribution

> Global Power Plant Database, Version 1.3.0. World Resources Institute.
> Available at: https://datasets.wri.org/dataset/globalpowerplantdatabase
>
> Licensed under Creative Commons Attribution 4.0 International (CC BY 4.0).
