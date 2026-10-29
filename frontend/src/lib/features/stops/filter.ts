import type { Stop } from '$lib/api/generated';
import { LINES, LINE_IDS, type LineId } from '$lib/features/network/lines';
import { AMENITIES, type AmenityValue } from './amenities';

export function normalize(value: string) {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('cs').trim();
}

export function parseLine(value: string | null): LineId | null {
  const id = value?.trim().toUpperCase();
  return LINE_IDS.find((line) => line === id) ?? null;
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
      (!line || LINES[line].stops.includes(stop.name)) &&
      AMENITIES.every(({ value, field }) => !filters.includes(value) || stop[field])
  );
}
