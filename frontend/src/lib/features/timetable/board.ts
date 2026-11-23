/** Pure departure-board logic, kept out of the component so it can be unit tested. */
export type Timed = { key: string; arr: number; dep: number; label: string };

/**
 * For each row, the visible trips that arrive after it but leave before it (diary: buses wait
 * at Syntax Square). Computed once per data or filter change, not per clock tick.
 */
export function overtakes(rows: Timed[]) {
  const result = new Map<string, string[]>();
  for (const row of rows) {
    const by = rows.filter((o) => o.arr > row.arr && o.dep < row.dep).map((o) => o.label);
    if (by.length) result.set(row.key, by);
  }
  return result;
}

/** Index calls by trip, e.g. to look up when a trip reaches another stop. */
export function byTrip<T extends { trip_id: number }>(rows: T[]): ReadonlyMap<number, T> {
  return new Map(rows.map((row) => [row.trip_id, row]));
}
