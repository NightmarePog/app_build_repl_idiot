import { browser } from '$app/environment';
import { getLine, getLines, type LineDetail, type Route, type Stop } from '$lib/api/generated';
import { inkFor } from '$lib/features/lines/line-style';
import { ROUTE_COORDS, STOP_COORDS, type LngLat } from './routes.geo';
import { pathThrough, pointAlong } from './map-geometry';

/**
 * The transport network as served by the lines API: every line with its routes, loaded once
 * and shared by the map, menus, stop pages and footer. Lines are keyed by their public number
 * (what passengers see on the badge and what `?line=` carries in URLs).
 */
export type LineId = string;

export type Line = {
  /** Database id, used for /lines/:id. */
  apiId: number;
  number: string;
  name: string;
  type: string;
  color: string;
  ink: string;
  routes: Route[];
  /** Outbound stop names in travel order; the map and menus draw the line this way. */
  stops: string[];
  /** Side of the street (in line widths) so lines sharing a road run side by side. */
  offset: number;
};

export const LINES: Record<LineId, Line> = $state({});
export const LINE_IDS: LineId[] = $state([]);

let status = $state<'idle' | 'loading' | 'ready' | 'failed'>('idle');
let pending: Promise<void> | null = null;

export function networkReady() {
  return status === 'ready';
}

export function networkFailed() {
  return status === 'failed';
}

function outbound(line: LineDetail) {
  return line.routes.find((route) => route.bound === 'outbound') ?? line.routes[0];
}

function apply(details: LineDetail[]) {
  for (const key of Object.keys(LINES)) delete LINES[key];
  const middle = (details.length - 1) / 2;
  details.forEach((line, index) => {
    LINES[line.number] = {
      apiId: line.id,
      number: line.number,
      name: line.name,
      type: line.type,
      color: line.color,
      ink: inkFor(line.color),
      routes: line.routes,
      stops: outbound(line)?.stops.map((stop) => stop.name) ?? [],
      offset: index - middle
    };
  });
  LINE_IDS.splice(0, LINE_IDS.length, ...details.map((line) => line.number));
}

/** Fetches the network once; later calls reuse the result or the request in flight. */
export function loadNetwork(force = false) {
  if (!browser) return Promise.resolve();
  if (pending) return pending;
  if (status === 'ready' && !force) return Promise.resolve();
  status = 'loading';
  const signal = AbortSignal.timeout(10_000);
  pending = (async () => {
    try {
      const { data } = await getLines({ throwOnError: true, signal });
      // The list carries no routes; there are only a handful of lines, so fetch details together.
      const details = await Promise.all(
        data.map(async (line) => {
          const { data: detail } = await getLine({
            path: { id: line.id },
            throwOnError: true,
            signal
          });
          return detail;
        })
      );
      apply(details);
      status = 'ready';
    } catch {
      status = 'failed';
    } finally {
      pending = null;
    }
  })();
  return pending;
}

export function lineByNumber(number: string | null | undefined) {
  return number ? (LINES[number] ?? null) : null;
}

export function lineById(id: number) {
  return Object.values(LINES).find((line) => line.apiId === id) ?? null;
}

/** Bus-stop positions as [lng, lat] at the kerb of real Dejvice streets. */
export const POSITIONS: Record<string, LngLat> = STOP_COORDS;

/**
 * Street geometry for the line. Lines that have a precomputed street path use it; any other
 * line (added later through the API) is drawn straight from stop to stop.
 */
export function routeCoordinates(id: LineId): LngLat[] {
  const precomputed = ROUTE_COORDS[id];
  if (precomputed) return precomputed;
  return (LINES[id]?.stops ?? []).map((name) => POSITIONS[name]).filter(Boolean);
}

export function linesOf(name: string): LineId[] {
  return LINE_IDS.filter((id) => LINES[id].stops.includes(name));
}

/** First line serving the stop, used for its colour; null when it is on no known line. */
export function primaryLine(name: string): LineId | null {
  return linesOf(name)[0] ?? null;
}

export function isTransfer(name: string) {
  return linesOf(name).length > 1;
}

export function describeStop(name: string) {
  const lines = linesOf(name);
  if (lines.length > 1) return 'Přestupní zastávka';
  if (lines.length === 1) return `Linka ${lines[0]}`;
  return 'Zastávka';
}

export function neighboursOf(name: string) {
  const result: string[] = [];
  for (const id of LINE_IDS) {
    const stops = LINES[id].stops;
    const index = stops.indexOf(name);
    if (index < 0) continue;
    if (stops[index - 1]) result.push(stops[index - 1]);
    if (stops[index + 1]) result.push(stops[index + 1]);
  }
  // eslint-disable-next-line svelte/prefer-svelte-reactivity -- local lookup, not state.
  return [...new Set(result)];
}

/** Stops grouped by line in route order, plus any API stop not on a known line. */
export function routesFrom(stops: Stop[]) {
  // eslint-disable-next-line svelte/prefer-svelte-reactivity -- local lookup, not state.
  const byName = new Map(stops.map((stop) => [stop.name, stop]));
  const routes = LINE_IDS.map((id) => ({
    ...LINES[id],
    id,
    stops: LINES[id].stops.map((name) => byName.get(name)).filter((stop): stop is Stop => !!stop)
  }));
  // eslint-disable-next-line svelte/prefer-svelte-reactivity -- local lookup, not state.
  const known = new Set(LINE_IDS.flatMap((id) => LINES[id].stops));
  const other = stops.filter((stop) => !known.has(stop.name));
  return { routes, other };
}

/** Street path of a line through the named stops, in their order (so direction arrows point right). */
export function linePath(id: LineId, names: string[]): LngLat[] {
  return pathThrough(routeCoordinates(id), names.map((name) => POSITIONS[name]).filter(Boolean));
}

/**
 * Where a bus is on the map: at stop `index` of `names`, or `share` of the way to the next one
 * (the shape `tripPosition` returns).
 */
export function busAt(
  id: LineId,
  names: string[],
  position: { kind: 'at' | 'between'; index: number; share: number } | null
): LngLat | null {
  if (!position) return null;
  const here = POSITIONS[names[position.index]];
  if (position.kind === 'at' || !names[position.index + 1]) return here ?? null;
  return pointAlong(linePath(id, names.slice(position.index, position.index + 2)), position.share);
}
