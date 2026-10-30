import assert from 'node:assert/strict';
import { test } from 'node:test';
import { subscribeVisit } from '../src/realtime/visitSocket.ts';

test('immediate React effect cleanup prevents opening an unused websocket', async () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'location');
  const original = globalThis.WebSocket;
  let opened = 0;
  try {
    Object.defineProperty(globalThis, 'location', { value: { protocol: 'http:', host: 'localhost' }, configurable: true });
    globalThis.WebSocket = class { constructor() { opened++; } close() {} } as unknown as typeof WebSocket;
    const stop = subscribeVisit('passenger', () => {});
    stop();
    await new Promise((resolve) => setTimeout(resolve, 10));
    assert.equal(opened, 0);
  } finally {
    globalThis.WebSocket = original;
    if (descriptor) Object.defineProperty(globalThis, 'location', descriptor);
    else Reflect.deleteProperty(globalThis, 'location');
  }
});
