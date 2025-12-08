/**
 * k6 CDC Latency Test (correlation-aware)
 * - Sends BATCH_SIZE events via HTTP
 * - Consumes from RAW_TOPIC and PROCESSED_TOPIC with fresh groups (tail)
 * - Matches only our own messages by correlationId (key, headers, or JSON value)
 * - Emits per-message and batch-level latency metrics
 *
 * Build:
 *   xk6 build --with github.com/mostafa/xk6-kafka@latest
 *
 * Run:
 *   ./k6 run cdc-latency-batch.k6.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Gauge } from 'k6/metrics';
import { Reader } from 'k6/x/kafka';

// ---------- Metrics ----------
const apiResponseTime = new Trend('cdc_api_response_ms', true);
const httpToRaw = new Trend('cdc_http_to_raw_ms', true);
const rawToProcessed = new Trend('cdc_raw_to_processed_ms', true);
const httpToProcessed = new Trend('cdc_end_to_end_ms', true);

const batchSubmitTime = new Trend('batch_submit_time_ms', true);
const rawTopicWaitTime = new Trend('raw_topic_wait_ms', true);
const processedTopicWaitTime = new Trend('processed_topic_wait_ms', true);

const rawMessagesGauge = new Gauge('raw_messages_count');
const processedMessagesGauge = new Gauge('processed_messages_count');

const httpErrors = new Counter('http_errors');
const timeouts = new Counter('timeouts');

// ---------- Options ----------
export const options = {
  vus: 1,
  iterations: 1,
  maxDuration: '10m',
  thresholds: {
    'cdc_api_response_ms': ['p(95)<100'],
    'cdc_http_to_raw_ms': ['p(95)<2000'],
    'cdc_raw_to_processed_ms': ['p(95)<1000'],
    'cdc_end_to_end_ms': ['p(95)<3000'],
    'http_errors': ['count==0'],
    'timeouts': ['count==0'],
  },
};

// ---------- Config ----------
const KAFKA_BROKERS = ['localhost:9092'];
const RAW_TOPIC = 'events.events_development.event';
const PROCESSED_TOPIC = 'events.events_development.processedevent';
const API_ENDPOINT = 'http://localhost:4055/event/api';

const BATCH_SIZE = 10;  // Start small for testing
const POLL_LIMIT = 200;              // max messages per consume() call
const POLL_INTERVAL_MS = 100;        // sleep between polls
const RAW_TOPIC_TIMEOUT_MS = 120000; // overall time budget to see all our RAW messages
const PROCESSED_TOPIC_TIMEOUT_MS = 120000; // time budget after last PROCESSED seen progress

export default function () {
  console.log(`Starting CDC latency test, batch size: ${BATCH_SIZE}`);

  // ---------- Step 1: Create Kafka readers ----------
  // Read from offset 0 (beginning) with no consumer group - we filter by correlationId
  console.log('Step 1: Creating Kafka readers...');
  const rawReader = new Reader({
    brokers: KAFKA_BROKERS,
    topic: RAW_TOPIC,
    partition: 0,
    offset: 0,  // Start from beginning explicitly
    maxWait: '10s',
  });
  const procReader = new Reader({
    brokers: KAFKA_BROKERS,
    topic: PROCESSED_TOPIC,
    partition: 0,
    offset: 0,  // Start from beginning explicitly
    maxWait: '10s',
  });
  console.log('✓ Readers ready');

  // ---------- Generate IDs & start batch timer ----------
  const batchStartTime = Date.now();
  const ids = [];
  for (let i = 0; i < BATCH_SIZE; i++) ids.push(generateUUID());

  // For per-id timing we store when we POSTed them
  const tPost = new Map();        // id -> ms
  const tRawSeen = new Map();     // id -> ms
  const tProcessedSeen = new Map(); // id -> ms

  // ---------- Submit batch via HTTP ----------
  console.log('Step 2: Submitting HTTP batch...');
  const submitStart = Date.now();

  for (let i = 0; i < BATCH_SIZE; i++) {
    const id = ids[i];
    const payload = JSON.stringify({
      name: `k6_batch_test_${i}`,
      correlationId: id,
      data: JSON.stringify({ test: true, batch: true, index: i, postedAt: Date.now() }),
      timestamp: new Date().toISOString(),
      source: 'k6_cdc_batch_test',
      version: '1.0',
    });

    const resp = http.post(API_ENDPOINT, payload, {
      headers: { 'Content-Type': 'application/json', 'X-Correlation-Id': id },
      timeout: '5s',
    });

    tPost.set(id, Date.now());
    const ok = check(resp, {
      'HTTP POST ok': (r) => r.status >= 200 && r.status < 400,
    });
    if (!ok) {
      console.error(`HTTP POST failed for ${id}: status=${resp.status}`);
      httpErrors.add(1);
    }
    apiResponseTime.add(resp.timings.duration);
  }

  const submitMs = Date.now() - submitStart;
  batchSubmitTime.add(submitMs);
  console.log(`✓ Submitted ${BATCH_SIZE} events in ${submitMs} ms`);

  // ---------- Consume from RAW topic ----------
  console.log(`Step 3: Polling RAW topic for our ${BATCH_SIZE} events...`);
  const rawStart = Date.now();
  let matchedRaw = 0;
  while (Date.now() - rawStart < RAW_TOPIC_TIMEOUT_MS && matchedRaw < BATCH_SIZE) {
    let msgs = [];
    try {
      msgs = rawReader.consume({ limit: POLL_LIMIT });
    } catch (e) {
      console.warn(`RAW consume error: ${e.message || e}`);
    }

    if (msgs && msgs.length) {
      // Debug: show what we're looking for vs what we got
      if (matchedRaw === 0 && msgs.length > 0) {
        const m = msgs[0];
        const extractedId = extractCorrelationId(m);
        const lookingFor = Array.from(tPost.keys()).slice(0, 3);
        console.log(`DEBUG: Looking for correlationIds like: ${lookingFor.join(', ')}`);
        console.log(`DEBUG: First message extractedId: ${extractedId}`);
        console.log(`DEBUG: First 3 ids in tPost: ${lookingFor.join(', ')}`);
        console.log(`DEBUG: tPost has ${extractedId}? ${tPost.has(extractedId)}`);
      }
      for (const m of msgs) {
        const id = extractCorrelationId(m);
        // Debug: log first few messages to see structure
        if (matchedRaw === 0 && Math.random() < 0.05) {
          console.log(`DEBUG RAW: id=${id}, msgKeys=${Object.keys(m || {}).join(',')}, valueType=${typeof m?.value}`);
        }
        if (id && tPost.has(id) && !tRawSeen.has(id)) {
          const seen = Date.now(); // if m.timestamp is exposed by xk6-kafka, prefer it here
          tRawSeen.set(id, seen);
          matchedRaw++;
          rawMessagesGauge.add(matchedRaw);

          // Per-message HTTP->RAW latency
          httpToRaw.add(seen - tPost.get(id));
        }
      }
      console.log(`RAW matched ${matchedRaw}/${BATCH_SIZE} (received ${msgs.length})`);
    }

    if (matchedRaw >= BATCH_SIZE) break;
    sleep(POLL_INTERVAL_MS / 1000);
  }

  if (matchedRaw < BATCH_SIZE) {
    console.error(`⏱ RAW TIMEOUT: matched ${matchedRaw}/${BATCH_SIZE}`);
    timeouts.add(1);
    return;
  }

  const rawDoneMs = Date.now() - rawStart;
  rawTopicWaitTime.add(rawDoneMs);
  console.log(`✓ RAW complete in ${rawDoneMs} ms`);

  // ---------- Consume from PROCESSED topic ----------
  console.log(`Step 4: Polling PROCESSED topic for our ${BATCH_SIZE} events...`);
  const procStart = Date.now();
  let matchedProcessed = 0;
  let lastProgressAt = Date.now();

  while (Date.now() - lastProgressAt < PROCESSED_TOPIC_TIMEOUT_MS && matchedProcessed < BATCH_SIZE) {
    let msgs = [];
    try {
      msgs = procReader.consume({ limit: POLL_LIMIT });
    } catch (e) {
      console.warn(`PROCESSED consume error: ${e.message || e}`);
    }

    if (msgs && msgs.length) {
      for (const m of msgs) {
        const id = extractCorrelationId(m);
        // Only count IDs from our batch, and only once
        if (id && tRawSeen.has(id) && !tProcessedSeen.has(id)) {
          const seen = Date.now(); // prefer m.timestamp if available
          tProcessedSeen.set(id, seen);
          matchedProcessed++;
          processedMessagesGauge.add(matchedProcessed);
          lastProgressAt = Date.now();

          // Per-message latencies
          rawToProcessed.add(seen - tRawSeen.get(id));
          httpToProcessed.add(seen - tPost.get(id));
        }
      }
      console.log(`PROCESSED matched ${matchedProcessed}/${BATCH_SIZE} (received ${msgs.length})`);
    }

    if (matchedProcessed >= BATCH_SIZE) break;
    sleep(POLL_INTERVAL_MS / 1000);
  }

  if (matchedProcessed < BATCH_SIZE) {
    console.error(`⏱ PROCESSED TIMEOUT: matched ${matchedProcessed}/${BATCH_SIZE}; last progress ${Date.now() - lastProgressAt} ms ago`);
    timeouts.add(1);
    return;
  }

  const procDoneMs = Date.now() - procStart;
  processedTopicWaitTime.add(procDoneMs);

  // ---------- Batch-level summaries ----------
  const endToEndMs = Date.now() - batchStartTime;
  console.log('📊 CDC Batch Metrics');
  console.log(`  Submit batch:          ${submitMs} ms`);
  console.log(`  HTTP → RAW (all):      ${rawDoneMs} ms`);
  console.log(`  RAW → PROCESSED (all): ${procDoneMs} ms`);
  console.log(`  HTTP → PROCESSED (all):${endToEndMs} ms`);

  // also record "all done" as end-to-end
  // (individual per-message httpToProcessed metrics already added above)
  // If you want an explicit batch metric:
  // endToEnd.add(endToEndMs);
}

// ---------- Helpers ----------

// Extract correlationId from a Kafka message.
// This logic EXACTLY matches what the BDD test does (test/events.bdd.ts lines 111-113).
function extractCorrelationId(msg) {
  // We could check key/headers, but the BDD test proves the correlationId
  // is reliably in the message value, so we'll focus there.

  // value: flat JSON or Debezium envelope
  try {
    const valStr = bytesToString(msg.value || '');
    if (!valStr) return null;

    try {
      const value = JSON.parse(valStr);
      if (!value || typeof value !== 'object') return null;

      // Extract Debezium envelope: payload.after (might be string or object)
      const afterString = value?.payload?.after || value?.after;
      if (!afterString) return null;

      // Parse after if it's a JSON string (standard Debezium MongoDB connector behavior)
      const afterDoc = typeof afterString === 'string' ? JSON.parse(afterString) : afterString;

      // The actual event data is at afterDoc.payload (MeshQL REST API wrapper)
      // This matches exactly what the BDD test does!
      const eventData = afterDoc?.payload || afterDoc;

      // Return correlationId from the event data
      const correlationId = eventData?.correlationId || null;

      // DEBUG: Log first few extractions to understand the structure
      if (!correlationId && Math.random() < 0.05) {  // 5% sample
        console.log(`DEBUG extractCorrelationId: eventData keys=${Object.keys(eventData|| {}).join(',')}`);
      }

      return correlationId;
    } catch (_e) {
      // Parse error - not valid JSON
      if (Math.random() < 0.05) {  // 5% sample
        console.log(`DEBUG extractCorrelationId: JSON parse error=${_e.message || _e}`);
      }
      return null;
    }
  } catch (_e) {}

  return null;
}

function bytesToString(b) {
  if (!b) return '';
  // xk6-kafka returns strings already; but if we ever get byte arrays, handle it.
  if (typeof b === 'string') return b;
  if (Array.isArray(b)) return String.fromCharCode.apply(null, b);
  return String(b);
}

function isUUID(s) {
  return typeof s === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s);
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
