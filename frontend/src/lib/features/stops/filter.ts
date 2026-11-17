import type { Stop } from '$lib/api/generated';
import { LINES, LINE_IDS, type LineId } from '$lib/features/network/lines.svelte';
import { AMENITIES, type AmenityValue } from './amenities';

export function normalize(value: string) {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('cs').trim();
}

/** Resolves `?line=` against the loaded network; null until the lines arrive or when unknown. */
export function parseLine(value: string | null): LineId | null {
  const id = value?.trim();
  if (!id) return null;
  const upper = id.toUpperCase();
  return (
    LINE_IDS.find((line) => line === id) ??
    LINE_IDS.find((line) => line.toUpperCase() === upper) ??
    null
  );
}

export function filterStops(
  stops: Stop[],
  query: string,
  filters: readonly AmenityValue[],
  line: LineId | null = null
) {
  const needle = normalize(query);
  return stops.filter(
    (stop) =>
      normalize(stop.name).includes(needle) &&
      (!line || (LINES[line]?.stops.includes(stop.name) ?? false)) &&
      AMENITIES.every(({ value, field }) => !filters.includes(value) || stop[field])
  );
}
