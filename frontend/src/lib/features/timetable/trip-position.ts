/**
 * Where a trip is at a given minute, from its stop times in operating-day minutes. Kept pure so
 * the midnight cases are unit tested: at 00:05 a trip that left at 23:52 is at minute 1445.
 */
export type TimedCall = { arr: number; dep: number };

export type Position =
  /** Standing at stop `index` (between its arrival and departure). */
  | { kind: 'at'; index: number; share: number }
  /** Between stop `index` and the next one; `share` 0..1 of the hop done. */
  | { kind: 'between'; index: number; share: number };

export function tripPosition(stops: TimedCall[], now: number): Position | null {
  const first = stops.at(0);
  const last = stops.at(-1);
  if (!first || !last) return null;
  // Just after midnight "now" may be on the previous operating day's 24:xx clock.
  const t = [now, now + 1440].find((m) => m >= first.dep && m <= last.arr);
  if (t === undefined) return null;
  for (let i = 0; i < stops.length; i++) {
    const stop = stops[i];
    if (t >= stop.arr && t <= stop.dep) {
      const wait = stop.dep - stop.arr;
      return { kind: 'at', index: i, share: wait > 0 ? (t - stop.arr) / wait : 0 };
    }
    const next = stops[i + 1];
    if (next && t > stop.dep && t < next.arr) {
      return { kind: 'between', index: i, share: (t - stop.dep) / (next.arr - stop.dep) };
    }
  }
  return null;
}
