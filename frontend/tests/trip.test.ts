import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  boarding,
  nextTrip,
  ribbon,
  rideProgress,
  segment,
  timedStops,
  tripPosition,
  tripStatus,
  spokenStop,
  waitLabel
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

const named = (arr: number, dep: number, name: string) => ({ arr, dep, stop: { name } });
const run = [named(420, 420, 'Turing'), named(424, 425, 'Ada'), named(438, 438, 'Stack')];

test('tripStatus says what the trip does now in one sentence', () => {
  assert.deepEqual(tripStatus(run, 0, '07:00', 300), {
    tone: 'waiting',
    title: 'Odjíždí v 07:00',
    detail: 'za 2 h'
  });
  // An evening trip seen after midnight is still running, not 'not left yet'.
  const night = [named(1432, 1432, 'Turing'), named(1444, 1445, 'Ada'), named(1454, 1454, 'Stack')];
  assert.equal(tripStatus(night, 0, '23:52', 2)?.tone, 'live');
  assert.equal(tripStatus(night, 0, '23:52', 100)?.title, 'Odjíždí ve 23:52');
  assert.equal(tripStatus(run, 0, '07:00', 600)?.title, 'Odjíždí zítra v 07:00');
  assert.equal(tripStatus(run, 0, '07:00', 410)?.title, 'Odjíždí za 10 min');
  assert.equal(tripStatus(run, 0, '07:00', 424)?.title, 'Stojí v zastávce Ada');
  assert.equal(tripStatus(run, 0, '07:00', 430)?.title, 'Jede z Ada do Stack');
  assert.equal(tripStatus(run, 0, '07:00', 600)?.tone, 'done');
  assert.equal(tripStatus([], 0, '07:00', 600), null);
});

test('rideProgress is null off the road and runs 0..1 from boarding', () => {
  assert.equal(rideProgress(run[0], run[2], 300), null);
  assert.equal(rideProgress(run[0], run[2], 420), 0);
  assert.equal(rideProgress(run[0], run[2], 438), 1);
  // Just after midnight, on the previous operating day's 24:xx clock.
  assert.equal(rideProgress({ dep: 1432 }, { arr: 1454 }, 3), 0.5);
});

test('spokenStop reads each row of the timetable', () => {
  const s = (arr: string, dep: string, dwell: number, offset = 0) => ({
    arrival_time: arr,
    arrival_day_offset: offset,
    departure_time: dep,
    departure_day_offset: offset,
    dwell
  });
  assert.equal(spokenStop(s('07:00', '07:00', 0), true, false), 'odjezd 07:00');
  assert.equal(spokenStop(s('07:20', '07:20', 0), false, true), 'příjezd 07:20');
  assert.equal(spokenStop(s('07:04', '07:04', 0), false, false), 'příjezd a odjezd 07:04');
  assert.equal(
    spokenStop(s('00:04', '00:05', 1, 1), false, false),
    'příjezd 00:04, následující den, odjezd 00:05, následující den, stojí 1 min'
  );
  assert.equal(waitLabel(327), 'za 5 h 27 min');
  assert.equal(waitLabel(40), 'za 40 min');
});

test('segment runs from ?od= to ?do=, falling back to the ends', () => {
  const stops = [1, 2, 3, 4].map((id) => ({ stop: { id } }));
  assert.deepEqual(segment(stops, null, null), { from: 0, to: 3, missing: false });
  assert.deepEqual(segment(stops, 2, 3), { from: 1, to: 2, missing: false });
  assert.deepEqual(segment(stops, 3, 1), { from: 2, to: 3, missing: false });
  assert.deepEqual(segment(stops, 99, null), { from: 0, to: 3, missing: true });
});
