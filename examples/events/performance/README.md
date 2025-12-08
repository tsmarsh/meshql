# Events CDC Performance Testing

Performance testing suite for the MeshQL Events CDC pipeline using k6.

> **Latest Results**: See [RESULTS.md](RESULTS.md) for performance comparison with MeshObj (Node.js).
> TL;DR: MeshQL (Java 21) is **~3x faster** for HTTP API latency due to JIT optimization.

## Overview

This directory contains k6-based performance tests that measure end-to-end latency of the CDC (Change Data Capture) pipeline:

```
HTTP POST → MongoDB → Debezium → Kafka (raw) → Processor → Kafka (processed)
```

## Prerequisites

### k6 with Kafka Extension

**Important**: The CDC latency tests require a custom k6 binary with the [xk6-kafka](https://github.com/mostafa/xk6-kafka) extension. This extension is **not included** in the standard k6 package available via package managers (pacman, apt, brew, etc.).

#### Why Custom Build?

The standard system k6 (`/usr/bin/k6`) can only make HTTP requests. Our CDC tests need to:
- Consume messages from Kafka topics
- Measure end-to-end latency by reading from both raw and processed topics
- Validate message delivery through the entire pipeline

#### Building k6 with Kafka Support

```bash
# 1. Install xk6 builder (requires Go)
go install go.k6.io/xk6/cmd/xk6@latest

# 2. Build k6 with Kafka extension (in this directory)
cd examples/events/performance
xk6 build --with github.com/mostafa/xk6-kafka@latest

# This creates ./k6 binary (~51MB)
```

**Note**: The k6 binary is gitignored because:
1. It's 51MB (too large for git)
2. Binary builds are platform-specific (Linux/Mac/Windows)
3. Users need to build it locally anyway

### Infrastructure

Start the CDC stack before running tests:

```bash
cd examples/events
docker-compose up -d

# Wait ~30 seconds for services to initialize
```

## Test Files

| File | Purpose | Status |
|------|---------|--------|
| [cdc-latency-http.k6.js](cdc-latency-http.k6.js) | **Recommended** - HTTP-based CDC test (no extensions needed) | ✅ Active |
| [cdc-latency-batch.k6.js](cdc-latency-batch.k6.js) | Kafka-based CDC test (requires xk6-kafka) | ✅ Active |
| [cdc-latency-kafka.k6.js](cdc-latency-kafka.k6.js) | Legacy correlationId-based approach | ⚠️ Reference only |
| [cleanup-and-restart.sh](cleanup-and-restart.sh) | Clean MongoDB/Kafka volumes and restart | ✅ Active |
| [RESULTS.md](RESULTS.md) | Performance results and comparison | 📊 Results |

## Running Tests

### Quick Start (HTTP-based test - Recommended)

The HTTP-based test requires only standard k6 (no extensions):

```bash
# Start the CDC stack
cd examples/events
docker-compose up -d

# Wait for services (~30 seconds), then run test
k6 run performance/cdc-latency-http.k6.js
```

This test measures end-to-end CDC latency by polling the REST API for processed events.

### 1. Clean Environment (Optional)

Clean volumes before testing for consistent results:

```bash
cd examples/events
./performance/cleanup-and-restart.sh
```

This script:
- Stops docker-compose
- Removes MongoDB and Kafka volumes
- Restarts all services
- Waits for services to be ready

### 2. Run CDC Latency Test

```bash
cd performance
./k6 run cdc-latency-batch.k6.js
```

**Important**: Use `./k6` (local binary with Kafka), not `k6` (system binary without Kafka).

### 3. View Results

Results are printed to stdout. Key metrics:

```
📊 CDC Pipeline Metrics:
   Batch Submit Time:  489ms
   Debezium Lag:       3,095ms (HTTP → Raw Topic)
   Processor Lag:      3,075ms (Raw → Processed Topic)
   End-to-End:         6,660ms (HTTP → Processed Topic)
```

## What the Test Does

The batch CDC latency test ([cdc-latency-batch.k6.js](cdc-latency-batch.k6.js)):

1. **Submits 100 events** via HTTP POST to `/event/api`
2. **Polls raw Kafka topic** until 100 messages appear (or 2-minute timeout)
3. **Polls processed Kafka topic** until 100 messages appear (or 2-minute timeout)
4. **Calculates metrics**:
   - API response time
   - Debezium lag (HTTP → raw topic)
   - Processor lag (raw → processed topic)
   - End-to-end latency (HTTP → processed topic)

**Why batch + count-based?**
- More reliable than searching for individual correlationIds
- Avoids Debezium envelope parsing complexity
- Measures realistic batch ingestion scenarios
- Simpler and faster than per-message tracking

## Test Configuration

Default settings in [cdc-latency-batch.k6.js](cdc-latency-batch.k6.js):

```javascript
const BATCH_SIZE = 100;                  // Number of events to submit
const RAW_TOPIC_TIMEOUT_MS = 120000;     // 2 minutes
const PROCESSED_TOPIC_TIMEOUT_MS = 120000; // 2 minutes
const KAFKA_BROKERS = ['localhost:9092'];
```

You can modify these values in the script or create variants for different scenarios (e.g., larger batches, different timeouts).

## Troubleshooting

### "GoError: Unable to read messages"

**Cause**: Using system k6 without Kafka extension

**Fix**: Use `./k6` (local binary), not `k6` (system binary)

```bash
# Wrong
k6 run cdc-latency-batch.k6.js

# Correct
./k6 run cdc-latency-batch.k6.js
```

### "./k6: No such file or directory"

**Cause**: Haven't built the custom k6 binary yet

**Fix**: Build k6 with Kafka extension (see Prerequisites above)

### "Connection refused" on port 9092

**Cause**: Kafka not running or not ready

**Fix**:
```bash
# Check if Kafka is running
docker-compose ps kafka

# Restart if needed
docker-compose restart kafka

# Or run cleanup script
./performance/cleanup-and-restart.sh
```

### Test times out waiting for messages

**Cause**: CDC pipeline may be slow on first run, or services not fully initialized

**Fix**:
1. Ensure all services are healthy: `docker-compose ps`
2. Check Debezium logs: `docker-compose logs debezium | tail -50`
3. Verify processor is running: `docker-compose logs events | tail -50`
4. Try running cleanup script and waiting 60 seconds before testing

### High latency (>10 seconds)

**Cause**: Normal for first run or after restart

**Fix**:
- First test run is always slower (cold start, consumer group rebalancing)
- Run test 2-3 times to get accurate results

## Understanding the Results

**TL;DR**:
- ✅ API performance is excellent (~2ms avg)
- ⚠️ CDC pipeline adds ~3s Debezium lag + ~3s processor lag = ~6s total
- 🎯 Good for eventual consistency, needs tuning for near-realtime

## For Developers

### Creating New Tests

1. Copy an existing test as a template
2. Modify the test logic
3. Use `./k6 run --vus 1 --iterations 1` for quick validation
4. Document expected results in comments

### Test Design Principles

- **Use count-based polling** for batch tests (not correlationId search)
- **Add timeouts** to prevent infinite loops
- **Log progress** with console.log() for debugging
- **Fail fast** if prerequisites aren't met (return early)
- **Measure each stage** separately for bottleneck analysis

### Why Not JMeter?

Previous versions used JMeter, but we switched to k6 because:
- ✅ Native Kafka consumer support (xk6-kafka extension)
- ✅ JavaScript-based (easier to maintain)
- ✅ Better Docker/container integration
- ✅ More modern CLI/automation workflow
- ❌ JMeter Kafka plugins are complex and poorly documented

## CI/CD Integration

These tests are **not run in CI** because:
1. They require Docker with significant resources (MongoDB, Kafka, Debezium)
2. Tests take ~7-10 seconds per run (too slow for PR checks)
3. CDC latency is environment-dependent (meaningless in CI)

To run locally as part of development:
```bash
# Full test cycle
./performance/cleanup-and-restart.sh
cd performance && ./k6 run cdc-latency-batch.k6.js
```

For functional validation (not performance), use BDD tests:
```bash
mvn test -Dtest=EventsBddTest  # Runs with testcontainers
```

## Further Reading

- [k6 Documentation](https://k6.io/docs/)
- [xk6-kafka Extension](https://github.com/mostafa/xk6-kafka)
- [Debezium MongoDB Connector](https://debezium.io/documentation/reference/stable/connectors/mongodb.html)
