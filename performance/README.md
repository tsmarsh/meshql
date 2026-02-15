# MeshQL Performance Tests (k6)

Performance test suite for the egg-economy API using [k6](https://k6.io/).

## Prerequisites

- k6 installed (`k6 version` to verify)
- A running egg-economy server (any variant: merkql, mongo, sap, salesforce)

## Quick Start

```bash
# Start any egg-economy variant, e.g.:
cd examples/egg-economy-merkql
java -jar target/egg-economy-merkql-example-0.2.0.jar

# Smoke test a single suite
k6 run -e BASE_URL=http://localhost:5088 -e PROFILE=smoke performance/tests/rest-crud.js

# Run all tests
./performance/run-all.sh http://localhost:5088 smoke
```

## Test Suites

| File | Focus | Key metrics |
|------|-------|-------------|
| `rest-crud.js` | REST CRUD throughput | Create/Read/Update/Delete across actor entities |
| `graphql-queries.js` | Query latency | getById p95<200ms, getAll p95<500ms, filtered p95<300ms |
| `federation-depth.js` | Nested resolver chains | Depth 2 p95<500ms, Depth 3 p95<1s, Depth 4 p95<2s |
| `mixed-workload.js` | Write events + read projections | Combined REST writes + GraphQL reads |

## Profiles

| Profile | VUs | Duration | Use case |
|---------|-----|----------|----------|
| `smoke` | 1 | 10s | Quick validation |
| `load` | 5→10→0 | 55s | Standard load test |
| `stress` | 10→30→50→0 | 90s | Find breaking points |

## Configuration

Set via environment variables:

- `BASE_URL` — Server URL (default: `http://localhost:5088`)
- `PROFILE` — Test profile: smoke, load, stress (default: `load`)

## Thresholds

- HTTP request duration: p95 < 500ms
- HTTP request failure rate: < 1%
- Per-test custom thresholds (see individual test files)
