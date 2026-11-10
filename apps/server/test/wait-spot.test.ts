import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';
import { demoWeather } from '../src/modules/weather/weather.ts';

const device = 'device-with-unguessable-random-identifier';

// 예시 시설: 북문(2) ↔ 1층 북문 로비(2)·2층 가족휴게실(3), 서문(1) ↔ 1층 서문 로비(1)·2층 가족휴게실(3)
test('동승자가 고른 대기 지점이 운전자에게 보이고, 만날 곳이 바뀌어 동선이 없으면 지워진다', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-wait-'));
  const db = openDb(join(directory, 'majung.sqlite'));
  const app = await buildApp(db, async () => null, { weather: demoWeather('rain') });
  const call = async (method: 'GET' | 'POST' | 'PATCH', url: string, payload?: object, status = 200) => {
    const res = await app.inject({ method, url, payload });
    assert.equal(res.statusCode, status, `${method} ${url}: ${res.body}`);
    return res.json();
  };
  try {
    const facilities = await call('GET', '/api/facilities');
    const north = facilities[0].accessPoints.find((p: { id: string }) => p.id === '2');
    assert.deepEqual(north.waitSpots.map((w: { name: string }) => w.name), ['1층 북문 로비', '2층 가족휴게실']);

    const session = await call('POST', '/api/visits', { requestId: 'c', facilityId: '1', mode: 'PICKUP', accessPointId: '2', driverDeviceId: device }, 201);
    const passenger = `/api/visits/${session.passengerToken}`;
    const driver = `/api/visits/${session.driverToken}`;
    let snap = await call('GET', passenger);
    assert.equal(snap.passengerWaitSpot, null);

    snap = await call('PATCH', passenger, { requestId: 'w1', version: snap.visit.version, passengerWaitSpotId: '2' });
    assert.equal(snap.passengerWaitSpot.name, '1층 북문 로비');
    assert.equal(snap.passengerWaitSpot.walkMinutes, 1);
    assert.equal((await call('GET', driver)).passengerWaitSpot.name, '1층 북문 로비');

    await call('PATCH', driver, { requestId: 'w-driver', version: snap.visit.version, passengerWaitSpotId: '3' }, 403);
    await call('PATCH', passenger, { requestId: 'w-far', version: snap.visit.version, passengerWaitSpotId: '1' }, 400); // 서문 로비는 북문과 동선 없음

    // 서문으로 바꾸면 북문 로비에서는 갈 동선이 없으므로 지워진다
    snap = await call('POST', passenger + '/proposals', { requestId: 'p1', version: snap.visit.version, accessPointId: '1' });
    snap = await call('POST', driver + `/proposals/${snap.proposals[0].id}/respond`, { requestId: 'a1', version: snap.visit.version, accept: true });
    assert.equal(snap.visit.confirmedAccessPointId, '1');
    assert.equal(snap.passengerWaitSpot, null);

    // 가족휴게실은 서문·북문 둘 다 이어지므로 다시 바꿔도 남는다
    snap = await call('PATCH', passenger, { requestId: 'w2', version: snap.visit.version, passengerWaitSpotId: '3' });
    snap = await call('POST', passenger + '/proposals', { requestId: 'p2', version: snap.visit.version, accessPointId: '2' });
    snap = await call('POST', driver + `/proposals/${snap.proposals[0].id}/respond`, { requestId: 'a2', version: snap.visit.version, accept: true });
    assert.equal(snap.passengerWaitSpot.name, '2층 가족휴게실');

    snap = await call('PATCH', passenger, { requestId: 'w3', version: snap.visit.version, passengerWaitSpotId: null });
    assert.equal(snap.passengerWaitSpot, null);

    const history = await call('GET', `/api/history?driverDeviceId=${device}`);
    assert.equal(history[0].weather.rainSoon, true);
  } finally {
    await app.close(); db.close(); rmSync(directory, { recursive: true, force: true });
  }
});

test('예전 DB에 대기 지점 칸이 없으면 열 때 추가한다', () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-migrate-'));
  const path = join(directory, 'majung.sqlite');
  try {
    let db = openDb(path);
    db.exec('ALTER TABLE api_visit_state DROP COLUMN passenger_wait_spot_id');
    db.close();
    db = openDb(path);
    const columns = db.prepare('PRAGMA table_info(api_visit_state)').all() as { name: string }[];
    assert.ok(columns.some((c) => c.name === 'passenger_wait_spot_id'));
    db.close();
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
