import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { openDb } from '../src/db/index.ts';
import { buildApp } from '../src/app.ts';
import { normalizePhone, type SmsSender } from '../src/modules/notification/sms.ts';
import { MAX_INVITES_PER_VISIT } from '../src/modules/notification/invite.routes.ts';

const create = { requestId: 'create-invite', facilityId: '1', mode: 'PICKUP', accessPointId: '2', driverDeviceId: 'device-with-unguessable-random-identifier' };

test('phone numbers are normalized to Korean mobile digits only', () => {
  assert.equal(normalizePhone('010-1234-5678'), '01012345678');
  assert.equal(normalizePhone('+82 10 1234 5678'), '01012345678');
  assert.equal(normalizePhone('02-123-4567'), null);
  assert.equal(normalizePhone('010-12'), null);
});

// Only the driver may text the passenger link; retries must not double-send; ended visits and abuse are refused.
test('driver sends the passenger link by SMS once per requestId, with role, phone, limit and phase checks', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'majung-invite-'));
  const db = openDb(join(directory, 'majung.sqlite'));
  const outbox: { to: string; text: string }[] = [];
  const sms: SmsSender = { name: 'test', async send(to, text) { outbox.push({ to, text }); } };
  const app = await buildApp(db, async () => null, { sms, passengerWebUrl: 'http://192.168.0.12:5173/' });
  const post = (url: string, payload: object) => app.inject({ method: 'POST', url, payload });
  try {
    const session = (await post('/api/visits', create)).json();
    const invite = `/api/visits/${session.driverToken}/invites`;

    const ok = await post(invite, { requestId: 'invite-1', phone: '010-1234-5678' });
    assert.equal(ok.statusCode, 200, ok.body);
    assert.deepEqual(ok.json(), { sentTo: '010-****-5678' });
    assert.equal(outbox.length, 1);
    assert.equal(outbox[0].to, '01012345678');
    assert.ok(outbox[0].text.includes(`http://192.168.0.12:5173/v/${session.passengerToken}`));
    assert.ok(!outbox[0].text.includes(session.driverToken));

    assert.equal((await post(invite, { requestId: 'invite-1', phone: '010-1234-5678' })).statusCode, 200);
    assert.equal(outbox.length, 1, 'same requestId must not send twice');

    assert.equal((await post(`/api/visits/${session.passengerToken}/invites`, { requestId: 'p', phone: '010-1111-2222' })).statusCode, 403);
    assert.equal((await post(invite, { requestId: 'bad', phone: '02-123-4567' })).json().code, 'INVALID_PHONE');
    assert.equal((await post(invite, { requestId: 'extra', phone: '010-1111-2222', text: 'spam' })).statusCode, 400);

    for (let i = 2; i <= MAX_INVITES_PER_VISIT; i++) assert.equal((await post(invite, { requestId: `invite-${i}`, phone: '010-1111-2222' })).statusCode, 200);
    const limited = await post(invite, { requestId: 'over', phone: '010-1111-2222' });
    assert.equal(limited.statusCode, 429);
    assert.equal(outbox.length, MAX_INVITES_PER_VISIT);

    const visit = (await app.inject({ method: 'GET', url: `/api/visits/${session.driverToken}` })).json().visit;
    await app.inject({ method: 'PATCH', url: `/api/visits/${session.driverToken}`, payload: { requestId: 'cancel', version: visit.version, phase: 'CANCELLED' } });
    const second = (await post('/api/visits', { ...create, requestId: 'create-2' })).json();
    const v2 = (await app.inject({ method: 'GET', url: `/api/visits/${second.driverToken}` })).json().visit;
    await app.inject({ method: 'PATCH', url: `/api/visits/${second.driverToken}`, payload: { requestId: 'cancel-2', version: v2.version, phase: 'CANCELLED' } });
    assert.equal((await post(`/api/visits/${second.driverToken}/invites`, { requestId: 'late', phone: '010-1111-2222' })).json().code, 'VISIT_ENDED');
  } finally {
    await app.close(); db.close(); rmSync(directory, { recursive: true, force: true });
  }
});
