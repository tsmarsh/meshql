---
title: Operational Blueprints
layout: default
parent: Reference
nav_order: 5
---

# Operational Blueprints

MeshQL provides primitives, not policies. This page documents concrete patterns for resilience, schema evolution, and observability built on those primitives.

Choose the pattern that fits your scale and requirements. These aren't prescriptive — they're starting points.

---

## Resilience

### MVP: Let It Crash

At MVP scale, simplicity beats sophistication. If a resolver fails, return null. If a write fails, return the error. Let the client retry.

```mermaid
graph LR
    client["Client"] -->|"request"| meshql["MeshQL"]
    meshql -->|"200 / 404 / 500"| client
    client -->|"retry on failure"| meshql

    style client fill:#f87171,stroke:#333,color:#fff
    style meshql fill:#4a9eff,stroke:#333,color:#fff
```

**What MeshQL gives you**: HTTP status codes, idempotent reads, temporal storage (data survives crashes).

**Good enough for**: Single-JVM deployments, small teams, low traffic.

### Growth: Client-Side Resilience

As you distribute meshobjs across services, add resilience at the client level:

```mermaid
graph LR
    subgraph "Your Service"
        app["Application"] --> resilience["Resilience Layer<br/>(Resilience4j / Hystrix)"]
    end

    resilience -->|"with timeout,<br/>retry, circuit breaker"| meshql["MeshQL<br/>Meshobj"]

    style app fill:#4a9eff,stroke:#333,color:#fff
    style resilience fill:#fbbf24,stroke:#333,color:#000
    style meshql fill:#34d399,stroke:#333,color:#fff
```

