import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';
import { demoWeather } from '../src/modules/weather/weather.ts';

const device = 'device-with-unguessable-random-identifier';

// 예시 시설 동선: 서문(1) ← 1층 서문 로비(1) 2분 · 2층 가족휴게실(3) 6분 계단
//                북문(2) ← 1층 북문 로비(2) 1분 지붕 · 2층 가족휴게실(3) 4분 엘리베이터·실내
const setup = async (weather = demoWeather('clear')) => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-wait-'));
  const db = openDb(join(directory, 'majung.sqlite'));
  const app = await buildApp(db, async () => null, { weather });
  const call = async (method: 'GET' | 'POST' | 'PATCH', url: string, payload?: object, status = 200) => {
    const res = await app.inject({ method, url, payload });
    assert.equal(res.statusCode, status, `${method} ${url}: ${res.body}`);
    return res.json();
  };
  const session = await call('POST', '/api/visits', { requestId: 'c', facilityId: '1', mode: 'PICKUP', accessPointId: '1', driverDeviceId: device }, 201);
  return { call, passenger: `/api/visits/${session.passengerToken}`, driver: `/api/visits/${session.driverToken}`,
    close: async () => { await app.close(); db.close(); rmSync(directory, { recursive: true, force: true }); } };
};
const none = { stroller: false, luggage: false, avoidStairs: false, indoorOnly: false };

test('동승자는 시설의 어느 대기 지점이든 고르고, 만날 곳이 바뀌어도 그대로 남는다', async () => {
  const { call, passenger, driver, close } = await setup(demoWeather('rain'));
  try {
    const facilities = await call('GET', '/api/facilities');
    assert.deepEqual(facilities[0].waitSpots.map((w: { name: string }) => w.name), ['1층 서문 로비', '1층 북문 로비', '2층 가족휴게실']);
    let snap = await call('GET', passenger);
    assert.equal(snap.passengerWaitSpot, null);
    assert.deepEqual(snap.passengerConditions, none);

    // 북문 로비는 서문(만날 곳)과 동선이 없지만 '지금 여기 있어요'로 고를 수 있다
    snap = await call('PATCH', passenger, { requestId: 'w1', version: snap.visit.version, passengerWaitSpotId: '2' });
    assert.equal(snap.passengerWaitSpot.name, '1층 북문 로비');
    assert.equal(snap.passengerWaitSpot.walkMinutes, null);
    snap = await call('PATCH', passenger, { requestId: 'w2', version: snap.visit.version, passengerWaitSpotId: '3' });
    assert.equal(snap.passengerWaitSpot.walkMinutes, 6);
    assert.equal((await call('GET', driver)).passengerWaitSpot.name, '2층 가족휴게실');

    await call('PATCH', driver, { requestId: 'w-driver', version: snap.visit.version, passengerWaitSpotId: '3' }, 403);
    await call('PATCH', passenger, { requestId: 'w-bad', version: snap.visit.version, passengerWaitSpotId: '99' }, 400);

    // 북문으로 바꾸면 같은 휴게실에서 북문까지의 동선(4분, 실내)으로 바뀐다
    snap = await call('POST', passenger + '/proposals', { requestId: 'p1', version: snap.visit.version, accessPointId: '2' });
    snap = await call('POST', driver + `/proposals/${snap.proposals[0].id}/respond`, { requestId: 'a1', version: snap.visit.version, accept: true });
    assert.equal(snap.passengerWaitSpot.name, '2층 가족휴게실');
    assert.equal(snap.passengerWaitSpot.walkMinutes, 4);
    assert.equal(snap.passengerWaitSpot.covered, true);

    snap = await call('PATCH', passenger, { requestId: 'w3', version: snap.visit.version, passengerWaitSpotId: null });
    assert.equal(snap.passengerWaitSpot, null);
    const history = await call('GET', `/api/history?driverDeviceId=${device}`);
    assert.equal(history[0].weather.rainSoon, true);
  } finally { await close(); }
});

test('이동 조건은 동승자만 바꾸고, 운전자 화면에도 보인다', async () => {
  const { call, passenger, driver, close } = await setup();
  try {
    let snap = await call('GET', passenger);
    const stroller = { ...none, stroller: true, avoidStairs: true };
    snap = await call('PATCH', passenger, { requestId: 'c1', version: snap.visit.version, passengerConditions: stroller });
    assert.deepEqual(snap.passengerConditions, stroller);
    assert.deepEqual((await call('GET', driver)).passengerConditions, stroller);
    await call('PATCH', driver, { requestId: 'c2', version: snap.visit.version, passengerConditions: none }, 403);
    await call('PATCH', passenger, { requestId: 'c3', version: snap.visit.version, passengerConditions: { stroller: true } }, 400);
  } finally { await close(); }
});

test('가족휴게실 + 유모차면 계단 있는 서문을 빼고 북문을 추천한다 (신청서 시나리오)', async () => {
  const { call, passenger, close } = await setup();
  try {
    // 대기 지점을 고르기 전에는 날씨·걷는 시간만 본다
    let rec = await call('GET', passenger + '/recommendation');
    assert.equal(rec.ranking[0].excludedReason, undefined);

    let snap = await call('GET', passenger);
    snap = await call('PATCH', passenger, { requestId: 's', version: snap.visit.version, passengerWaitSpotId: '3' });
    rec = await call('GET', passenger + '/recommendation');
    assert.deepEqual(rec.ranking.map((r: { accessPointId: string }) => r.accessPointId), ['2', '1']); // 조건 없어도 북문 4분 < 서문 6분
    assert.equal(rec.ranking[1].excludedReason, null);

    snap = await call('PATCH', passenger, { requestId: 'cond', version: snap.visit.version, passengerConditions: { ...none, stroller: true } });
    rec = await call('GET', passenger + '/recommendation');
    const [north, west] = rec.ranking;
    assert.equal(north.accessPointId, '2');
    assert.equal(north.recommended, true);
    assert.equal(north.reason, '계단 없이 걸어서 4분');
    assert.equal(west.recommended, false);
    assert.equal(west.excludedReason, '계단이 있어 유모차로는 돌아가야 해요');

    // 서문 로비에 있으면 북문까지는 동선이 없다
    snap = await call('PATCH', passenger, { requestId: 's2', version: snap.visit.version, passengerWaitSpotId: '1' });
    rec = await call('GET', passenger + '/recommendation');
    assert.equal(rec.ranking[0].accessPointId, '1');
    assert.equal(rec.ranking[1].excludedReason, '기다리는 곳에서 가는 동선 정보가 없어요');
    await call('GET', '/api/visits/not-a-token/recommendation', undefined, 404);
  } finally { await close(); }
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
