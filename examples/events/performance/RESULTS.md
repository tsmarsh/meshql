# MeshQL Events CDC Performance Results

## Summary

Performance testing of the MeshQL Events CDC pipeline demonstrates excellent HTTP API performance and reliable end-to-end CDC latency. Notably, the Java 21 implementation shows **~3x faster HTTP response times** compared to the equivalent TypeScript/Node.js implementation (MeshObj).

## Test Environment

- **Platform**: MeshQL Events CDC Pipeline (Java 21+)
- **Stack**: MongoDB → Debezium → Kafka → Processor → MongoDB
- **Test Tool**: k6 (HTTP-based test)
- **Batch Size**: 100 events per test run

## Results

### HTTP API Performance

| Metric | MeshQL (Java 21) | MeshObj (Node.js) | Improvement |
|--------|------------------|-------------------|-------------|
| **Average Latency** | 1.5-2.2ms | 6.14ms | **~3x faster** |
| **Median Latency** | 1.5-1.9ms | 5.4ms | **~3x faster** |
| **p90 Latency** | 2.1-3.4ms | 8.67ms | **~3x faster** |
| **p95 Latency** | 2.2-3.8ms | 9.22ms | **~3x faster** |
| **Success Rate** | 100% | 100% | Same |

### End-to-End CDC Latency (100 events batch)

| Metric | Run 1 | Run 2 | Run 3 | Average |
|--------|-------|-------|-------|---------|
| **Batch Submit Time** | 156ms | 230ms | 152ms | **179ms** |
| **Wait for Processing** | 1,072ms | 1,091ms | 1,046ms | **1,070ms** |
| **End-to-End Latency** | 1,229ms | 1,325ms | 1,200ms | **1,251ms** |
| **Avg per Event** | 12ms | 13ms | 12ms | **12ms** |

### All Thresholds Passed

- `cdc_api_response_ms p(95)<100ms`: ✓ (~2-4ms actual)
- `cdc_end_to_end_ms p(95)<30s`: ✓ (~1.2s actual)
- `http_errors count==0`: ✓
- `timeouts count==0`: ✓

## Why Java 21 is Faster

The ~3x improvement in HTTP latency is attributed to several factors in the modern Java stack:

### 1. Virtual Threads (Project Loom)

Java 21's virtual threads handle concurrent requests extremely efficiently without the overhead of traditional thread pools. Each request gets its own lightweight thread with minimal context-switching cost.

### 2. JIT Compilation

The JVM's Just-In-Time compiler aggressively optimizes hot paths after warmup:

- **First few requests**: Interpreted execution (~6ms, similar to Node.js)
- **JIT kicks in**: Identifies hot paths, compiles to native code
- **Steady state**: Optimized execution (~1.5ms)

This test sends 100 sequential requests, giving the JIT ample opportunity to optimize - something that short-lived tests or single-request benchmarks miss entirely.

### 3. Jetty 12

The latest Jetty server is optimized for virtual threads and has minimal per-request overhead.

### 4. No Runtime Interpreter Overhead

Direct bytecode execution vs Node.js event loop and V8 JavaScript interpretation.

## Implications

1. **Keep services long-running**: JIT benefits compound over time. The 1.5ms average is the optimized steady state.

2. **Warmup matters**: Cold-start benchmarks understate Java's performance. Production services that handle sustained traffic will see these optimized latencies.

3. **CDC pipeline is production-ready**: ~1.2 seconds for 100 events to flow through MongoDB → Debezium → Kafka → Processor → MongoDB is suitable for eventual consistency use cases.

## Running the Tests

### Prerequisites

```bash
# Start the CDC stack
cd examples/events
docker-compose up -d

# Wait for services to initialize (~30 seconds)
```

### Run HTTP-based Performance Test

```bash
# Using system k6 (no extensions required)
k6 run performance/cdc-latency-http.k6.js
```

### Run Kafka-based Performance Test (requires xk6-kafka)

```bash
# Build k6 with Kafka extension
cd performance
go install go.k6.io/xk6/cmd/xk6@latest
xk6 build --with github.com/mostafa/xk6-kafka@latest

# Run the test
./k6 run cdc-latency-batch.k6.js
```

**Note**: The Kafka-based test requires the custom k6 binary with xk6-kafka extension. The HTTP-based test works with standard k6 and provides equivalent end-to-end latency measurements.

## Test Files

| File | Purpose |
|------|---------|
| [cdc-latency-http.k6.js](cdc-latency-http.k6.js) | HTTP-based CDC test (recommended) |
| [cdc-latency-batch.k6.js](cdc-latency-batch.k6.js) | Kafka-based CDC test (requires xk6-kafka) |
| [cleanup-and-restart.sh](cleanup-and-restart.sh) | Clean environment script |

## Conclusion

MeshQL's Java 21 implementation delivers significantly better HTTP performance than the Node.js equivalent while maintaining the same CDC pipeline reliability. The JIT compilation benefits are substantial for sustained workloads, making this implementation well-suited for production use cases requiring low-latency event processing.
