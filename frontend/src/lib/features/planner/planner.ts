import { resolve } from '$app/paths';
import type { ResolvedPathname } from '$app/types';
import type { Connection, ConnectionLeg } from '$lib/api/generated';
import { withQuery } from '$lib/links';

export type Mode = 'departure' | 'arrival';

/** A search as it lives in the URL: /spojeni?z=2&do=11&cas=07:00&podle=prijezd. */
export type Query = { from: number | null; to: number | null; time: string | null; mode: Mode };

const TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;

const idOf = (value: string | null) => {
  const n = Number(value);
  return value && Number.isSafeInteger(n) && n >= 1 ? n : null;
};

export function parseQuery(params: URLSearchParams): Query {
  const time = params.get('cas');
  return {
    from: idOf(params.get('z')),
    to: idOf(params.get('do')),
    time: time && TIME.test(time) ? time : null,
    mode: ['prijezd', 'arrival'].includes(params.get('podle') ?? '') ? 'arrival' : 'departure'
  };
}

/** The planner URL; a null time means "now". */
export function plannerHref(query: Partial<Query>, detail?: string | null): ResolvedPathname {
  return withQuery(resolve('/spojeni'), {
    z: query.from,
    do: query.to,
    cas: query.time,
    podle: query.mode === 'arrival' ? 'prijezd' : null,
    detail
  });
}

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
  if (offset === 1) return 'následující den';
  if (offset === -1) return 'předchozí den';
  if (offset > 1) return `+${offset} dny`;
  return '';
}

/** Czech diacritics-insensitive match key: "Kernel hub" finds "Kernel Hub", "nadrazi" "Nádraží". */
export function fold(text: string) {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
}
