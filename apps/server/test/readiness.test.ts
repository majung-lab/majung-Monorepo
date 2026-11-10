import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';

test('동승자 준비 단계 다섯 가지가 각각 저장되고 운전자에게 보인다 (로비 도착 포함)', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-ready-'));
  const db = openDb(join(directory, 'majung.sqlite'));
  const app = await buildApp(db, async () => null);
  const call = async (method: 'GET' | 'POST' | 'PATCH', url: string, payload?: object, status = 200) => {
    const res = await app.inject({ method, url, payload });
    assert.equal(res.statusCode, status, `${method} ${url}: ${res.body}`);
    return res.json();
  };
  try {
    const s = await call('POST', '/api/visits', { requestId: 'c', facilityId: '1', mode: 'PICKUP', accessPointId: '2', driverDeviceId: 'device-with-unguessable-random-identifier' }, 201);
    const passenger = `/api/visits/${s.passengerToken}`, driver = `/api/visits/${s.driverToken}`;
    let snap = await call('GET', passenger);
    assert.equal(snap.visit.passengerReadiness, 'UNSEEN');
    for (const step of ['SEEN', 'READY', 'HEADING_OUT', 'AT_LOBBY']) {
      snap = await call('PATCH', passenger, { requestId: `r-${step}`, version: snap.visit.version, passengerReadiness: step });
      assert.equal(snap.visit.passengerReadiness, step);
      assert.equal((await call('GET', driver)).visit.passengerReadiness, step);
    }
    await call('PATCH', driver, { requestId: 'r-driver', version: snap.visit.version, passengerReadiness: 'AT_LOBBY' }, 403);
    await call('PATCH', passenger, { requestId: 'r-bad', version: snap.visit.version, passengerReadiness: 'BOARDED' }, 400);
  } finally {
    await app.close(); db.close(); rmSync(directory, { recursive: true, force: true });
  }
});