**Pattern**: Wrap resolver HTTP calls with [Resilience4j](https://resilience4j.readme.io/):
- **Timeouts**: 2-5s for external resolvers (prevents thread starvation)
- **Retries**: 1-2 retries with exponential backoff for transient failures
- **Circuit breaker**: Open after 5 consecutive failures, half-open after 30s

**Good enough for**: Multi-service deployments, moderate traffic.

### Scale: Service Mesh

At scale, push resilience to the infrastructure layer:

```mermaid
graph TB
    subgraph "Service Mesh (Istio / Linkerd)"
        subgraph "Pod A"
            svc_a["Meshobj A"]
            proxy_a["Sidecar Proxy"]
            svc_a --> proxy_a
        end

        subgraph "Pod B"
            proxy_b["Sidecar Proxy"]
            svc_b["Meshobj B"]
            proxy_b --> svc_b
        end

        proxy_a -->|"mTLS + retries<br/>+ circuit breaking"| proxy_b
    end

    style svc_a fill:#4a9eff,stroke:#333,color:#fff
    style svc_b fill:#34d399,stroke:#333,color:#fff
    style proxy_a fill:#818cf8,stroke:#333,color:#fff
    style proxy_b fill:#818cf8,stroke:#333,color:#fff
```

**Pattern**: Configure Istio/Linkerd to handle:
- **Retries**: Automatic retry on 503/connection errors
- **Circuit breaking**: Per-destination connection limits and outlier detection
- **Timeouts**: Enforced at the mesh level
- **mTLS**: Service-to-service authentication without application changes
- **Load balancing**: Distribute requests across meshobj replicas

MeshQL's external resolvers use standard HTTP — any service mesh works without code changes.

**Good enough for**: Large-scale distributed deployments, high availability requirements.

### Recovery from Failures

MeshQL's persistent storage and temporal versioning provide natural recovery primitives:

| Failure | Recovery |
|:--------|:---------|
| Resolver returns null | Client retries or gracefully degrades |
| Write fails mid-operation | Idempotent retry; temporal versioning prevents duplicates |
| Service crashes | Restart and resume; all data is persisted |
| CDC processor falls behind | Kafka retains events; processor catches up on restart |
| Accidental deletion | Soft deletes; query at earlier timestamp to recover data |

---

## Schema Evolution

### The Ground Rules

MeshQL's loose contract model (consumer-defined projections) already insulates consumers from most changes. But when you need to evolve a query interface, follow these guidelines inspired by [Google's Protocol Buffer compatibility rules](https://protobuf.dev/programming-guides/proto3/#updating):

**Safe changes** (no coordination needed):
- Adding new fields to a type
- Adding new queries to a graphlette
- Adding new optional arguments to existing queries
- Adding a new meshobj

**Requires consumer coordination**:
- Removing a field that consumers reference in their projections
- Changing a field's type
- Renaming a query
- Changing a query's required arguments

**Never do**:
- Change the meaning of an existing field
- Change a field's type silently (e.g., `Int` to `String`)
- Remove a query without deprecation

### Pattern: Additive Evolution

The safest approach — add new fields and queries, deprecate old ones:

```mermaid
graph LR
    subgraph "Phase 1: Add"
        v1a["getById(id) → Hen"]
        v1b["getHenById(id) → Hen<br/>(new, with extra fields)"]
    end

    subgraph "Phase 2: Migrate"
        v2a["getById(id) → Hen<br/><i>deprecated</i>"]
        v2b["getHenById(id) → Hen"]
    end

    subgraph "Phase 3: Remove"
        v3["getHenById(id) → Hen"]
    end

    style v1a fill:#34d399,stroke:#333,color:#fff
    style v1b fill:#4a9eff,stroke:#333,color:#fff
    style v2a fill:#fbbf24,stroke:#333,color:#000
    style v2b fill:#4a9eff,stroke:#333,color:#fff
    style v3 fill:#4a9eff,stroke:#333,color:#fff
```

1. **Add** the new query alongside the old one
2. **Notify** consumers to migrate their resolver configurations
3. **Remove** the old query after all consumers have migrated

### Pattern: Versioned Meshobjs

For breaking changes that can't be done additively, run two versions side by side:

```mermaid
graph TB
    subgraph "Versioned Deployment"
        direction TB
        v1["/hen/v1/graph<br/>Original schema"]
        v2["/hen/v2/graph<br/>New schema"]
        db[("Shared storage")]
        v1 --> db
        v2 --> db
    end

    old_consumer["Legacy Consumers"] --> v1
    new_consumer["New Consumers"] --> v2

    style v1 fill:#fbbf24,stroke:#333,color:#000
    style v2 fill:#4a9eff,stroke:#333,color:#fff
    style db fill:#34d399,stroke:#333,color:#fff
```

Both versions read from the same storage (the Envelope format is stable). The graphlette schemas differ, but the underlying data is the same. Consumers migrate at their own pace.

### Pattern: Expand-Contract for Field Changes

When a field needs to change type or be renamed:

1. **Expand**: Add the new field alongside the old one
2. **Migrate**: Backfill data so both fields are populated (via REST bulk update or migration script)
3. **Update consumers**: Point resolver configs and projections to the new field
4. **Contract**: Remove the old field from the schema and query templates

```
Step 1: { name: "Henrietta", display_name: "Henrietta" }  ← both fields
Step 2: Consumers switch to display_name
Step 3: { display_name: "Henrietta" }                      ← old field removed
```

---

## Observability

### The Primitives

MeshQL already flows identifiers through the system that you can observe:

| Primitive | Where It Flows | What It Tells You |
|:----------|:---------------|:-----------------|
| Document `id` | Storage → REST response → GraphQL → federation → CDC | Which entity is being accessed |
| Auth tokens | HTTP header → Auth → Repository/Searcher filter | Who is making the request |
| HTTP requests | Between meshobjs via external resolvers | Federation topology and latency |
| `createdAt` timestamps | Every Envelope version | Write patterns and data velocity |
| Kafka offsets | CDC pipeline | Processing lag and throughput |

### Pattern: Structured Logging

Configure SLF4J with a structured backend (Logback JSON encoder, Log4j2 JSON layout):

```xml
<!-- logback.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <logger name="com.meshql" level="DEBUG"/>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

Pipe to your aggregator of choice (CloudWatch, Datadog, Splunk, ELK).

### Pattern: Sidecar Metrics

Since MeshQL uses standard HTTP between meshobjs, any HTTP-aware sidecar can collect metrics:

```mermaid
graph TB
    subgraph "Pod"
        meshql["MeshQL"]
        sidecar["Metrics Sidecar<br/>(Prometheus exporter)"]
        meshql --> sidecar
    end

    sidecar --> prometheus["Prometheus"]
    prometheus --> grafana["Grafana"]

    style meshql fill:#4a9eff,stroke:#333,color:#fff
    style sidecar fill:#818cf8,stroke:#333,color:#fff
    style prometheus fill:#fbbf24,stroke:#333,color:#000
    style grafana fill:#34d399,stroke:#333,color:#fff
```

Key metrics to track:
- **Request latency** per graphlette/restlette endpoint
- **Resolver latency** (time spent in federation calls)
- **Error rate** per endpoint
- **DataLoader batch sizes** (are you hitting the 100-ID max?)
- **Temporal query frequency** (how often do consumers use `at` parameters?)

### Pattern: Distributed Tracing

MeshQL's external resolvers forward the `Authorization` header. To add trace propagation, add a servlet filter that injects trace context headers (`traceparent`, `X-Request-ID`) and forward them through resolver HTTP calls:

```mermaid
sequenceDiagram
    participant Client
    participant Farm as Farm Meshobj
    participant Coop as Coop Meshobj
    participant Jaeger

    Client->>Farm: GET /farm/graph<br/>traceparent: 00-abc-001-01
    Farm->>Farm: Start span: farm.getById
    Farm->>Coop: POST /coop/graph<br/>traceparent: 00-abc-002-01
    Coop->>Coop: Start span: coop.getByFarm
    Coop-->>Farm: Response
    Farm->>Farm: End span
    Farm-->>Client: Response

    Farm->>Jaeger: Report spans
    Coop->>Jaeger: Report spans
    Note over Jaeger: Full trace: client → farm → coop
```

Compatible with OpenTelemetry, Jaeger, Zipkin, or any W3C Trace Context implementation.

### Pattern: Health and Readiness

MeshQL exposes built-in health endpoints:

```
GET /health → {"status": "ok"}
GET /ready  → {"status": "ok"}
```

Use these for Kubernetes liveness and readiness probes:

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 3033
readinessProbe:
  httpGet:
    path: /ready
    port: 3033
```
