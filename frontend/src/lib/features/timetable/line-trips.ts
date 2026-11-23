import { minutesOf } from './time';

/**
 * Pure line-timetable logic, kept out of LineTrips.svelte so the midnight edge cases are unit
 * tested. All minutes count from midnight of the operating day (24:14 is 1454).
 */
export type Departure = { dep: number };

/**
 * Now on the operating-day clock. Shortly after midnight, while last evening's 24:xx trips are
 * still to come, 00:05 is 24:05 of yesterday's service day, so the evening stays the evening.
 */
export function operatingNow(rows: Departure[], now: number) {
  return rows.some((r) => r.dep >= now + 1440) ? now + 1440 : now;
}

/** The first departure still ahead (rows sorted by dep), else the first one tomorrow. */
export function nextDeparture<T extends Departure>(rows: T[], nowOp: number) {
  const ahead = rows.find((r) => r.dep >= nowOp);
  if (ahead) return { row: ahead, until: ahead.dep - nowOp, tomorrow: false };
  const first = rows.at(0);
  return first ? { row: first, until: first.dep + 1440 - nowOp, tomorrow: true } : null;
}

const hh = (hour: number) => String(hour % 24).padStart(2, '0');

export type HourGroup<T> =
  | { kind: 'hour'; hour: number; label: string; trips: T[] }
  | { kind: 'gap'; from: number; to: number; label: string };

/**
 * One row per hour with departures; a run of two or more empty hours collapses into one gap row
 * ("08–13"), so the few real times are not lost in a column of dashes.
 */
export function hourGroups<T extends Departure>(rows: T[]): HourGroup<T>[] {
  const first = rows.at(0);
  const last = rows.at(-1);
  if (!first || !last) return [];
  const from = Math.floor(first.dep / 60);
  const to = Math.floor(last.dep / 60);
  const groups: HourGroup<T>[] = [];
  let emptyFrom: number | null = null;
  const flush = (until: number) => {
    if (emptyFrom === null) return;
    if (until === emptyFrom) {
      groups.push({ kind: 'hour', hour: emptyFrom, label: hh(emptyFrom), trips: [] });
    } else {
      groups.push({
        kind: 'gap',
        from: emptyFrom,
        to: until,
        label: `${hh(emptyFrom)}–${hh(until)}`
      });
    }
    emptyFrom = null;
  };
  for (let hour = from; hour <= to; hour++) {
    const trips = rows.filter((r) => Math.floor(r.dep / 60) === hour);
    if (trips.length === 0) {
      emptyFrom ??= hour;
      continue;
    }
    flush(hour - 1);
    groups.push({ kind: 'hour', hour, label: hh(hour), trips });
  }
  return groups;
}

/** Day ribbon span in hours: 0–24, stretched to cover departures after midnight. */
export function ribbonHours(rows: Departure[]) {
  const last = rows.at(-1);
  return last ? Math.max(24, Math.ceil((last.dep + 1) / 60)) : 24;
}

/** Median travel time of a list of minute durations, or null for none. */
export function median(values: number[]) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)];
}

type TimedStop = {
  stop: { id: number };
  arrival_time: string;
  arrival_day_offset: number;
  departure_time: string;
  departure_day_offset: number;
};

/** End-to-end minutes of one trip, from the first departure to the last arrival. */
export function travelMinutes(stops: TimedStop[]) {
  const first = stops.at(0);
  const last = stops.at(-1);
  if (!first || !last || first === last) return null;
  return (
    minutesOf(last.arrival_time, last.arrival_day_offset) -
    minutesOf(first.departure_time, first.departure_day_offset)
  );
}

/**
 * Minutes from each stop to the next (keyed by the stop it leaves) and the wait at each stop
 * (keyed by stop), so the line diagram can print "4 min" between stops in this direction.
 */
export function hops(stops: TimedStop[]) {
  const travel = new Map<number, number>();
  const dwell = new Map<number, number>();
  stops.forEach((stop, index) => {
    const dep = minutesOf(stop.departure_time, stop.departure_day_offset);
    dwell.set(stop.stop.id, dep - minutesOf(stop.arrival_time, stop.arrival_day_offset));
    const next = stops[index + 1];
    if (next) travel.set(stop.stop.id, minutesOf(next.arrival_time, next.arrival_day_offset) - dep);
  });
  return { travel, dwell };
}
