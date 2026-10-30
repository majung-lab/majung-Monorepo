import assert from 'node:assert/strict';
import { test } from 'node:test';
import * as client from '../src/api/client.ts';
const { api } = client;

test('passenger proposal sends the token, current version and one request identity', async () => {
  const original = globalThis.fetch;
  try {
    let actual: { path: string; init?: RequestInit } | undefined;
    globalThis.fetch = async (path, init) => {
      actual = { path: String(path), init };
      return new Response(JSON.stringify({ visit: { version: 4 } }), { status: 200 });
    };
    assert.equal(typeof api.propose, 'function', 'live proposal API must exist');
    const result = await api.propose('passenger/token', 3, '2');
    assert.equal(actual?.path, '/api/visits/passenger%2Ftoken/proposals');
    assert.equal(actual?.init?.method, 'POST');
    const body = JSON.parse(String(actual?.init?.body));
    assert.equal(body.version, 3);
    assert.equal(body.accessPointId, '2');
    assert.match(body.requestId, /^[0-9a-f]{32}$/);
    assert.equal(result.visit.version, 4);
  } finally { globalThis.fetch = original; }
});

test('version conflicts retain their status and readable server message', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'STALE_VERSION', message: '방문이 변경되었습니다' }), { status: 409 });
    await assert.rejects(api.getVisit('secret'), (error: unknown) => {
      assert.equal(typeof client.ApiError, 'function');
      assert.ok(error instanceof client.ApiError);
      assert.equal(error.status, 409);
      assert.equal(error.message, '방문이 변경되었습니다');
      assert.ok(!error.message.includes('secret'));
      return true;
    });
  } finally { globalThis.fetch = original; }
});

test('stopping location sharing clears the server position instead of sending a fallback coordinate', async () => {
  const original = globalThis.fetch;
  try {
    let body: Record<string, unknown> | undefined;
    globalThis.fetch = async (_path, init) => {
      body = JSON.parse(String(init?.body));
      return new Response('{}', { status: 200 });
    };
    assert.equal(typeof api.stopLocation, 'function');
    await api.stopLocation('passenger', 5);
    assert.equal(body?.version, 5);
    assert.equal(body?.passengerLocation, null);
  } finally { globalThis.fetch = original; }
});

test('LAN HTTP browsers can send mutations without crypto.randomUUID', async () => {
  const originalFetch = globalThis.fetch;
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto')!;
  const browserCrypto = globalThis.crypto;
  const requestIds = new Set<string>();
  try {
    Object.defineProperty(globalThis, 'crypto', { configurable: true, value: { getRandomValues: browserCrypto.getRandomValues.bind(browserCrypto) } });
    globalThis.fetch = async (_path, init) => {
      const { requestId } = JSON.parse(String(init?.body));
      assert.match(requestId, /^[0-9a-f]{32}$/);
      requestIds.add(requestId);
      return new Response('{"visit":{"version":2}}', { status: 200 });
    };
    for (let i = 0; i < 10; i++) await api.readiness('passenger', 1, 'READY');
    assert.equal(requestIds.size, 10);
  } finally {
    globalThis.fetch = originalFetch;
    Object.defineProperty(globalThis, 'crypto', descriptor);
  }
});
