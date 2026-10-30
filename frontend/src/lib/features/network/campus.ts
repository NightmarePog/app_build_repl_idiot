/**
 * Map configuration for the ČVUT Dejvice campus (Prague 6). Base map: OpenFreeMap vector tiles
 * (OpenMapTiles schema, © OpenStreetMap contributors) — attribution must stay visible.
 */
export type { LngLat } from './routes.geo';
import type { LngLat } from './routes.geo';

export const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

/** Area the initial camera fits: the seed frame (see routes.geo.ts) plus Campus Gate. */
export const BOUNDS: [LngLat, LngLat] = [
  [14.385, 50.0965],
  [14.4045, 50.107]
];

/** The camera may not leave Dejvice. */
export const MAX_BOUNDS: [LngLat, LngLat] = [
  [14.372, 50.089],
  [14.418, 50.114]
];
