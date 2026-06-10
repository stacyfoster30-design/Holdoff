/**
 * Automated test suite for /api/verdict endpoints.
 * Runs against the actual running server (no test DB mocking).
 *
 * Usage:
 *   node tests/verdict.test.js
 *
 * Prerequisites:
 *   - Server must be running on localhost:3000 (or override BASE_URL)
 *   - No AI providers required — fallback verdict returns HOLD gracefully
 */
const http = require('http');

const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';

// ── Test utilities ─────────────────────────────────────────────────────────────

function post(path, body, cookies = '') {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const bodyStr = JSON.stringify(body);
    const req = http.request({
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(bodyStr),
        ...(cookies ? { 'Cookie': cookies } : {}),
      },
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });
    req.on('error', reject);
    req.write(bodyStr);
    req.end();
  });
}

function get(path, cookies = '') {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const req = http.request({
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...(cookies ? { 'Cookie': cookies } : {}),
      },
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) throw new Error(`ASSERTION FAILED: ${message}`);
}

function assertIn(value, allowed, message) {
  if (!allowed.includes(value)) {
    throw new Error(`ASSERTION FAILED: ${message} — got "${value}", expected one of [${allowed.join(', ')}]`);
  }
}

// ── Test cases ────────────────────────────────────────────────────────────────

async function testEmptyMessage() {
  console.log('TEST: Empty message → 400');
  const res = await post('/api/verdict', { message_text: '' });
  assert(res.status === 400, `Expected 400, got ${res.status}`);
  assert(res.body.error && res.body.error.toLowerCase().includes('empty'), `Expected error about empty message, got: ${JSON.stringify(res.body)}`);
  console.log('  PASS');
}

async function testMissingMessageText() {
  console.log('TEST: Missing message_text → 400');
  const res = await post('/api/verdict', {});
  assert(res.status === 400, `Expected 400, got ${res.status}`);
  assert(res.body.error && res.body.error.toLowerCase().includes('required'), `Expected error about required field, got: ${JSON.stringify(res.body)}`);
  console.log('  PASS');
}

async function testValidMessage() {
  console.log('TEST: Valid message → 200 + valid verdict object');
  const messages = [
    'are we okay',
    'i miss you so much',
    'why arent you answering me',
    'just wanted to say goodnight',
    'i hate that you always do this',
  ];

  for (const message of messages) {
    const res = await post('/api/verdict', { message_text: message });
    assert(res.status === 200, `Message "${message}": expected 200, got ${res.status}`);
    assertIn(res.body.verdict, ['SEND', 'HOLD', 'REWRITE'], `verdict`);
    assert(res.body.feedback_text || res.body.whats_happening, `Message "${message}": missing feedback_text / whats_happening`);
    assert(res.body.pattern || res.body.pattern_name, `Message "${message}": missing pattern`);
    console.log(`  PASS: "${message}" → ${res.body.verdict}`);
  }
}

async function testRateLimitEnforced() {
  console.log('TEST: Rate limit → 429 on 31st request in a minute');

  // Use a fresh cookie jar to get a clean IP-based rate limit bucket
  const email = `ratelimit_${Date.now()}@test.example.com`;

  // First 30 should succeed
  const promises = [];
  for (let i = 0; i < 30; i++) {
    promises.push(post('/api/verdict', { message_text: `rate test ${i}` }));
  }
  const results = await Promise.all(promises);
  const all200 = results.every((r) => r.status === 200);
  console.log(`  First 30 requests: ${all200 ? 'all 200 OK' : 'some non-200: ' + results.map((r) => r.status).join(', ')}`);

  // 31st should be 429
  const res = await post('/api/verdict', { message_text: 'rate limit test' });
  assert(res.status === 429, `Expected 429 on 31st request, got ${res.status}`);
  assert(res.body.code === 'RATE_LIMITED', `Expected code RATE_LIMITED, got: ${JSON.stringify(res.body)}`);
  console.log('  PASS');
}

async function testHistoryNeedsAuth() {
  console.log('TEST: GET /api/verdict/history without auth → 401');
  const res = await get('/api/verdict/history');
  assert(res.status === 401, `Expected 401, got ${res.status}`);
  console.log('  PASS');
}

async function testStreakNeedsAuth() {
  console.log('TEST: GET /api/verdict/streak without auth → 401');
  const res = await get('/api/verdict/streak');
  assert(res.status === 401, `Expected 401, got ${res.status}`);
  console.log('  PASS');
}

async function testInvalidVerdictTypeInHistory() {
  console.log('TEST: GET /api/verdict/history?verdict_type=INVALID → 400');
  const res = await get('/api/verdict/history?verdict_type=INVALID');
  assert(res.status === 400, `Expected 400, got ${res.status}`);
  console.log('  PASS');
}

// ── Run all tests ─────────────────────────────────────────────────────────────

async function main() {
  console.log(`\nRunning verdict API tests against ${BASE_URL}\n`);
  const failures = [];

  const tests = [
    testEmptyMessage,
    testMissingMessageText,
    testValidMessage,
    testRateLimitEnforced,
    testHistoryNeedsAuth,
    testStreakNeedsAuth,
    testInvalidVerdictTypeInHistory,
  ];

  for (const test of tests) {
    try {
      await test();
    } catch (err) {
      console.error(`  FAIL: ${err.message}\n`);
      failures.push({ name: test.name, err });
    }
  }

  console.log('\n────────────────────────────────────');
  if (failures.length === 0) {
    console.log('ALL TESTS PASSED');
    process.exit(0);
  } else {
    console.log(`${failures.length} TEST(S) FAILED:`);
    for (const { name, err } of failures) {
      console.log(`  - ${name}: ${err.message}`);
    }
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Test runner error:', err);
  process.exit(1);
});
