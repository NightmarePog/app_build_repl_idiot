import type { LngLat } from './routes.geo';

/** A coloured path drawn over the network: a line direction, a trip or one planner leg. */
export type MapPath = {
  coords: LngLat[];
  color: string;
  /** 1 = the passenger's part, lower = context (the rest of the trip). */
  opacity?: number;
};

const dist = (a: LngLat, b: LngLat) => Math.hypot(a[0] - b[0], (a[1] - b[1]) * 1.55);

function nearest(coords: LngLat[], point: LngLat, from = 0) {
  let best = from;
  let score = Infinity;
  for (let i = from; i < coords.length; i++) {
    const d = dist(coords[i], point);
    if (d < score) {
      score = d;
      best = i;
    }
  }
  return best;
}

/**
 * The street geometry through the stops, in travel order. The line's street path is walked in
 * the direction of travel (backwards for the inbound direction) and each stop is matched only
 * ahead of the previous one, so a path that passes a spot twice is not cut short. A stop the
 * path does not reach is joined straight.
 */
export function pathThrough(street: LngLat[], stops: LngLat[]): LngLat[] {
  if (stops.length < 2 || street.length < 2) return [...stops];
  const forward = nearest(street, stops[0]) <= nearest(street, stops.at(-1)!);
  const walk = forward ? street : [...street].reverse();
  const out: LngLat[] = [stops[0]];
  let at = nearest(walk, stops[0]);
  for (let i = 1; i < stops.length; i++) {
    const next = nearest(walk, stops[i], at);
    out.push(...walk.slice(at + 1, next), stops[i]);
    at = next;
  }
  return out;
}

/** The point `share` (0..1) of the way along a path, by length. */
export function pointAlong(path: LngLat[], share: number): LngLat | null {
  if (path.length === 0) return null;
  if (path.length === 1) return path[0];
  const lengths = path.slice(1).map((p, i) => dist(path[i], p));
  let left = Math.min(1, Math.max(0, share)) * lengths.reduce((s, l) => s + l, 0);
  for (let i = 0; i < lengths.length; i++) {
    if (left <= lengths[i] || i === lengths.length - 1) {
      const t = lengths[i] ? Math.min(1, left / lengths[i]) : 0;
      return [
        path[i][0] + (path[i + 1][0] - path[i][0]) * t,
        path[i][1] + (path[i + 1][1] - path[i][1]) * t
      ];
    }
    left -= lengths[i];
  }
  return path.at(-1)!;
}

/** South-west and north-east corners around the points, or null for none. */
export function boundsOf(points: LngLat[]): [LngLat, LngLat] | null {
  if (!points.length) return null;
  const lng = points.map((p) => p[0]);
  const lat = points.map((p) => p[1]);
  return [
    [Math.min(...lng), Math.min(...lat)],
    [Math.max(...lng), Math.max(...lat)]
  ];
}
