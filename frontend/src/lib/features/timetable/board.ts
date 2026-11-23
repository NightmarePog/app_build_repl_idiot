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

type Plate = { number: string; color: string };

export type Arrival = {
  trip_id: number;
  line: Plate;
  /** Where the trip comes from. */
  from: string;
  time: string;
  dayOffset: number;
  minutes: number;
  /** The trip ends here, so everybody gets off. */
  ends: boolean;
};

type Through = { trip_id: number; line: Plate; arrival_time: string; arrival_day_offset: number };
type Ending = {
  id: number;
  line: Plate;
  origin: { name: string };
  stops: { stop: { id: number }; arrival_time: string; arrival_day_offset: number }[];
};

const minutesOf = (time: string, dayOffset: number) => {
  const [h, m] = time.split(':').map(Number);
  return dayOffset * 1440 + h * 60 + m;
};

/** Every bus arriving at a stop over the day: trips passing through plus trips ending there. */
export function arrivalsAt<T extends Through>(
  stopId: number,
  through: T[],
  ending: Ending[],
  originOf: (row: T) => string
): Arrival[] {
  const rows: Arrival[] = through.map((d) => ({
    trip_id: d.trip_id,
    line: d.line,
    from: originOf(d),
    time: d.arrival_time,
    dayOffset: d.arrival_day_offset,
    minutes: minutesOf(d.arrival_time, d.arrival_day_offset),
    ends: false
  }));
  for (const trip of ending) {
    const call = trip.stops.findLast((s) => s.stop.id === stopId);
    if (!call || rows.some((r) => r.trip_id === trip.id)) continue;
    rows.push({
      trip_id: trip.id,
      line: trip.line,
      from: trip.origin.name,
      time: call.arrival_time,
      dayOffset: call.arrival_day_offset,
      minutes: minutesOf(call.arrival_time, call.arrival_day_offset),
      ends: true
    });
  }
  return rows.sort((a, b) => a.minutes - b.minutes || a.trip_id - b.trip_id);
}
