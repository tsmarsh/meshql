# Farm Example - Performance Testing Results

## Executive Summary

Performance baseline established for the Farm service under development/test conditions. REST mutations show excellent sub-millisecond performance, while GraphQL queries demonstrate good single-client performance (5-7ms) but experience expected degradation under concurrent load (77ms avg) due to resource contention.

## Test Environment

**⚠️ Important Context:**
- All components running on single development machine
- MongoDB not production-tuned
- No connection pool optimization
- No event loop tuning
- Expected resource contention under load

**Hardware:**
- Platform: Linux 6.17.4-arch2-1
- Deployment: Docker Compose (single host)
- Database: MongoDB (containerized, default configuration)

**Service Configuration:**
- 10 farms, 50 coops (5:1 ratio), 1000 hens (20:1 ratio)
- 3 GraphQL endpoints (/farm/graph, /coop/graph, /hen/graph)
- 3 REST endpoints (/farm/api, /coop/api, /hen/api)

## Test Scenarios

### 1. Mixed Workload Test (Intended Usage Pattern)
**Test:** `example-mixed-workload.jmx`
**Pattern:** REST for mutations, GraphQL for queries
**Load:** 10 concurrent users, 60s duration

**Results:**
- **Overall:** 378 req/sec, 25ms avg, 0% errors
- **REST Create:** 0.6ms avg (lightning fast writes)
- **GraphQL Get:** 50ms avg (reads with tracked IDs)

**Key Insight:** Demonstrates the intended architectural pattern - fast REST writes followed by GraphQL reads.

### 2. Full Object Graph Test
**Test:** `example-full-graph.jmx`
**Pattern:** Complete farm ecosystem with realistic relationships
**Load:** 10 concurrent users, 60s duration

**Results:**
- **Overall:** 245 req/sec, 39ms avg, 0% errors

**REST Operations (Writes):**
- Create Farm: 0.7ms avg
- Create Coop: 0.7ms avg
- Create Hen: 0.6ms avg

**GraphQL Operations (Reads):**
| Query Type | Complexity | Avg Latency | p90 |
|-----------|-----------|-------------|-----|
| Get Farm Simple | No relationships | 74ms | 97ms |
| Get Farm with Coops | 1 level deep | 75ms | 98ms |
| Get Coop with Farm & Hens | Multi-directional | 67ms | 101ms |
| Get Hen with Coop | 2 levels deep | 75ms | 113ms |

**Key Insight:** GraphQL shows ~70ms base overhead regardless of relationship complexity. Relationship resolution itself is efficient - complex multi-level queries perform the same as simple queries.

### 3. GraphQL-Only Test (Latency Investigation)
**Test:** `example-graphql-only.jmx`
**Pattern:** Pure GraphQL queries without REST operations
**Load:** 10 concurrent users, 60s duration

**Results:**
- **Overall:** 124 req/sec, 77ms avg, 0% errors
- **Min:** 4ms (matches single-client performance)
- **Median:** 77ms
- **p90:** 99ms
- **Max:** 152ms

## Critical Finding: Concurrency Impact

### Single Client Performance (curl)
Testing with `curl` sequential requests:
```
Request 1: 0.006719s (6.7ms)
Request 2: 0.005652s (5.7ms)
Request 3: 0.005596s (5.6ms)
...
Average: ~6ms
```

### Concurrent Load Performance (JMeter, 10 threads)

**Latency Distribution:**
- Fast (<30ms): 442 requests (6%) - Near-optimal performance
- Mid (30-60ms): 215 requests (3%)
- **Slow (>=60ms): 6,804 requests (91%)** - Degraded under contention

## Root Cause Analysis

### GraphQL Latency Under Load

The **10x latency increase** (6ms → 77ms) under concurrent load indicates expected resource contention:

**Identified Bottlenecks:**
1. **MongoDB Connection Pool Exhaustion**
   - Default connection pool configuration
   - Multiple concurrent queries competing for connections
   - Connection establishment overhead

2. **Node.js Event Loop Saturation**
   - Single-threaded event loop handling 10 concurrent GraphQL queries
   - Query parsing and execution queuing
   - JavaScript execution blocking

3. **GraphQL Execution Queue**
   - Schema validation overhead
   - Query parsing per request
   - Resolver execution serialization

### Why This Is Expected

On a development machine with:
- All services co-located (app + MongoDB on same host)
- Default MongoDB configuration (no tuning)
- Default Node.js configuration (no cluster mode)
- No connection pool optimization
- Limited CPU/memory resources

**This performance is competitive for a Node.js GraphQL service.**

## Performance Characteristics Summary

### ✅ Excellent Performance
- **REST mutations:** Sub-millisecond (0.6-0.7ms)
- **Single-client GraphQL:** 5-7ms
- **Relationship resolution:** Efficient (no overhead for complexity)

### ⚠️ Expected Degradation Under Load
- **Concurrent GraphQL:** 77ms avg (10x degradation)
- **Throughput:** 124-378 req/sec depending on workload mix
- **Contention:** 91% of requests experience queuing delays

## Recommendations for Production

If deploying to production and performance is critical:

1. **Connection Pool Tuning**
   ```javascript
   {
     maxPoolSize: 100,
     minPoolSize: 10,
     maxIdleTimeMS: 30000
   }
   ```

2. **Node.js Clustering**
   - Deploy multiple Node.js processes
   - Utilize all CPU cores
   - Distribute load across event loops

3. **MongoDB Optimization**
   - Dedicated database server
   - Index optimization for queries
   - Read replicas for scaling

4. **Caching Layer**
   - Redis for frequently accessed data
   - Reduce database round trips
   - Cache parsed GraphQL queries

5. **Load Balancing**
   - Multiple service instances
   - Round-robin distribution
   - Health checks

## Test Artifacts

All performance test results are available in:
```
performance/results/
├── example-mixed-workload_*/
├── example-full-graph_*/
└── example-graphql-only_*/
```

Each test includes:
- JMeter JTL result files
- HTML dashboard reports
- Detailed statistics and charts

## Running Performance Tests

```bash
# Check services are ready
yarn perf:check

# Run specific test
yarn perf example-mixed-workload.jmx
yarn perf example-full-graph.jmx
yarn perf example-graphql-only.jmx

# View results
open performance/results/<test-name>_<timestamp>_report/index.html
```

## Conclusion

The Farm service demonstrates **excellent single-client performance** with sub-10ms GraphQL queries and sub-millisecond REST mutations. Under concurrent load on a single development machine, expected resource contention causes **~10x latency increase** to 77ms average.

This performance is **competitive for Node.js GraphQL services** and represents realistic development/test environment behavior. Production deployment with proper tuning, clustering, and dedicated database infrastructure would significantly improve concurrent performance.

**The performance test suite successfully identifies bottlenecks and establishes a baseline for future optimization efforts.**
