import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { AccessPoint } from '@majung/shared';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';
import { baseTime, demoWeather, rankAccessPoints, rankForPassenger, summarize, toGrid, type ForecastItem } from '../src/modules/weather/weather.ts';

test('위경도를 기상청 격자로 바꾼다 (서울시청 = 60,127)', () => {
  assert.deepEqual(toGrid(37.5665, 126.978), { nx: 60, ny: 127 });
});

test('지금 받을 수 있는 가장 최근 발표 시각을 고른다', () => {
  assert.deepEqual(baseTime(new Date('2026-11-09T05:05:00Z')), { date: '20261109', time: '1100' }); // KST 14:05
  assert.deepEqual(baseTime(new Date('2026-11-09T05:15:00Z')), { date: '20261109', time: '1400' }); // KST 14:15
  assert.deepEqual(baseTime(new Date('2026-11-08T16:00:00Z')), { date: '20261108', time: '2300' }); // KST 01:00 → 전날 23시
});

const hour = (time: string, values: Record<string, string>): ForecastItem[] =>
  Object.entries(values).map(([category, fcstValue]) => ({ category, fcstDate: '20261109', fcstTime: time, fcstValue }));

test('곧 비가 오면 시작 시각과 강수확률을 한 줄로 보여 준다', () => {
  const items = [
    ...hour('1300', { TMP: '10', SKY: '1', PTY: '1', POP: '90' }), // 지난 시간은 무시
    ...hour('1400', { TMP: '14.4', SKY: '4', PTY: '0', POP: '30' }),
    ...hour('1500', { TMP: '13', SKY: '4', PTY: '1', POP: '70' }),
  ];
  const w = summarize(items, new Date('2026-11-09T05:00:00Z'))!;
  assert.equal(w.summary, '15시부터 비 · 강수확률 70%');
  assert.equal(w.rainSoon, true);
  assert.equal(w.condition, 'CLOUDY');
  assert.equal(w.tempC, 14);
});

test('지금 비가 오거나, 맑으면 그에 맞게', () => {
  const now = new Date('2026-11-09T05:00:00Z');
  assert.equal(summarize(hour('1400', { TMP: '12', SKY: '4', PTY: '1', POP: '80' }), now)!.summary, '지금 비 · 12°');
  const clear = summarize(hour('1400', { TMP: '18', SKY: '1', PTY: '0', POP: '10' }), now)!;
  assert.equal(clear.summary, '맑음 · 18°');
  assert.equal(clear.rainSoon, false);
  assert.equal(summarize([], now), null);
});

const point = (id: string, walkMinutes: number | null, covered: boolean): AccessPoint =>
  ({ id, name: id, lat: 0, lng: 0, description: '', kind: 'PICKUP', allowed: true, waitSpot: null, walkMinutes, covered, waitSpots: [] });

test('비가 오면 지붕 있는 곳이 먼저, 아니면 가까운 곳이 먼저', async () => {
  const points = [point('1', 1, false), point('2', 3, true)];
  const dry = rankAccessPoints(points, null);
  assert.deepEqual(dry.map((r) => r.accessPointId), ['1', '2']);
  assert.match(dry[0].reason!, /가장 가까워요/);
  const wet = rankAccessPoints(points, await demoWeather('rain')(0, 0));
  assert.deepEqual(wet.map((r) => r.accessPointId), ['2', '1']);
  assert.equal(wet[0].recommended, true);
  assert.match(wet[0].reason!, /지붕 아래/);
  assert.match(wet[1].reason!, /비를 맞을 수/);
});

test('동승자 추천: 비가 오면 실내 연결을 먼저, 지붕 없는 동선은 경고한다', async () => {
  const points = [point('1', null, false), point('2', null, false)];
  const links = new Map([['1', { walkMinutes: 2, covered: false, noStairs: true, strollerOk: true }], ['2', { walkMinutes: 4, covered: true, noStairs: true, strollerOk: true }]]);
  const conditions = { stroller: true, luggage: false, avoidStairs: false, indoorOnly: false };
  const wet = rankForPassenger(points, await demoWeather('rain')(0, 0), { links, conditions });
  assert.deepEqual(wet.map((r) => r.accessPointId), ['2', '1']);
  assert.equal(wet[0].reason, '계단 없이, 비 맞지 않고 걸어서 4분');
  assert.equal(wet[1].reason, '계단 없이 걸어서 2분 · 지붕이 없어 비를 맞을 수 있어요');
  const dry = rankForPassenger(points, null, { links, conditions });
  assert.deepEqual(dry.map((r) => r.accessPointId), ['1', '2']);
});

test('시설 날씨 API와 방문 기록에 날씨가 남는다', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-weather-'));
  const db = openDb(join(directory, 'majung.sqlite'));
  const app = await buildApp(db, async () => null, { weather: demoWeather('rain') });
  try {
    const res = await app.inject({ method: 'GET', url: '/api/facilities/1/weather?mode=PICKUP' });
    assert.equal(res.statusCode, 200);
    const body = res.json();
    assert.equal(body.weather.source, 'DEMO');
    assert.equal(body.ranking[0].accessPointId, '2'); // 북문: 지붕 있음
    assert.ok(body.ranking.every((r: { accessPointId: string }) => r.accessPointId !== '3')); // 승하차 금지 구역은 빠진다
    assert.equal((await app.inject({ method: 'GET', url: '/api/facilities/99/weather' })).statusCode, 404);
    assert.equal((await app.inject({ method: 'GET', url: '/api/facilities/1/weather?mode=FLY' })).statusCode, 400);

    const created = await app.inject({ method: 'POST', url: '/api/visits', payload: { requestId: 'w-1', facilityId: '1', mode: 'PICKUP', accessPointId: '1', driverDeviceId: 'device-with-unguessable-random-identifier' } });
    assert.equal(created.statusCode, 201);
    const saved = db.prepare('SELECT weather_json FROM visit').get() as { weather_json: string };
    assert.equal(JSON.parse(saved.weather_json).rainSoon, true);
    const ranks = db.prepare('SELECT access_point_id AS ap, rank FROM candidate ORDER BY rank').all() as { ap: number }[];
    assert.deepEqual(ranks.map((r) => r.ap), [2, 1]);
  } finally {
    await app.close(); db.close(); rmSync(directory, { recursive: true, force: true });
  }
});
