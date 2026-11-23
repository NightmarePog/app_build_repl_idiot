import { countdown, minutesUntil, spokenTime } from './time';

/**
 * Where a trip is at a given minute, from its stop times in operating-day minutes. Kept pure so
 * the midnight cases are unit tested: at 00:05 a trip that left at 23:52 is at minute 1445.
 */
export type TimedCall = { arr: number; dep: number };

export type Position =
  /** Standing at stop `index` (between its arrival and departure). */
  | { kind: 'at'; index: number; share: number }
  /** Between stop `index` and the next one; `share` 0..1 of the hop done. */
  | { kind: 'between'; index: number; share: number };

export function tripPosition(stops: TimedCall[], now: number): Position | null {
  const first = stops.at(0);
  const last = stops.at(-1);
  if (!first || !last) return null;
  // Just after midnight "now" may be on the previous operating day's 24:xx clock.
  const t = [now, now + 1440].find((m) => m >= first.dep && m <= last.arr);
  if (t === undefined) return null;
  for (let i = 0; i < stops.length; i++) {
    const stop = stops[i];
    if (t >= stop.arr && t <= stop.dep) {
      const wait = stop.dep - stop.arr;
      return { kind: 'at', index: i, share: wait > 0 ? (t - stop.arr) / wait : 0 };
    }
    const next = stops[i + 1];
    if (next && t > stop.dep && t < next.arr) {
      return { kind: 'between', index: i, share: (t - stop.dep) / (next.arr - stop.dep) };
    }
  }
  return null;
}

type Call = {
  arrival_time: string;
  arrival_day_offset: number;
  departure_time: string;
  departure_day_offset: number;
  stop: { id: number };
};
type Departs = {
  id: number;
  route_id: number;
  departure_time: string;
  departure_day_offset: number;
};

const minutesOf = (time: string, dayOffset: number) => {
  const [hours, minutes] = time.split(':').map(Number);
  return dayOffset * 1440 + hours * 60 + minutes;
};

/** Each call with operating-day minutes, its dwell, the gap from the previous call and midnight. */
export function timedStops<T extends Call>(timetable: T[]) {
  let previous: number | null = null;
  return timetable.map((stop, index) => {
    const arr = minutesOf(stop.arrival_time, stop.arrival_day_offset);
    const dep = minutesOf(stop.departure_time, stop.departure_day_offset);
    const gap = previous === null ? 0 : arr - previous;
    const crossesMidnight =
      index > 0 && stop.arrival_day_offset > timetable[index - 1].departure_day_offset;
    previous = dep;
    return { ...stop, arr, dep, dwell: dep - arr, gap, crossesMidnight };
  });
}

/**
 * Where the passenger boards: the ?od= stop, or the origin when there is none. `missing` is true
 * when ?od= names a stop this trip does not call at; the page then starts from the origin.
 */
export function boarding(stops: { stop: { id: number } }[], fromId: number | null) {
  const found = fromId === null ? 0 : stops.findIndex((s) => s.stop.id === fromId);
  return { index: Math.max(0, found), missing: fromId !== null && stops.length > 0 && found < 0 };
}

/**
 * The passenger's part of a trip: boarding at ?od= (origin when absent or not served), getting
 * off at ?do= (terminus when absent, not served or not after boarding). Indexes into `stops`.
 */
export function segment(
  stops: { stop: { id: number } }[],
  fromId: number | null,
  toId: number | null
) {
  const from = boarding(stops, fromId);
  const found =
    toId === null ? -1 : stops.findIndex((s, i) => i > from.index && s.stop.id === toId);
  const to = found > from.index ? found : Math.max(from.index, stops.length - 1);
  return { from: from.index, to, missing: from.missing };
}

/** Ride length from boarding to the end, and where midnight falls on it in percent (or null). */
export function ribbon(
  board: { dep: number; departure_day_offset: number },
  last: { arr: number }
) {
  const total = Math.max(1, last.arr - board.dep);
  const midnight = 1440 * (board.departure_day_offset + 1);
  const crosses = last.arr >= midnight && board.dep < midnight;
  return { total, night: crosses ? ((midnight - board.dep) / total) * 100 : null };
}

