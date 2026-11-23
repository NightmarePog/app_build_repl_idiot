import type { Connection, ConnectionLeg } from '$lib/api/generated';

export type Mode = 'departure' | 'arrival';

/** A search as it lives in the URL: /spojeni?z=2&do=11&cas=07:00&podle=prijezd. */
export type Query = { from: number | null; to: number | null; time: string | null; mode: Mode };

const TIME = /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/;

const idOf = (value: string | null) => {
  const n = Number(value);
  return value && Number.isSafeInteger(n) && n >= 1 ? n : null;
};

/** "7:05" -> "07:05"; anything that is not a 24 h time -> null. */
export function normalizeTime(value: string | null | undefined) {
  const text = value?.trim() ?? '';
  return TIME.test(text) ? text.padStart(5, '0') : null;
}

/** Reads the Czech keys (z, do, cas, podle) and the English aliases (from, to, time, mode). */
export function parseQuery(params: URLSearchParams): Query {
  const get = (cz: string, en: string) => params.get(cz) ?? params.get(en);
  return {
    from: idOf(get('z', 'from')),
    to: idOf(get('do', 'to')),
    time: normalizeTime(get('cas', 'time')),
    mode: ['prijezd', 'arrival'].includes(get('podle', 'mode') ?? '') ? 'arrival' : 'departure'
  };
}

/** Waits this long or longer are flagged in the results. */
export const LONG_WAIT = 60;

export type BarPart =
  | { kind: 'ride'; leg: ConnectionLeg; share: number }
  | { kind: 'wait'; minutes: number; long: boolean; share: number };

/** Longest wait drawn to scale; anything longer gets a break glyph instead of its full width. */
export const WAIT_CAP = 8;

/**
 * The journey bar on a compressed time scale: rides keep their real length, a wait is drawn at
 * most WAIT_CAP minutes wide so a 13 h night wait does not squeeze the rides into chips.
 */
export function journeyBar(connection: Connection): BarPart[] {
  const drawn = (minutes: number) => Math.min(minutes, WAIT_CAP);
  const total = Math.max(
    connection.legs.reduce(
      (sum, leg, index) =>
        sum + leg.duration_minutes + (index > 0 ? drawn(leg.transfer_minutes ?? 0) : 0),
      0
    ),
    1
  );
  const parts: BarPart[] = [];
  connection.legs.forEach((leg, index) => {
    if (index > 0) {
      const minutes = leg.transfer_minutes ?? 0;
      parts.push({
        kind: 'wait',
        minutes,
        long: minutes >= LONG_WAIT,
        share: drawn(minutes) / total
      });
    }
    parts.push({ kind: 'ride', leg, share: leg.duration_minutes / total });
  });
  return parts;
}

/** Longest wait of a connection, 0 for a direct one. */
export function longestWait(connection: Connection) {
  return Math.max(0, ...connection.legs.map((l) => l.transfer_minutes ?? 0));
}

/**
 * Duration that marks the one fastest result: only when exactly one connection has the
 * shortest time and the times actually differ, otherwise the badge would mean nothing.
 */
export function fastestDuration(connections: Connection[]) {
  if (connections.length < 2) return null;
  const minutes = connections.map((c) => c.duration_minutes);
  const min = Math.min(...minutes);
  return minutes.filter((m) => m === min).length === 1 ? min : null;
}

/** "Bus A01" for the vehicle working a trip, null when none is assigned. */
export const vehicleLabel = (blockRef: string | null | undefined) =>
  blockRef ? `Bus ${blockRef}` : null;

/**
 * A connection with its day offsets moved by `shift` days, so pages fetched for a later or
 * earlier time line up with the first search's day.
 */
export function shifted(connection: Connection, shift: number): Connection {
  if (shift === 0) return connection;
  const leg = (l: ConnectionLeg): ConnectionLeg => ({
    ...l,
    departure_day_offset: l.departure_day_offset + shift,
    arrival_day_offset: l.arrival_day_offset + shift,
    stops: l.stops.map((s) => ({
      ...s,
      arrival_day_offset: s.arrival_day_offset + shift,
      departure_day_offset: s.departure_day_offset + shift
    }))
  });
  return {
    ...connection,
    departure_day_offset: connection.departure_day_offset + shift,
    arrival_day_offset: connection.arrival_day_offset + shift,
    legs: connection.legs.map(leg)
  };
}

/** Stable id of a connection for the URL and keyed lists: trips, boarding and departure. */
export function connectionKey(connection: Connection) {
  return [
    connection.departure_day_offset,
    connection.departure_time.replace(':', ''),
    ...connection.legs.map((l) => `${l.trip_id}.${l.from.id}`)
  ].join('-');
}

/** Absolute minutes from midnight of the search day. */
export function absolute(time: string, dayOffset: number) {
  const [h, m] = time.split(':').map(Number);
  return dayOffset * 1440 + h * 60 + m;
}

/** "přímé spojení", "1 přestup", "2 přestupy", "5 přestupů". */
export function transfersLabel(count: number) {
  if (count === 0) return 'přímé spojení';
  if (count === 1) return '1 přestup';
  if (count < 5) return `${count} přestupy`;
  return `${count} přestupů`;
}

/** Day relative to the search: "" for the same day. */
export function dayNote(offset: number) {
  if (offset === 1) return 'zítra';
  if (offset === -1) return 'včera';
  if (offset > 1) return `+${offset} dny`;
  return '';
}

/** Czech diacritics-insensitive match key: "Kernel hub" finds "Kernel Hub", "nadrazi" "Nádraží". */
export function fold(text: string) {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
}
