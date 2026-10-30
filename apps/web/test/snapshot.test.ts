import assert from 'node:assert/strict';
import { test } from 'node:test';
import * as store from '../src/data/store';
import type { VisitSnapshot } from '@majung/shared';

const snapshot = (id: string, version: number) => ({ visit: { id, version } }) as VisitSnapshot;
test('a late HTTP response cannot roll back a newer websocket state or replace another visit', () => {
  assert.equal(typeof store.chooseSnapshot, 'function', 'server snapshot reconciliation must exist');
  const current = snapshot('one', 8);
  assert.equal(store.chooseSnapshot(current, snapshot('one', 7)), current);
  assert.equal(store.chooseSnapshot(current, snapshot('other', 9)), current);
  const newer = snapshot('one', 9);
  assert.equal(store.chooseSnapshot(current, newer), newer);
  assert.equal(store.chooseSnapshot(null, newer), newer);
});