/** The next trip on the same route; after the day's last one it wraps to tomorrow's first. */
export function nextTrip<T extends Departs>(trips: T[], trip: Departs) {
  const same = trips
    .filter((t) => t.route_id === trip.route_id)
    .sort(
      (a, b) =>
        minutesOf(a.departure_time, a.departure_day_offset) -
        minutesOf(b.departure_time, b.departure_day_offset)
    );
  const index = same.findIndex((t) => t.id === trip.id);
  if (index < 0) return null;
  const next = same[index + 1] ?? same[0];
  if (next.id === trip.id) return null;
  return { trip: next, tomorrow: index + 1 >= same.length };
}

export type TripStatus = {
  /** live: under way, soon: leaves within the hour, waiting: later today, done: ran today. */
  tone: 'live' | 'soon' | 'waiting' | 'done';
  /** One sentence for the status card, the only place the page talks about the day. */
  title: string;
  /** Relative countdown under the title ('za 5 h 27 min'), when the trip is still to come. */
  detail?: string;
};

/** Czech 'v'/'ve' before a clock time: 've' before hours said with a consonant cluster. */
export function atTime(time: string) {
  const hour = Number(time.slice(0, 2));
  return `${[2, 3, 4, 12, 13, 14, 20, 21, 22, 23].includes(hour) ? 've' : 'v'} ${time}`;
}

/** 'za 5 h 27 min' / 'za 40 min' for a wait in minutes. */
export function waitLabel(minutes: number) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `za ${h ? `${h} h ` : ''}${m || !h ? `${m} min` : ''}`.trim();
}

type Spoken = {
  arrival_time: string;
  arrival_day_offset: number;
  departure_time: string;
  departure_day_offset: number;
  dwell: number;
};

/** What a screen reader says for one row of the trip timetable. */
export function spokenStop(stop: Spoken, first: boolean, end: boolean) {
  const arr = spokenTime(stop.arrival_time, stop.arrival_day_offset);
  const dep = spokenTime(stop.departure_time, stop.departure_day_offset);
  if (first) return `odjezd ${dep}`;
  if (end) return `příjezd ${arr}`;
  if (stop.dwell === 0) return `příjezd a odjezd ${dep}`;
  return `příjezd ${arr}, odjezd ${dep}, stojí ${stop.dwell} min`;
}

/**
 * What the trip is doing at `now`, in one sentence. The page title stays stable because the
 * schedule runs every day; only this status says today or tomorrow.
 */
export function tripStatus(
  stops: { arr: number; dep: number; stop: { name: string } }[],
  boardIndex: number,
  departureTime: string,
  now: number
): TripStatus | null {
  const board = stops[boardIndex];
  if (!board || stops.length < 2) return null;
  const vehicle = tripPosition(stops, now);
  if (vehicle?.kind === 'at')
    return { tone: 'live', title: `Stojí v zastávce ${stops[vehicle.index].stop.name}` };
  if (vehicle?.kind === 'between')
    return {
      tone: 'live',
      title: `Jede z ${stops[vehicle.index].stop.name} do ${stops[vehicle.index + 1].stop.name}`
    };
  const wait = minutesUntil(board.dep, now);
  const soon = countdown(wait);
  if (soon)
    return { tone: 'soon', title: soon === 'odjíždí' ? 'Právě odjíždí' : `Odjíždí ${soon}` };
  // Not on the road: the next run is later today, or (once today's has ended) tomorrow.
  const title = `Odjíždí ${board.dep > now ? '' : 'zítra '}${atTime(departureTime)}`;
  const detail = waitLabel(wait ?? (board.dep + 1440 - (now % 1440)) % 1440);
  return { tone: board.dep > now ? 'waiting' : 'done', title, detail };
}

/** Share 0..1 of the ride from boarding to the terminus done at `now`; null when not under way. */
export function rideProgress(board: { dep: number }, last: { arr: number }, now: number) {
  const t = [now, now + 1440].find((m) => m >= board.dep && m <= last.arr);
  return t === undefined ? null : (t - board.dep) / Math.max(1, last.arr - board.dep);
}
