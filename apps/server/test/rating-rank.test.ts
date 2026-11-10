import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { AccessPoint } from '@majung/shared';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';
import { demoWeather, rankAccessPoints } from '../src/modules/weather/weather.ts';

const point = (id: string, walkMinutes: number, covered: boolean): AccessPoint =>
  ({ id, name: id, lat: 0, lng: 0, description: '', kind: 'PICKUP', allowed: true, waitSpot: null, walkMinutes, covered, waitSpots: [] });

test('지난 평가: 좋았던 곳은 앞으로, 별로였던 곳은 뒤로. 비 예보의 지붕이 평가보다 먼저', async () => {
  const points = [point('1', 1, false), point('2', 3, false), point('3', 5, true)];
  assert.deepEqual(rankAccessPoints(points, null).map((r) => r.accessPointId), ['1', '2', '3']);

  const liked = rankAccessPoints(points, null, new Map([['2', 5]]));
  assert.deepEqual(liked.map((r) => r.accessPointId), ['2', '1', '3']);
  assert.equal(liked[0].reason, '지난번에 좋았던 곳이에요');

  const disliked = rankAccessPoints(points, null, new Map([['1', 1]]));
  assert.deepEqual(disliked.map((r) => r.accessPointId), ['2', '3', '1']);
  assert.equal(disliked[2].reason, '지난번 평가가 좋지 않았어요');

  // 보통(3)은 순서에 영향 없음
  assert.deepEqual(rankAccessPoints(points, null, new Map([['3', 3]])).map((r) => r.accessPointId), ['1', '2', '3']);

  const wet = rankAccessPoints(points, await demoWeather('rain')(0, 0), new Map([['2', 5], ['3', 1]]));
  assert.equal(wet[0].accessPointId, '3'); // 별로였어도 비 오면 지붕 있는 곳이 먼저
  assert.deepEqual(wet.slice(1).map((r) => r.accessPointId), ['2', '1']);
});

test('평가를 남기면 같은 운전자의 다음 추천 순서가 바뀐다', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-rating-'));
  const db = openDb(join(directory, 'majung.sqlite'));
  const app = await buildApp(db, async () => null, { weather: demoWeather('clear') });
  const device = 'device-with-unguessable-random-identifier';
  const call = async (method: 'GET' | 'POST' | 'PATCH', url: string, payload?: object) => {
    const res = await app.inject({ method, url, payload });
    assert.ok(res.statusCode < 300, `${method} ${url}: ${res.body}`);
    return res.json();
  };
  const order = async (query: string) => (await call('GET', `/api/facilities/1/weather?mode=PICKUP${query}`)).ranking.map((r: { accessPointId: string }) => r.accessPointId);
  try {
    // 맑으면 걷는 시간 순: 북문(1분) → 서문(2분)
    assert.deepEqual(await order(''), ['2', '1']);

    const session = await call('POST', '/api/visits', { requestId: 'v1', facilityId: '1', mode: 'PICKUP', accessPointId: '2', driverDeviceId: device });
    const driver = `/api/visits/${session.driverToken}`;
    let snap = await call('PATCH', driver, { requestId: 'm', version: session.visit.version, phase: 'MOVING' });
    snap = await call('PATCH', driver, { requestId: 'a', version: snap.visit.version, phase: 'ARRIVED' });
    snap = await call('PATCH', driver, { requestId: 'c', version: snap.visit.version, phase: 'COMPLETED' });
    await call('POST', driver + '/rating', { requestId: 'r', version: snap.visit.version, rating: 1, tags: ['차가 많음'] });

    const ranked = (await call('GET', `/api/facilities/1/weather?mode=PICKUP&driverDeviceId=${device}`)).ranking;
    assert.deepEqual(ranked.map((r: { accessPointId: string }) => r.accessPointId), ['1', '2']);
    assert.equal(ranked[1].reason, '지난번 평가가 좋지 않았어요');
    assert.equal(ranked[0].reason, '지난번 별로였던 곳 대신 추천해요'); // 서문은 가장 가깝지 않으므로 '가장 가까워요'라고 하지 않는다
    assert.deepEqual(await order('&driverDeviceId=another-device-with-random-identifier'), ['2', '1']); // 다른 운전자는 그대로

    // 새 방문을 만들면 후보 순위에도 반영된다
    await call('POST', '/api/visits', { requestId: 'v2', facilityId: '1', mode: 'PICKUP', accessPointId: '2', driverDeviceId: device });
    const ranks = db.prepare('SELECT c.access_point_id AS ap FROM candidate c JOIN visit v ON v.id=c.visit_id ORDER BY v.created_at DESC, c.rank LIMIT 2').all() as { ap: number }[];
    assert.deepEqual(ranks.map((r) => r.ap), [1, 2]);
  } finally {
    await app.close(); db.close(); rmSync(directory, { recursive: true, force: true });
  }
});
