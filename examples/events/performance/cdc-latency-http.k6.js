/**
 * k6 CDC Latency Test (HTTP-based)
 * - Sends BATCH_SIZE events via HTTP
 * - Polls processedEvent REST API for processed events
 * - Measures end-to-end CDC pipeline latency via HTTP
 *
 * Run:
 *   k6 run cdc-latency-http.k6.js
 *   # Or with custom k6 if you have it
 *   ./k6 run cdc-latency-http.k6.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Gauge } from 'k6/metrics';

// ---------- Metrics ----------
const apiResponseTime = new Trend('cdc_api_response_ms', true);
const httpToProcessed = new Trend('cdc_end_to_end_ms', true);

const batchSubmitTime = new Trend('batch_submit_time_ms', true);
const processedApiWaitTime = new Trend('processed_api_wait_ms', true);

const processedMessagesGauge = new Gauge('processed_messages_count');

const httpErrors = new Counter('http_errors');
const timeouts = new Counter('timeouts');

// ---------- Options ----------
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    'cdc_api_response_ms': ['p(95)<100'],
    'cdc_end_to_end_ms': ['p(95)<30000'],  // 30s for CDC end-to-end
    'http_errors': ['count==0'],
    'timeouts': ['count==0'],
  },
};

// ---------- Config ----------
const EVENT_API = 'http://localhost:4055/event/api';
const PROCESSED_EVENT_API = 'http://localhost:4055/processedevent/api';

const BATCH_SIZE = 100;
const POLL_INTERVAL_MS = 500;
const PROCESSED_TIMEOUT_MS = 60000;  // 60 seconds to find all processed events

export default function () {
  console.log(`Starting CDC latency test (HTTP-based), batch size: ${BATCH_SIZE}`);

  // ---------- Get initial count of processed events ----------
  const initialCount = getProcessedEventCount();
  console.log(`Initial processed event count: ${initialCount}`);

  // ---------- Generate IDs & start batch timer ----------
  const batchStartTime = Date.now();
  const ids = [];
  for (let i = 0; i < BATCH_SIZE; i++) ids.push(generateUUID());

  // For per-id timing we store when we POSTed them
  const tPost = new Map();

  // ---------- Submit batch via HTTP ----------
  console.log('Step 1: Submitting HTTP batch...');
  const submitStart = Date.now();

  for (let i = 0; i < BATCH_SIZE; i++) {
    const id = ids[i];
    const payload = JSON.stringify({
      name: `k6_http_test_${i}`,
      correlationId: id,
      data: JSON.stringify({ test: true, batch: true, index: i, postedAt: Date.now() }),
      timestamp: new Date().toISOString(),
      source: 'k6_cdc_http_test',
      version: '1.0',
    });

    const resp = http.post(EVENT_API, payload, {
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

  // ---------- Poll processed events API ----------
  console.log(`Step 2: Polling processed events API for ${BATCH_SIZE} new events...`);
  const pollStart = Date.now();
  const targetCount = initialCount + BATCH_SIZE;
  let currentCount = initialCount;
  let lastProgressAt = Date.now();

  while (Date.now() - pollStart < PROCESSED_TIMEOUT_MS && currentCount < targetCount) {
    sleep(POLL_INTERVAL_MS / 1000);

    const newCount = getProcessedEventCount();
    if (newCount > currentCount) {
      const progress = newCount - initialCount;
      console.log(`Processed: ${progress}/${BATCH_SIZE} (total: ${newCount})`);
      currentCount = newCount;
      processedMessagesGauge.add(progress);
      lastProgressAt = Date.now();
    }

    // If no progress for 30 seconds, something is wrong
    if (Date.now() - lastProgressAt > 30000) {
      console.error(`⏱ No progress for 30 seconds, last count: ${currentCount - initialCount}/${BATCH_SIZE}`);
      break;
    }
  }

  const pollMs = Date.now() - pollStart;
  processedApiWaitTime.add(pollMs);

  const matchedProcessed = currentCount - initialCount;
  if (matchedProcessed < BATCH_SIZE) {
    console.error(`⏱ PROCESSED TIMEOUT: found ${matchedProcessed}/${BATCH_SIZE}`);
    timeouts.add(1);
  } else {
    console.log(`✓ All ${BATCH_SIZE} events processed in ${pollMs} ms`);
  }

  // ---------- Calculate end-to-end latency ----------
  const endToEndMs = Date.now() - batchStartTime;
  httpToProcessed.add(endToEndMs);

  // ---------- Batch-level summaries ----------
  console.log('📊 CDC Pipeline Metrics (HTTP-based):');
  console.log(`  Submit batch:          ${submitMs} ms`);
  console.log(`  Wait for processing:   ${pollMs} ms`);
  console.log(`  End-to-end:            ${endToEndMs} ms`);
  console.log(`  Avg per event:         ${Math.round(endToEndMs / BATCH_SIZE)} ms`);
}

// ---------- Helpers ----------

function getProcessedEventCount() {
  const resp = http.get(PROCESSED_EVENT_API, { timeout: '5s' });
  if (resp.status !== 200) {
    console.error(`Failed to get processed events: ${resp.status}`);
    return 0;
  }
  try {
    const events = JSON.parse(resp.body);
    return Array.isArray(events) ? events.length : 0;
  } catch (e) {
    console.error(`Failed to parse processed events: ${e}`);
    return 0;
  }
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
