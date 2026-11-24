<script lang="ts">
  import { czechCount } from '$lib/plural';
  import { Button } from '$lib/ui/button';
  import { ArrowRight, ChevronDown, MapPin } from '@lucide/svelte';
  import LinePlate from './LinePlate.svelte';
  import { vehicleLabel } from '$lib/features/planner/planner-core';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import { resolve } from '$app/paths';
  import type { Route } from '$lib/api/generated';
  import { clock } from './clock.svelte';
  import type { LineTimetable } from './line-timetable.svelte';
  import { hourGroups, nextDeparture, operatingNow } from './line-trips';
  import { countdown, minutesOf, spokenTime } from './time';
  import { withQuery } from './trips';

  let {
    timetable,
    route,
    lineNumber,
    lineColor,
    stopId = $bindable(null)
  }: {
    timetable: LineTimetable;
    route: Route;
    lineNumber: string;
    lineColor: string;
    /** Boarding stop, shared with the route diagram so it can highlight it. */
    stopId?: number | null;
  } = $props();

  // A new direction starts from its own first stop.
  $effect(() => {
    void route.id;
    stopId = null;
  });
  $effect(() => clock.subscribe());
  const now = $derived(clock.now);

  const boardable = $derived(route.stops.slice(0, -1));
  const selected = $derived(boardable.find((s) => s.id === stopId) ?? boardable[0]);
  const atOrigin = $derived(selected?.id === route.stops[0]?.id);

  type Call = { time: string; dayOffset: number; minutes: number };
  const call = (time: string, dayOffset: number): Call => ({
    time,
    dayOffset,
    minutes: minutesOf(time, dayOffset)
  });

  const ofRoute = $derived((timetable.trips ?? []).filter((trip) => trip.route_id === route.id));

  /**
   * From the origin the list endpoint already has every time, so rows render at once; for a
   * later stop each row waits for its trip's stop times.
   */
  const rows = $derived(
    ofRoute
      .flatMap((trip) => {
        const detail = timetable.details.get(trip.id);
        const last = detail?.stops.at(-1);
        const end = last ? call(last.arrival_time, last.arrival_day_offset) : null;
        let at: Call;
        if (atOrigin) at = call(trip.departure_time, trip.departure_day_offset);
        else {
          const here = detail?.stops.find((stop) => stop.stop.id === selected?.id);
          if (!here) return [];
          at = call(here.departure_time, here.departure_day_offset);
        }
        // The trip reaches its terminus on the next calendar day.
        const night = end !== null && end.dayOffset > at.dayOffset;
        return [{ trip, at, dep: at.minutes, end, night }];
      })
      .sort((a, b) => a.dep - b.dep)
  );
  /** Trips whose times at this stop are still loading or failed to load. */
  const waiting = $derived(
    atOrigin ? 0 : ofRoute.filter((trip) => !timetable.details.has(trip.id)).length
  );
  const failedHere = $derived(
    atOrigin ? 0 : ofRoute.filter((trip) => timetable.failed.has(trip.id)).length
  );

  const nowOp = $derived(operatingNow(rows, now));
  const next = $derived(nextDeparture(rows, nowOp));
  /** Every empty run folds into one gap row, a single empty hour included: one rule for the scale. */
  const hours = $derived(
    hourGroups(rows).map((h) =>
      h.kind === 'hour' && h.trips.length === 0
        ? { kind: 'gap' as const, from: h.hour, to: h.hour, label: h.label }
        : h
    )
  );
  const currentHour = $derived(Math.floor(nowOp / 60));
  /** The "teď" tick sits in the hour row (or gap) that holds the current hour. */
  const holdsNow = (h: (typeof hours)[number]) =>
    !next?.tomorrow &&
    (h.kind === 'gap' ? currentHour >= h.from && currentHour <= h.to : h.hour === currentHour);

  function tripHref(tripId: number) {
    return withQuery(resolve('/(map)/trips/[id]', { id: String(tripId) }), { od: selected?.id });
  }

  /** "Bus B03", or the line number when the trip has no vehicle assigned. */
  const vehicle = (row: (typeof rows)[number]) => vehicleLabel(row.trip.block_ref) ?? '';

  /** The trip's departure from the first stop, when the sheet is read at a later stop. */
  const originTime = (row: (typeof rows)[number]) => (atOrigin ? null : row.trip.departure_time);

  /** Short per-minute label; the shared context (line, stop, direction) is in the caption. */
  function label(row: (typeof rows)[number]) {
    return [
      [vehicle(row), spokenTime(row.at.time, row.at.dayOffset)].filter(Boolean).join(' · '),
      originTime(row)
        ? `z ${route.stops[0]?.name} ${spokenTime(row.trip.departure_time, row.trip.departure_day_offset)}`
        : '',
      row.end
        ? `příjezd ${row.trip.destination.name} ${spokenTime(row.end.time, row.end.dayOffset)}`
        : '',
      next?.row.trip.id === row.trip.id ? 'nejbližší odjezd' : ''
    ]
      .filter(Boolean)
      .join(', ');
  }
</script>

