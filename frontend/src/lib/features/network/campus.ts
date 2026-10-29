/**
 * Map configuration for the ČVUT Dejvice campus (Prague 6). Base map: OpenFreeMap vector tiles
 * (OpenMapTiles schema, © OpenStreetMap contributors) — attribution must stay visible.
 */
export type { LngLat } from './routes.geo';
import type { LngLat } from './routes.geo';

export const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

/** Area the initial camera fits: the campus between Kolejní and Vítězné náměstí. */
export const BOUNDS: [LngLat, LngLat] = [
  [14.3848, 50.0974],
  [14.4048, 50.1068]
];

/** The camera may not leave Dejvice. */
export const MAX_BOUNDS: [LngLat, LngLat] = [
  [14.372, 50.091],
  [14.418, 50.113]
];
