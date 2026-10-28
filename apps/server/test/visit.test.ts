import { test } from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';

test('방문을 만들고 같은 버전으로 다시 조회한다', async () => {
  const db = openDb(':memory:');
  db.exec(`INSERT INTO facility VALUES ('f1', '테스트몰', '서울', '현장 확인', '2026-10-28');
           INSERT INTO access_point (id, facility_id, kind, name, vehicle_lat, vehicle_lng)
           VALUES ('ap-west', 'f1', 'PICKUP', '서문 로비', 37.5, 127.0);`);
  const app = await buildApp(db);

  const created = await app.inject({
    method: 'POST', url: '/api/visits',
    payload: { requestId: 'r1', facilityId: 'f1', mode: 'PICKUP', accessPointId: 'ap-west' },
  });
  assert.equal(created.statusCode, 201);
  const visit = created.json();
  assert.equal(visit.version, 1);

  const fetched = await app.inject({ method: 'GET', url: `/api/visits/${visit.id}` });
  assert.deepEqual(fetched.json(), visit);
  await app.close();
});
