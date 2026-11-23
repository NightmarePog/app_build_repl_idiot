import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  boarding,
  nextTrip,
  ribbon,
  timedStops,
  tripPosition
} from '../src/lib/features/timetable/trip-position';

const call = (id: number, arr: string, dep: string, arrDay = 0, depDay = arrDay) => ({
  stop: { id },
  arrival_time: arr,
  arrival_day_offset: arrDay,
  departure_time: dep,
  departure_day_offset: depDay
});

// The 23:52 C trip: crosses midnight between the second and third call.
const night = [
  call(1, '23:52', '23:52'),
  call(2, '23:58', '23:59'),
  call(3, '00:00', '00:00', 1),
  call(4, '00:09', '00:09', 1)
];

test('timedStops counts minutes across midnight and marks the crossing', () => {
  const stops = timedStops(night);
  assert.deepEqual(
    stops.map((s) => [s.arr, s.dep, s.dwell, s.gap, s.crossesMidnight]),
    [
      [1432, 1432, 0, 0, false],
      [1438, 1439, 1, 6, false],
      [1440, 1440, 0, 1, true],
      [1449, 1449, 0, 9, false]
    ]
  );
});

test('ribbon puts the night band where midnight falls', () => {
  const stops = timedStops(night);
  const r = ribbon(stops[0], stops[3]);
  assert.equal(r.total, 17);
  assert.equal(r.night, (8 / 17) * 100);
  const day = timedStops([call(1, '07:00', '07:00'), call(2, '07:18', '07:18')]);
  assert.equal(ribbon(day[0], day[1]).night, null);
  // Boarding after midnight: no band.
  assert.equal(ribbon(stops[2], stops[3]).night, null);
});

test('boarding falls back to the origin when ?od= is not on the trip', () => {
  const stops = timedStops(night);
  assert.deepEqual(boarding(stops, null), { index: 0, missing: false });
  assert.deepEqual(boarding(stops, 3), { index: 2, missing: false });
  assert.deepEqual(boarding(stops, 99), { index: 0, missing: true });
  assert.deepEqual(boarding([], 99), { index: 0, missing: false });
});

test('tripPosition finds the bus just after midnight on the previous day clock', () => {
  const stops = timedStops(night);
  assert.deepEqual(tripPosition(stops, 5), { kind: 'between', index: 2, share: 5 / 9 });
  assert.equal(tripPosition(stops, 600), null);
});

const trip = (id: number, time: string, route = 1, day = 0) => ({
  id,
  route_id: route,
  departure_time: time,
  departure_day_offset: day
});

test('nextTrip keeps to the route and wraps to tomorrow', () => {
  const trips = [trip(3, '20:00'), trip(1, '07:00'), trip(2, '07:00', 2), trip(4, '23:52')];
  assert.deepEqual(nextTrip(trips, trips[1]), { trip: trips[0], tomorrow: false });
  assert.deepEqual(nextTrip(trips, trips[3]), { trip: trips[1], tomorrow: true });
  assert.equal(nextTrip(trips, trips[2]), null);
  assert.equal(nextTrip([], trips[0]), null);
});
