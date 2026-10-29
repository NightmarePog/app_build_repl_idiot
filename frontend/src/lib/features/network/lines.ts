import type { Stop } from '$lib/api/generated';
import { ROUTE_COORDS, STOP_COORDS, type LngLat } from './routes.geo';

/**
 * Line routing is not part of the stops API yet (it arrives with the "Linky" phase), so the
 * schematic and the route menus use the seed network, matched to API stops by name. Stops the
 * API returns that are not on a known line are still listed, just without a line.
 */
export type LineId = 'A' | 'B' | 'C';

export type Line = {
  color: string;
  ink: string;
  stops: string[];
  /** Side of the street (in line widths) so lines sharing a road run side by side. */
  offset: number;
};

export const LINES: Record<LineId, Line> = {
  A: {
    color: 'var(--line-a)',
    ink: 'var(--line-a-ink)',
    stops: ['Turing Terminal', 'Ada Exchange', 'Pixel Park', 'Byte Square', 'Stack Garden'],
    offset: -1
  },
  B: {
    color: 'var(--line-b)',
    ink: 'var(--line-b-ink)',
    stops: [
      'Hopper Hall',
      'Ada Exchange',
      'Compiler Court',
      'Kernel Hub',
      'Syntax Square',
      'Lambda Lane',
      'Campus Gate'
    ],
    offset: 0
  },
  C: {
    color: 'var(--line-c)',
    ink: 'var(--line-c-ink)',
    stops: [
      'Edison East',
      'Notebook Quay',
      'Kernel Hub',
      'Library Loop',
      'Byte Square',
      'Quantum Commons'
    ],
    offset: 1
  }
};

export const LINE_IDS = Object.keys(LINES) as LineId[];

/** Bus-stop positions as [lng, lat] at the kerb of real Dejvice streets. */
export const POSITIONS: Record<string, LngLat> = STOP_COORDS;

/** Route geometry as [lng, lat] following the streets (precomputed, see routes.geo.ts). */
export function routeCoordinates(id: LineId): LngLat[] {
  return ROUTE_COORDS[id];
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
  return [...new Set(result)];
}

/** Stops grouped by line in route order, plus any API stop not on a known line. */
export function routesFrom(stops: Stop[]) {
  const byName = new Map(stops.map((stop) => [stop.name, stop]));
  const routes = LINE_IDS.map((id) => ({
    id,
    ...LINES[id],
    stops: LINES[id].stops.map((name) => byName.get(name)).filter((stop): stop is Stop => !!stop)
  }));
  const known = new Set(LINE_IDS.flatMap((id) => LINES[id].stops));
  const other = stops.filter((stop) => !known.has(stop.name));
  return { routes, other };
}
