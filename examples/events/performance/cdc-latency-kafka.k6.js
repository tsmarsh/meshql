/**
 * k6 CDC Latency Test with Kafka Support
 *
 * Measures end-to-end latency of the CDC pipeline using actual Kafka consumers.
 *
 * Prerequisites:
 *   1. Install xk6: go install go.k6.io/xk6/cmd/xk6@latest
 *   2. Build k6 with Kafka: xk6 build --with github.com/mostafa/xk6-kafka@latest
 *   3. This creates ./k6 binary with Kafka support
 *
 * Run:
 *   ./k6 run cdc-latency-kafka.k6.js
 *   ./k6 run --vus 1 --iterations 100 cdc-latency-kafka.k6.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import {
  Writer,
  Reader,
  Connection,
  SchemaRegistry,
  SCHEMA_TYPE_STRING,
} from 'k6/x/kafka';

// Custom metrics for CDC pipeline stages
const apiResponseTime = new Trend('cdc_api_response_ms', true);       // Stage 1: Time to HTTP 30x response
const httpToRaw = new Trend('cdc_http_to_raw_ms', true);             // Stage 2: Time to appear on raw topic (Debezium lag)
const rawToProcessed = new Trend('cdc_raw_to_processed_ms', true);   // Stage 3: Time from raw to processed topic (processor lag)
const endToEnd = new Trend('cdc_end_to_end_ms', true);               // Total: Time from POST to processed topic
const timeouts = new Counter('cdc_timeouts');
const eventsPosted = new Counter('events_posted');
const rawEventsFound = new Counter('raw_events_found');
const processedEventsFound = new Counter('processed_events_found');

// Test configuration
export const options = {
  vus: 1,
  iterations: 10,
  maxDuration: '5m',  // Allow up to 5 minutes for test completion
  noVUConnectionReuse: false,
  thresholds: {
    // Stage 1: API response time (should be fast - just DB insert)
    'cdc_api_response_ms': ['p(95)<50'],  // API should respond in <50ms

    // Stage 2: Debezium lag (MongoDB → Kafka)
    'cdc_http_to_raw_ms': ['p(95)<500'],  // Raw event should appear in <500ms

    // Stage 3: Processor lag (Raw topic → Processed topic)
    'cdc_raw_to_processed_ms': ['p(95)<500'],  // Processing should take <500ms

    // End-to-end: Total CDC pipeline latency
    'cdc_end_to_end_ms': [
      'p(50)<500',   // Median under 500ms
      'p(95)<1000',  // 95th percentile under 1 second
      'p(99)<2000',  // 99th percentile under 2 seconds
    ],

    'cdc_timeouts': ['count==0'], // Zero timeouts
    'http_req_failed': ['rate<0.01'], // Less than 1% HTTP errors
  },
};

const KAFKA_BROKERS = ['localhost:9092'];
const RAW_TOPIC = 'events.events_development.event';
const PROCESSED_TOPIC = 'events.events_development.processedevent';
const API_ENDPOINT = 'http://localhost:4055/event/api';
const MAX_WAIT_MS = 15000;

// Kafka consumers (initialized once per VU)
let rawReader, processedReader;

export default function () {
  // Initialize readers if not already done (once per VU)
  if (!rawReader) {
    console.log(`[VU ${__VU}] Initializing Kafka consumers...`);
    rawReader = new Reader({
      brokers: KAFKA_BROKERS,
      groupTopics: [RAW_TOPIC],
      groupID: `k6-raw-${__VU}-${Date.now()}`,
      maxWait: '1s',
    });
  }

  if (!processedReader) {
    processedReader = new Reader({
      brokers: KAFKA_BROKERS,
      groupTopics: [PROCESSED_TOPIC],
      groupID: `k6-processed-${__VU}-${Date.now()}`,
      maxWait: '1s',
    });
  }

  const correlationId = generateUUID();
  const t0 = Date.now();

  // Step 1: POST event via HTTP
  const payload = JSON.stringify({
    name: `k6_test_${correlationId}`,
    correlationId: correlationId,
    data: JSON.stringify({ test: true, vu: __VU, iter: __ITER, timestamp: t0 }),
    timestamp: new Date(t0).toISOString(),
    source: 'k6_cdc_latency_test',
    version: '1.0',
  });

  const response = http.post(API_ENDPOINT, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
    },
    timeout: '5s',
  });

  const tApiResponse = Date.now();
  const apiLatency = tApiResponse - t0;

  check(response, {
    'HTTP POST successful': (r) => r.status === 200 || r.status === 303 || r.status === 201,
  }) || console.error(`HTTP POST failed: ${response.status} ${response.body}`);

  // Stage 1: API response time
  apiResponseTime.add(apiLatency);
  eventsPosted.add(1);

  // Step 2: Wait for event in raw Kafka topic
  let tRaw = null;
  const rawStartTime = Date.now();

  while (Date.now() - rawStartTime < MAX_WAIT_MS && tRaw === null) {
    const messages = rawReader.consume({ limit: 50 });

    for (let i = 0; i < messages.length; i++) {
      const message = messages[i];
      const value = String.fromCharCode.apply(null, message.value);

      if (value.includes(correlationId)) {
        tRaw = Date.now();
        rawEventsFound.add(1);
        break;
      }
    }
  }

  if (tRaw === null) {
    console.error(`⏱ TIMEOUT waiting for raw event: ${correlationId}`);
    timeouts.add(1);
    return;
  }

  const latHttpToRaw = tRaw - t0;
  httpToRaw.add(latHttpToRaw);

  // Step 3: Wait for event in processed Kafka topic
  let tProcessed = null;
  const processedStartTime = Date.now();

  while (Date.now() - processedStartTime < MAX_WAIT_MS && tProcessed === null) {
    const messages = processedReader.consume({ limit: 50 });

    for (let i = 0; i < messages.length; i++) {
      const message = messages[i];
      const value = String.fromCharCode.apply(null, message.value);

      if (value.includes(correlationId)) {
        tProcessed = Date.now();
        processedEventsFound.add(1);
        break;
      }
    }
  }

  if (tProcessed === null) {
    console.error(`⏱ TIMEOUT waiting for processed event: ${correlationId}`);
    timeouts.add(1);
    return;
  }

  const latRawToProcessed = tProcessed - tRaw;
  const latEndToEnd = tProcessed - t0;

  rawToProcessed.add(latRawToProcessed);
  endToEnd.add(latEndToEnd);

  console.log(`✓ ${correlationId}: HTTP→Raw=${latHttpToRaw}ms, Raw→Processed=${latRawToProcessed}ms, E2E=${latEndToEnd}ms`);
}

// Helper: Generate UUID v4
function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
