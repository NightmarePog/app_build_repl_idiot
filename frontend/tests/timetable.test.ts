import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  clock,
  countdown,
  dayLabel,
  duration,
  minutesOf,
  minutesUntil,
  nowMinutes,
  relative
} from '../src/lib/features/timetable/time';
import { overtakes } from '../src/lib/features/timetable/board';
import {
  hops,
  hourGroups,
  median,
  nextDeparture,
  operatingNow,
  ribbonHours,
  travelMinutes
} from '../src/lib/features/timetable/line-trips';
import { tripPosition } from '../src/lib/features/timetable/trip-position';

test('minutesOf counts day offsets', () => {
  assert.equal(minutesOf('00:14', 1), 1454);
  assert.equal(minutesOf('07:05'), 425);
});

test('clock wraps around midnight both ways', () => {
  assert.equal(clock(1454), '00:14');
  assert.equal(clock(-1), '23:59');
  assert.equal(clock(1440), '00:00');
});

test('duration formats minutes and hours', () => {
  assert.equal(duration(21), '21 min');
  assert.equal(duration(65), '1 h 05 min');
});

test('nowMinutes reads Prague time regardless of the host zone', () => {
  // 2026-11-23 22:30 UTC is 23:30 in Prague (CET).
  assert.equal(nowMinutes(new Date('2026-11-23T22:30:00Z')), 23 * 60 + 30);
  // Summer time: 2026-07-01 22:30 UTC is 00:30 in Prague.
  assert.equal(nowMinutes(new Date('2026-07-01T22:30:00Z')), 30);
});

test('minutesUntil keeps last night trip visible just after midnight', () => {
  assert.equal(minutesUntil(1449, 5), 4);
  assert.equal(minutesUntil(1449, 1430), 19);
  assert.equal(minutesUntil(600, 5), 595);
  assert.equal(minutesUntil(300, 600), null);
});

test('countdown only within the hour', () => {
  assert.equal(countdown(0), 'odjíždí');
  assert.equal(countdown(1), 'odjíždí');
  assert.equal(countdown(12), 'za 12 min');
  assert.equal(countdown(61), null);
  assert.equal(countdown(null), null);
});

test('dayLabel spells out the next day', () => {
  assert.equal(dayLabel(0), '');
  assert.equal(dayLabel(1), 'po půlnoci');
});

test('overtakes names the trip that arrives later but leaves first', () => {
  const rows = [
    { key: 'a', arr: 1213, dep: 1215, label: 'B → Campus Gate 20:15' },
    { key: 'b', arr: 1214, dep: 1214, label: 'B → Hopper Hall 20:14' }
  ];
  const result = overtakes(rows);
  assert.deepEqual(result.get('a'), ['B → Hopper Hall 20:14']);
  assert.deepEqual(result.get('b') ?? [], []);
});

const deps = (...minutes: number[]) => minutes.map((dep) => ({ dep }));

test('operatingNow keeps last evening after midnight only while 24:xx trips remain', () => {
  // 00:05 with a 24:09 trip still to come: still the evening, 24:05.
  assert.equal(operatingNow(deps(420, 1449), 5), 1445);
  // 00:05 with nothing after midnight: a new day.
  assert.equal(operatingNow(deps(420, 1320), 5), 5);
  // 23:59 is plain 23:59.
  assert.equal(operatingNow(deps(420, 1449), 1439), 1439);
});

test('nextDeparture rolls over to tomorrow after the last trip', () => {
  const rows = deps(420, 1200, 1432);
  assert.deepEqual(nextDeparture(rows, 1000), { row: rows[1], until: 200, tomorrow: false });
  // 24:00 on the operating clock: everything left, the first one is tomorrow at 07:00.
  assert.deepEqual(nextDeparture(rows, 1440), { row: rows[0], until: 420, tomorrow: true });
  assert.equal(nextDeparture([], 600), null);
});

test('nextDeparture counts a 24:xx trip from just before midnight', () => {
  const rows = deps(1432, 1449);
  const now = operatingNow(rows, 1439);
  assert.equal(nextDeparture(rows, now)?.until, 10);
});

test('hourGroups collapses runs of empty hours and keeps single gaps as hours', () => {
  const groups = hourGroups(deps(420, 425, 540, 1200, 1449));
  assert.deepEqual(
    groups.map((g) => (g.kind === 'gap' ? `gap ${g.label}` : `${g.label}:${g.trips.length}`)),
    ['07:2', '08:0', '09:1', 'gap 10–19', '20:1', 'gap 21–23', '00:1']
  );
  assert.deepEqual(hourGroups([]), []);
});

test('ribbonHours stretches past 24 for trips after midnight', () => {
  assert.equal(ribbonHours(deps(420, 1320)), 24);
  assert.equal(ribbonHours(deps(420, 1449)), 25);
});

test('median and travel minutes across midnight', () => {
  assert.equal(median([21, 20, 22]), 21);
  assert.equal(median([]), null);
  const stops = [
    {
      stop: { id: 1 },
      arrival_time: '23:52',
      arrival_day_offset: 0,
      departure_time: '23:52',
      departure_day_offset: 0
    },
    {
      stop: { id: 2 },
      arrival_time: '23:59',
      arrival_day_offset: 0,
      departure_time: '00:00',
      departure_day_offset: 1
    },
    {
      stop: { id: 3 },
      arrival_time: '00:14',
      arrival_day_offset: 1,
      departure_time: '00:14',
      departure_day_offset: 1
    }
  ];
  assert.equal(travelMinutes(stops), 22);
  const { travel, dwell } = hops(stops);
  assert.equal(travel.get(1), 7);
  assert.equal(travel.get(2), 14);
  assert.equal(dwell.get(2), 1);
  assert.equal(dwell.get(3), 0);
});

test('tripPosition places the bus at a stop, between stops and past midnight', () => {
  const calls = [
    { arr: 1432, dep: 1432 },
    { arr: 1439, dep: 1440 },
    { arr: 1454, dep: 1454 }
  ];
  assert.deepEqual(tripPosition(calls, 1435), { kind: 'between', index: 0, share: 3 / 7 });
  assert.deepEqual(tripPosition(calls, 1439), { kind: 'at', index: 1, share: 0 });
  // 00:07 the next calendar day is 24:07 of the operating day: half way to the terminus.
  assert.deepEqual(tripPosition(calls, 7), { kind: 'between', index: 1, share: 0.5 });
  assert.deepEqual(tripPosition(calls, 14), { kind: 'at', index: 2, share: 0 });
  assert.equal(tripPosition(calls, 15), null);
  assert.equal(tripPosition(calls, 1000), null);
});

test('relative spells long waits in hours', () => {
  assert.equal(relative(1), 'odjíždí');
  assert.equal(relative(3), 'za 3 min');
  assert.equal(relative(155), 'za 2 h 35 min');
  assert.equal(relative(null), null);
});