<section class="trips" aria-labelledby={`trips-heading-${route.id}`}>
  <header class="sheet-head">
    <h3 id={`trips-heading-${route.id}`} class="sr-only">Odjezdy směr {route.direction}</h3>
    {#if boardable.length > 1}
      <label class="board-pick">
        <span class="pick-label"><MapPin size={18} aria-hidden="true" />Ze zastávky</span>
        <span class="pick-box">
          <select value={selected?.id} onchange={(e) => (stopId = Number(e.currentTarget.value))}>
            {#each boardable as stop (stop.id)}
              <option value={stop.id}>{stop.name}</option>
            {/each}
          </select>
          <ChevronDown size={18} aria-hidden="true" />
        </span>
      </label>
    {:else}
      <p class="board-pick">
        <span class="pick-label"><MapPin size={18} aria-hidden="true" />Ze zastávky</span>
        <strong>{selected?.name}</strong>
      </p>
    {/if}
    {#if timetable.loading && timetable.trips}<span class="sr-only" role="status"
        >Načítáme spoje…</span
      >{/if}
  </header>

  {#if timetable.error}
    <PageState
      title={timetable.error === 'missing'
        ? 'Linka už v jízdním řádu není'
        : 'Spoje se nepodařilo načíst'}
      error={timetable.error !== 'missing'}
      onretry={timetable.error === 'missing' ? undefined : timetable.retry}
    />
  {:else if !timetable.trips || (rows.length === 0 && waiting > failedHere)}
    <LoadingState label="Načítáme spoje…" />
  {:else if ofRoute.length === 0}
    <PageState title="V tomto směru žádný spoj nejede" />
  {:else if rows.length === 0}
    <PageState title="Spoje se nepodařilo načíst" error onretry={timetable.retry} />
  {:else}
    {#if next}
      {@const cd = countdown(next.until)}
      <a
        class="next"
        href={tripHref(next.row.trip.id)}
        aria-label={`Nejbližší odjezd ze zastávky ${selected?.name}: ${label(next.row)}`}
      >
        <span class="next-time tabular"
          ><span class="cap">{next.tomorrow ? 'Nejbližší odjezd zítra' : 'Nejbližší odjezd'}</span
          >{next.row.at.time}</span
        >
        <span class="next-what">
          <span class="next-vehicle"
            ><LinePlate number={lineNumber} color={lineColor} size="sm" />{vehicle(next.row)}</span
          >
          {#if next.row.end}
            <span class="next-end tabular"
              ><ArrowRight size={18} aria-hidden="true" />příjezd {next.row.trip.destination.name}
              {next.row.end.time}</span
            >
          {/if}
        </span>
        <span class="next-when tabular"
          >{next.tomorrow
            ? 'zítra'
            : (cd ?? `za ${Math.floor(next.until / 60)} h ${next.until % 60} min`)}</span
        >
      </a>
    {/if}

    {#key selected?.id}
      <table class="grid">
        <caption class="sr-only">
          Odjezdy linky {lineNumber} ze zastávky {selected?.name} směr {route.direction}, po
          hodinách. Každá minuta otevře detail spoje.
        </caption>
        <thead>
          <tr>
            <th scope="col" class="col-head">Hodina</th>
            <th scope="col" class="col-head">Minuta odjezdu · směr {route.direction}</th>
          </tr>
        </thead>
        <tbody>
          {#each hours as h, index (h.kind === 'hour' ? h.hour : `gap-${h.from}`)}
            {@const isNow = holdsNow(h)}
            {#if h.kind === 'gap'}
              <tr class="gap" aria-current={isNow ? 'time' : undefined} style:--row={index}>
                <th scope="row" class="hour tabular">
                  {h.label}{#if isNow}<span class="sr-only">, teď</span>{/if}
                </th>
                <td>
                  <span class="gap-text tabular"
                    >Nejede {String(h.from % 24).padStart(2, '0')}:00–{String(h.to % 24).padStart(
                      2,
                      '0'
                    )}:59</span
                  >
                </td>
              </tr>
            {:else}
              <tr aria-current={isNow ? 'time' : undefined} style:--row={index}>
                <th scope="row" class="hour tabular">
                  {String(h.hour % 24).padStart(2, '0')}{#if isNow}<span class="sr-only">, teď</span
                    >{/if}
                </th>
                <td>
                  <ul class="mins">
                    {#each h.trips as row (row.trip.id)}
                      {@const isNext = next?.row.trip.id === row.trip.id}
                      <li>
                        <a
                          class="min"
                          class:next-min={isNext}
                          class:gone={row.dep < nowOp && !next?.tomorrow}
                          href={tripHref(row.trip.id)}
                          title={label(row)}
                          aria-label={label(row)}
                          ><span class="min-mm tabular">{row.at.time.slice(3, 5)}</span><span
                            class="min-bus">{row.trip.block_ref ?? lineNumber}</span
                          >{#if originTime(row)}<span class="min-origin tabular" aria-hidden="true"
                              >start {originTime(row)}</span
                            >{/if}</a
                        >
                      </li>
                    {/each}
                  </ul>
                </td>
              </tr>
            {/if}
          {/each}
        </tbody>
      </table>
    {/key}

    {#if waiting > 0}
      <p class="note" role="status">
        {failedHere > 0
          ? `U ${czechCount(failedHere, 'spoje', 'spojů', 'spojů')} se časy nepodařilo načíst.`
          : 'Načítáme spoje…'}
        {#if failedHere > 0}<Button variant="outline" size="sm" onclick={timetable.retry}
            >Zkusit znovu</Button
          >{/if}
      </p>
    {/if}
  {/if}
</section>

<style>
  .cap {
    display: block;
    margin-bottom: 0.15rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm, 0.875rem);
    font-weight: 600;
    letter-spacing: 0;
  }
  .col-head {
    padding: 0.5rem 0.75rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm, 0.875rem);
    font-weight: 600;
    text-align: left;
  }
  /* A posted stop timetable: hours down the left, every departure of the hour in one dense row. */
  .trips {
    min-width: 0;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    overflow: clip;
    scroll-margin-top: calc(var(--header-h, 4rem) + 1rem);
  }
  .sheet-head {
    padding: 1rem 1.25rem;
    border-bottom: 1px solid var(--border);
  }
  .board-pick {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 0.75rem;
  }
  .pick-label {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-weight: var(--weight-strong);
  }
  .pick-box {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    max-width: 100%;
    min-height: 2.75rem;
    padding: 0.25rem 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    font-weight: var(--weight-strong);
    cursor: pointer;
  }
  .pick-box:hover {
    background: var(--accent);
  }
  .pick-box:focus-within {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .pick-box select {
    appearance: none;
    min-width: 0;
    max-width: 100%;
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    font-size: var(--fs-lg);
    cursor: pointer;
    outline: none;
  }
  .pick-box select option {
    background: var(--popover);
    color: var(--popover-foreground);
  }
  a:focus-visible {
    outline: 3px solid var(--ring);
    outline-offset: 2px;
  }
  .note {
    padding: 1rem 1.25rem;
  }

  /* The next departure as a board row: time, line + vehicle, destination, countdown. */
  .next {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.5rem 1rem;
    padding: 0.875rem 1.25rem;
    border-bottom: 1px solid var(--border);
    background: var(--muted);
    color: var(--foreground);
  }
  .next-time {
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  .next-what {
    display: grid;
    gap: 0.25rem;
    min-width: 0;
  }
  .next-vehicle,
  .next-end {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
  }
  .next-when {
    padding: 0.25rem 0.625rem;
    border-radius: var(--radius);
    background: var(--foreground);
    color: var(--background);
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    white-space: nowrap;
  }
  .next:hover .next-time {
    text-decoration: underline;
    text-underline-offset: 4px;
  }

  .grid {
    width: 100%;
    border-collapse: collapse;
  }
  .grid tr + tr {
    border-top: 1px solid var(--border);
  }
  .hour {
    width: 3.5rem;
    min-width: 3.5rem;
    white-space: nowrap;
    padding: 0.625rem 0.25rem;
    background: var(--muted);
    border-right: 1px solid var(--border);
    text-align: center;
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    vertical-align: top;
  }
  .grid td {
    width: 100%;
    padding: 0.25rem 0.5rem;
  }
  /* Now: a thin line on top of the current hour row, across the whole sheet. */
  .grid tr[aria-current='time'] {
    box-shadow: inset 0 2px 0 var(--foreground);
  }
  /* An empty run of hours: one thin hatched row, its range is the whole message. */
  .gap .hour {
    padding-block: 0.25rem;
    font-size: var(--fs-base);
  }
  .gap td {
    padding-block: 0.5rem;
  }
  .gap-text {
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
    color: var(--muted-foreground);
  }
  /* Minutes as text on one row, like a PID sheet; the vehicle run follows each minute. */
  .mins {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(4.5rem, 1fr));
    gap: 0.375rem;
    padding-block: 0.25rem;
  }
  /* Each minute is a button-like chip: minute big, the vehicle run underneath. */
  .min {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.125rem;
    min-height: 2.75rem;
    padding: 0.375rem 0.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    color: var(--foreground);
    transition: background var(--motion-fast) var(--ease-out);
  }
  .min-mm {
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: 1.1;
  }
  .min-bus {
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .min:hover {
    border-color: var(--foreground);
    background: var(--accent);
  }
  .min:hover .min-mm {
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .min.gone {
    opacity: 0.45;
  }
  .min.next-min {
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
  }
  .min-origin {
    font-size: var(--fs-sm);
  }
  .grid tr {
    animation: reflow var(--motion-slow) var(--ease-out) both;
    animation-delay: calc(var(--row) * var(--motion-stagger));
  }
  @keyframes reflow {
    from {
      opacity: 0;
      transform: translateX(-0.5rem);
    }
  }
  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
  @container (max-width: 639px) {
    .next {
      grid-template-columns: auto minmax(0, 1fr);
    }
    .next-when {
      grid-column: 1 / -1;
      justify-self: start;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .grid tr {
      animation: none;
    }
  }
</style>
