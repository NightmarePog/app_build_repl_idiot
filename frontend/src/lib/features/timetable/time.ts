/** Minutes after midnight of the day the trip starts, so 00:14 the next day is 1454. */
export function minutesOf(time: string, dayOffset = 0) {
  const [hours, minutes] = time.split(':').map(Number);
  return dayOffset * 1440 + hours * 60 + minutes;
}

export function clock(minutes: number) {
  const day = ((minutes % 1440) + 1440) % 1440;
  return `${String(Math.floor(day / 60)).padStart(2, '0')}:${String(day % 60).padStart(2, '0')}`;
}

/** Czech minute count: "1 min", "25 min", "1 h 05 min". */
export function duration(minutes: number) {
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min`;
}

/** Spoken form for screen readers when a time falls on the next day. */
export function spokenTime(time: string, dayOffset: number) {
  return dayOffset > 0 ? `${time}, následující den` : time;
}

const PRAGUE = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Europe/Prague',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23'
});

/** Minutes since midnight now in Prague, where the timetable runs, whatever the viewer's zone. */
export function nowMinutes(date = new Date()) {
  const parts = PRAGUE.formatToParts(date);
  const part = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  return part('hour') * 60 + part('minute');
}

/**
 * Minutes until a departure given in operating-day minutes (24:09 is 1449), or null when it has
 * already gone. Just after midnight, last night's late trip (1449 at now = 5) is 4 min away.
 */
export function minutesUntil(dep: number, now: number) {
  const candidates = [dep - now, dep - 1440 - now].filter((m) => m >= 0);
  return candidates.length ? Math.min(...candidates) : null;
}

/** Countdown text, only for departures within the hour; null means show the plain time. */
export function countdown(minutes: number | null) {
  if (minutes === null || minutes > 60) return null;
  if (minutes <= 1) return 'odjíždí';
  return `za ${minutes} min`;
}

/** Plain-language day shift: 'po půlnoci' for the small hours of the next day. */
export function dayLabel(dayOffset: number) {
  return dayOffset === 1 ? 'po půlnoci' : dayOffset > 1 ? `+${dayOffset} dny` : '';
}

/** Relative time for any distance: "odjíždí", "za 12 min", "za 2 h 35 min". */
export function relative(minutes: number | null) {
  if (minutes === null) return null;
  if (minutes <= 1) return 'odjíždí';
  return `za ${duration(minutes)}`;
}
