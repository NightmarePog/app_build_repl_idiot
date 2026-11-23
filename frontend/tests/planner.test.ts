import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { Connection, ConnectionLeg } from '../src/lib/api/generated';
import {
  absolute,
  connectionKey,
  dayNote,
  fold,
  journeyBar,
  normalizeTime,
  parseQuery,
  shifted,
  transfersLabel
} from '../src/lib/features/planner/planner-core';

const stop = (id: number) => ({ id, name: `S${id}` });

const leg = (trip: number, from: number, dep: string, duration: number, wait: number | null) =>
  ({
    trip_id: trip,
    from: stop(from),
    to: stop(from + 1),
    destination: stop(9),
    departure_time: dep,
    departure_day_offset: 0,
    arrival_time: dep,
    arrival_day_offset: 0,
    duration_minutes: duration,
    transfer_minutes: wait,
    direction: 'outbound',
    line: { id: 1, number: 'A', color: '#2f7d4f' },
    stops: [
      {
        stop: stop(from),
        arrival_time: dep,
        arrival_day_offset: 0,
        departure_time: dep,
        departure_day_offset: 0
      }
    ]
  }) as unknown as ConnectionLeg;

const connection = (legs: ConnectionLeg[], duration: number) =>
  ({
    departure_time: legs[0].departure_time,
    departure_day_offset: 0,
    arrival_time: '08:00',
    arrival_day_offset: 0,
    duration_minutes: duration,
    transfers: legs.length - 1,
    legs
  }) as unknown as Connection;

test('parseQuery reads Czech keys and English aliases', () => {
  assert.deepEqual(parseQuery(new URLSearchParams('z=1&do=9&cas=07:00&podle=prijezd')), {
    from: 1,
    to: 9,
    time: '07:00',
    mode: 'arrival'
  });
  assert.deepEqual(parseQuery(new URLSearchParams('from=1&to=8&time=7:05')), {
    from: 1,
    to: 8,
    time: '07:05',
    mode: 'departure'
  });
  assert.deepEqual(parseQuery(new URLSearchParams('z=0&do=abc&cas=25:00')), {
    from: null,
    to: null,
    time: null,
    mode: 'departure'
  });
});

test('normalizeTime accepts only 24 h times', () => {
  assert.equal(normalizeTime('9:30'), '09:30');
  assert.equal(normalizeTime(' 23:59 '), '23:59');
  assert.equal(normalizeTime('24:00'), null);
  assert.equal(normalizeTime('7:5'), null);
  assert.equal(normalizeTime(''), null);
});

test('labels', () => {
  assert.equal(transfersLabel(0), 'přímé spojení');
  assert.equal(transfersLabel(1), '1 přestup');
  assert.equal(transfersLabel(3), '3 přestupy');
  assert.equal(transfersLabel(5), '5 přestupů');
  assert.equal(dayNote(0), '');
  assert.equal(dayNote(1), 'zítra');
  assert.equal(dayNote(-1), 'včera');
  assert.equal(fold(' Nádraží '), 'nadrazi');
});

test('absolute, shifted and connectionKey', () => {
  assert.equal(absolute('00:10', 1), 1450);
  const c = connection([leg(1, 1, '07:00', 10, null), leg(2, 3, '07:20', 10, 10)], 30);
  const moved = shifted(c, 1);
  assert.equal(moved.departure_day_offset, 1);
  assert.equal(moved.legs[1].stops[0].arrival_day_offset, 1);
  assert.equal(shifted(c, 0), c);
  assert.equal(connectionKey(c), '0-0700-1.1-2.3');
});

test('journeyBar sizes rides and waits and flags long waits', () => {
  const c = connection([leg(1, 1, '07:00', 20, null), leg(2, 3, '08:30', 20, 70)], 110);
  const parts = journeyBar(c);
  assert.deepEqual(
    parts.map((p) => p.kind),
    ['ride', 'wait', 'ride']
  );
  const wait = parts[1];
  assert.ok(wait.kind === 'wait' && wait.long && wait.minutes === 70);
  assert.ok(Math.abs(parts.reduce((sum, p) => sum + p.share, 0) - 1) < 1e-9);
});
