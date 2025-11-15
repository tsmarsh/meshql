# Farm Performance Tests

JMeter-based performance testing suite for the Farm example.

## Prerequisites

1. **JMeter** - Download from https://jmeter.apache.org/download_jmeter.cgi
   - Either set `JMETER_HOME` environment variable
   - Or add `jmeter` to your PATH

2. **Running services** - Start the farm example services:
   ```bash
   cd examples/farm
   docker-compose up -d
   ```

## Running Tests

### Check if services are ready
```bash
yarn perf:check
```

### Run all performance tests
```bash
yarn perf
```

### Run a specific test plan
```bash
yarn perf graphql-simple.jmx
```

## Directory Structure

```
performance/
├── test-plans/          # JMeter test plan files (.jmx)
├── scripts/
│   ├── wait-for-services.sh    # Health check script
│   └── run-perf-tests.sh       # Test orchestration
└── results/             # Test results (gitignored)
    ├── *.jtl           # Raw results
    ├── *_report/       # HTML reports
    └── *.log           # JMeter logs
```

## Creating Test Plans

1. Open JMeter GUI:
   ```bash
   jmeter
   ```

2. Create your test plan with:
   - Thread Groups (users, ramp-up, iterations)
   - HTTP/GraphQL samplers
   - Listeners for results

3. Save to `test-plans/*.jmx`

## Test Scenarios

### Full Graph Test (example-full-graph.jmx)
**DataLoader stress test with 1M+ records**

**Data Scale:**
- 10 farms
- 100 coops per farm (1,000 total)
- 100 hens per coop (100,000 total)
- 100 lay reports per hen (10,000,000 total)

**Key Features:**
- Uses bulk API (`/api/bulk`) for fast data generation
- Tests 4-level relationship nesting: Farm → Coops → Hens → Lay Reports
- Demonstrates DataLoader batching (100 HTTP calls → 1 batched call)

**Test Phases:**
1. **Setup (5-15 min):** Creates 1M+ records using bulk operations
2. **Load Test (60s):** 10 concurrent users querying deep relationships

**Critical Query:**
```graphql
{
  getById(id: "<coop_id>") {
    hens {
      layReports { id time_of_day eggs }  # 100 hens = 100 requests batched into 1!
    }
  }
}
```

**Measured Results** (10M records, 10 concurrent threads):
- **Without DataLoader**: 102 HTTP calls per query, 865ms avg (10,951ms worst case)
- **With DataLoader**: 3 HTTP calls per query, 655ms avg (73ms worst case)
- **6x throughput improvement** (4.28 → 25.73 req/sec)
- **149x better worst-case latency** under concurrent load

See [DATALOADER_ANALYSIS.md](./DATALOADER_ANALYSIS.md) for detailed performance analysis.

### Other Test Scenarios

The repository also supports smaller-scale tests for quick iterations:
- GraphQL simple queries
- REST CRUD operations
- Mixed workloads

## Viewing Results

After running tests, open the HTML report:
```bash
open performance/results/<test-name>_<timestamp>_report/index.html
```

Key metrics to review:
- **Requests/sec** - Throughput
- **Response Time** - p50, p90, p99
- **Error Rate** - % failures
- **Throughput** - Bytes/sec

## Tips

- Start with low load (10 users) and increase gradually
- Run tests multiple times for consistency
- Monitor docker stats during tests: `docker stats`
- Clear results regularly to save disk space
