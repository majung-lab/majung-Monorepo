import assert from 'node:assert/strict';
import { test } from 'node:test';
import { distanceM, nearest } from '../src/data/geo.ts';

test('map snapping chooses the nearest supplied access point and rejects distant pins', () => {
  const west = { id: 'west', pos: [37.5455, 127.2214] as [number, number] };
  const east = { id: 'east', pos: [37.5455, 127.2266] as [number, number] };
  assert.ok(Math.abs(distanceM(west.pos, east.pos) - 458) < 10);
  assert.equal(nearest([west, east], east.pos).point, east);
  assert.equal(nearest([west, east], east.pos).snapped, true);
  assert.equal(nearest([west, east], [37.55, 127.24]).snapped, false);
});
