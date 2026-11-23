<script lang="ts">
  import { czechCount } from '$lib/plural';
  import { Button } from '$lib/ui/button';
  import { ArrowRight, ChevronDown, MapPin } from '@lucide/svelte';
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
    stopId = $bindable(null)
  }: {
    timetable: LineTimetable;
    route: Route;
    lineNumber: string;
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
  const hours = $derived(hourGroups(rows));
  const currentHour = $derived(Math.floor(nowOp / 60));

  function tripHref(tripId: number) {
    return withQuery(resolve('/trips/[id]', { id: String(tripId) }), { od: selected?.id });
  }

  /** "Bus B03", or the line number when the trip has no vehicle assigned. */
  const vehicle = (row: (typeof rows)[number]) =>
    vehicleLabel(row.trip.block_ref) ?? `Linka ${lineNumber}`;

  /** Short per-minute label; the shared context (line, stop, direction) is in the caption. */
  function label(row: (typeof rows)[number]) {
    return [
      `${vehicle(row)} · ${spokenTime(row.at.time, row.at.dayOffset)}`,
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
    <h3 id={`trips-heading-${route.id}`} class="dir-title">
      <span class="sr-only">Odjezdy </span><span class="dir-word">Směr</span>
      <ArrowRight size={18} aria-hidden="true" />{route.direction}
    </h3>
    {#if boardable.length > 1}
      <!-- The stop is part of the title: picking it here or on the route changes it. -->
      <label class="board-pick">
        <MapPin size={18} aria-hidden="true" />
        <span class="sr-only">Odjezdy ze zastávky</span>
        <select value={selected?.id} onchange={(e) => (stopId = Number(e.currentTarget.value))}>
          {#each boardable as stop (stop.id)}
            <option value={stop.id}>{stop.name}</option>
          {/each}
        </select>
        <ChevronDown size={18} aria-hidden="true" />
      </label>
    {:else}
      <span class="board-pick static"><MapPin size={18} aria-hidden="true" />{selected?.name}</span>
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
        <span class="next-label">Nejbližší</span>
        <span class="next-time tabular">{next.row.at.time}</span>
        <span class="next-vehicle">{vehicle(next.row)}</span>
        {#if next.tomorrow}<span>zítra</span>{:else if cd}<span>{cd}</span>{/if}
        {#if next.row.end}
          <span class="next-end tabular"
            ><ArrowRight size={14} aria-hidden="true" />{next.row.trip.destination.name}
            {next.row.end.time}</span
          >
        {/if}
      </a>
    {/if}

    {#key selected?.id}
      <table class="grid">
        <caption class="sr-only">
          Odjezdy linky {lineNumber} ze zastávky {selected?.name} směr {route.direction}, po
          hodinách. Každá minuta otevře detail spoje.
        </caption>
        <tbody>
          {#each hours as h, index (h.kind === 'hour' ? h.hour : `gap-${h.from}`)}
            {#if h.kind === 'gap'}
              <tr class="gap" style:--row={index}>
                <th scope="row" class="hour tabular">{h.label}</th>
                <td>bez spojů</td>
              </tr>
            {:else}
              {@const isNow = h.hour === currentHour}
              <tr aria-current={isNow ? 'time' : undefined} style:--row={index}>
                <th scope="row" class="hour tabular">{String(h.hour % 24).padStart(2, '0')}</th>
                <td>
                  {#if h.trips.length === 0}<span class="empty">bez spojů</span>{/if}
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
                          ></a
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
    display: grid;
    justify-items: start;
    gap: 0.5rem;
    padding: 1rem 1.25rem;
    border-bottom: 1px solid var(--border);
  }
  .dir-title {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.375rem;
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: var(--lh-tight);
  }
  .board-pick {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    max-width: 100%;
    min-height: 2.5rem;
    padding: 0.25rem 0.625rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    font-weight: var(--weight-strong);
    cursor: pointer;
  }
  .board-pick:hover {
    background: var(--accent);
  }
  .board-pick:focus-within {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .board-pick select {
    appearance: none;
    min-width: 0;
    max-width: 100%;
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    cursor: pointer;
    outline: none;
  }
  .board-pick select option {
    background: var(--popover);
    color: var(--popover-foreground);
  }
  .static {
    cursor: default;
  }
  a:focus-visible {
    outline: 3px solid var(--ring);
    outline-offset: 2px;
  }
  .note {
    padding: 1rem 1.25rem;
  }

  .next {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0.25rem 0.75rem;
    padding: 0.75rem 1.25rem;
    border-bottom: 1px solid var(--border);
    background: var(--muted);
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .next-label {
    font-weight: var(--weight-strong);
  }
  .next-time {
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    line-height: 1;
    text-decoration: underline;
    text-underline-offset: 4px;
    text-decoration-thickness: 2px;
  }
  .next-end {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
  }
  .next:hover .next-time {
    text-decoration-thickness: 3px;
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
  tr[aria-current='time'] .hour {
    background: var(--foreground);
    color: var(--background);
  }
  .gap .hour {
    font-size: var(--fs-sm);
  }
  .gap td,
  .empty {
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
    padding-inline: 0.75rem;
  }
  .mins {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
  }
  /* Minute over vehicle, like a PID sheet with the run number: a link that looks like one. */
  .min {
    display: grid;
    justify-items: center;
    min-width: 3.5rem;
    min-height: 2.75rem;
    padding: 0.25rem 0.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    color: var(--foreground);
    transition: background var(--motion-fast) var(--ease-out);
  }
  .min-mm {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    line-height: 1.1;
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .min-bus {
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
  }
  .min:hover {
    background: var(--accent);
  }
  .min.gone {
    color: var(--muted-foreground);
    border-style: dashed;
  }
  .min.gone .min-mm {
    font-weight: var(--weight-regular);
  }
  .min.next-min {
    border: 2px solid var(--foreground);
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
  @media (max-width: 639px) {
    .dir-title {
      font-size: var(--fs-lg);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .grid tr {
      animation: none;
    }
  }
</style>
