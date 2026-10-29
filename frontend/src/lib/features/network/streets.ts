import { LINE_IDS, LINES } from './lines';
import { ROUTE_COORDS, STOP_COORDS, type LngLat } from './routes.geo';

/**
 * The base map is a real neighbourhood, but the campus is fictional, so real street names are
 * hidden and the streets the buses use get campus names instead. One name per leg between two
 * stops; legs shared by several lines keep the first name they were given.
 */
type StreetFeature = {
  type: 'Feature';
  properties: { name: string };
  geometry: { type: 'LineString'; coordinates: LngLat[] };
};

/**
 * The base map is a real neighbourhood, but the campus is fictional, so real street names are
 * hidden and the streets the buses use get campus names instead. One name per leg between two
 * stops; legs shared by several lines keep the first name they were given.
 */

const NAMES: Record<string, string> = {
  'Turing Terminal|Ada Exchange': 'Turingova',
  'Ada Exchange|Pixel Park': 'Lovelaceové',
  'Pixel Park|Byte Square': 'Pixelová',
  'Byte Square|Stack Garden': 'Zásobníková',
  'Hopper Hall|Ada Exchange': 'Hopperové',
  'Ada Exchange|Compiler Court': 'Překladačová',
  'Compiler Court|Kernel Hub': 'Dijkstrova',
  'Kernel Hub|Syntax Square': 'Syntaktická',
  'Syntax Square|Lambda Lane': 'Lambdová',
  'Lambda Lane|Campus Gate': 'Kampusová',
  'Edison East|Notebook Quay': 'Edisonova',
  'Notebook Quay|Kernel Hub': 'Sešitové nábřeží',
  'Kernel Hub|Library Loop': 'Knihovní',
  'Library Loop|Byte Square': 'Knuthova',
  'Byte Square|Quantum Commons': 'Kvantová'
};

function nearestIndex(path: LngLat[], [x, y]: LngLat, from: number) {
  let best = from;
  let bestDistance = Infinity;
  for (let index = from; index < path.length; index++) {
    const distance = (path[index][0] - x) ** 2 + (path[index][1] - y) ** 2;
    if (distance < bestDistance) {
      best = index;
      bestDistance = distance;
    }
  }
  return best;
}

export function streetLabels(): { type: 'FeatureCollection'; features: StreetFeature[] } {
  const named = new Set<string>();
  const features: StreetFeature[] = [];
  for (const id of LINE_IDS) {
    const path = ROUTE_COORDS[id];
    const stops = LINES[id].stops.filter((name) => STOP_COORDS[name]);
    let start = nearestIndex(path, STOP_COORDS[stops[0]], 0);
    for (let index = 1; index < stops.length; index++) {
      const end = nearestIndex(path, STOP_COORDS[stops[index]], start);
      const name = NAMES[`${stops[index - 1]}|${stops[index]}`];
      if (name && !named.has(name) && end - start >= 1) {
        named.add(name);
        features.push({
          type: 'Feature',
          properties: { name },
          geometry: { type: 'LineString', coordinates: path.slice(start, end + 1) }
        });
      }
      start = end;
    }
  }
  return { type: 'FeatureCollection', features };
}
