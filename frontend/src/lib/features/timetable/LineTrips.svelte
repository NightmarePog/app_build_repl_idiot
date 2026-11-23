<script lang="ts">
  import { czechCount } from '$lib/plural';
  import { Button } from '$lib/ui/button';
  import { resolve } from '$app/paths';
  import type { Route } from '$lib/api/generated';
  import { clock } from './clock.svelte';
  import type { LineTimetable } from './line-timetable.svelte';
  import { hourGroups, median, nextDeparture, operatingNow, travelMinutes } from './line-trips';
  import { countdown, duration, minutesOf, spokenTime } from './time';
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

  /** Typical end-to-end time in this direction (the other one is on the direction tabs). */
  const travel = $derived(
    median(
      ofRoute.flatMap((trip) => {
        const minutes = travelMinutes(timetable.details.get(trip.id)?.stops ?? []);
        return minutes === null ? [] : [minutes];
      })
    )
  );

  const nowOp = $derived(operatingNow(rows, now));
  const next = $derived(nextDeparture(rows, nowOp));
  const hours = $derived(hourGroups(rows));
  const currentHour = $derived(Math.floor(nowOp / 60));
  const hasNight = $derived(rows.some((r) => r.night));
  const hasAfterMidnight = $derived(rows.some((r) => r.dep >= 1440));

  function tripHref(tripId: number) {
    return withQuery(resolve('/trips/[id]', { id: String(tripId) }), { od: selected?.id });
  }

  /** Short per-minute label; the shared context (line, stop, direction) is in the caption. */
  function label(row: (typeof rows)[number]) {
    return [
      spokenTime(row.at.time, row.at.dayOffset),
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
  <div class="sheet">
    <header class="sheet-head">
      <div class="sheet-title">
        <h3 id={`trips-heading-${route.id}`} class="dir-title">
          <span class="plate" aria-hidden="true">{lineNumber}</span>
          <span>Odjezdy z {selected?.name} · směr {route.direction}</span>
        </h3>
        <p class="toward tabular">
          {#if timetable.trips}{czechCount(ofRoute.length, 'spoj', 'spoje', 'spojů')} denně{/if}
          {#if travel !== null}<span class="travel">· jízda {travel} min</span>{/if}
        </p>
        {#if boardable.length > 1}
          <label class="board-pick">
            <span class="board-pick-label">Nástup na zastávce</span>
            <select value={selected?.id} onchange={(e) => (stopId = Number(e.currentTarget.value))}>
              {#each boardable as stop, i (stop.id)}
                <option value={stop.id}>{stop.name}{i === 0 ? ' (výchozí)' : ''}</option>
              {/each}
            </select>
          </label>
        {/if}
      </div>
      {#if timetable.loading && timetable.trips}<span class="sr-only" role="status">Načítáme…</span
        >{/if}
    </header>

    {#if timetable.error}
      <p class="note">
        {timetable.error === 'missing'
          ? 'Linka už v jízdním řádu není.'
          : 'Spoje se nepodařilo načíst.'}
        <Button variant="outline" size="sm" onclick={timetable.retry}>Zkusit znovu</Button>
      </p>
    {:else if !timetable.trips}
      <p class="note" role="status">Načítáme spoje…</p>
    {:else if ofRoute.length === 0}
      <p class="note">V tomto směru zatím žádný spoj nejede.</p>
    {:else if rows.length === 0 && waiting > failedHere}
      <p class="note" role="status">Načítáme časy pro zastávku {selected?.name}…</p>
    {:else if rows.length === 0}
      <p class="note">
        Časy pro zastávku {selected?.name} se nepodařilo načíst.
        <Button variant="outline" size="sm" onclick={timetable.retry}>Zkusit znovu</Button>
      </p>
    {:else}
      {#if next}
        {@const cd = countdown(next.until)}
        <a
          class="next"
          href={tripHref(next.row.trip.id)}
          aria-label={`Nejbližší spoj ze zastávky ${selected?.name}: ${label(next.row)}`}
        >
          {#if next.tomorrow}
            <span class="next-row">
              <span class="next-gone">Dnes už nejede ·</span>
              <span class="next-tag">další odjezd</span>
              <span class="next-time tabular">zítra {next.row.at.time}</span>
            </span>
          {:else}
            <span class="next-row">
              <span class="next-tag">Další odjezd</span>
              <span class="next-time tabular">{next.row.at.time}</span>
              {#if cd}<strong class="next-cd">{cd}</strong>{/if}
            </span>
          {/if}
          <span class="next-info tabular"
            >{#if next.row.end}příjezd {next.row.trip.destination.name}
              {next.row.end.time}{next.row.night ? ' (+1 den)' : ''} · jízda {duration(
                next.row.end.minutes - next.row.dep
              )}{/if}</span
          >
        </a>
      {/if}

      {#if rows.length === 1 && next}
        <!-- One trip a day: the next-departure block above already is the whole timetable. -->
        <p class="inline-note">Jediný spoj dne v tomto směru.</p>
      {:else if rows.length <= 12}
        <table class="list">
          <caption class="sr-only">
            Odjezdy linky {lineNumber} ze zastávky {selected?.name} směr {route.direction}.
          </caption>
          <thead class="grid-head">
            <tr>
              <th scope="col">Odjezd</th>
              <th scope="col">Příjezd</th>
              <th scope="col"><span class="sr-only">Detail</span></th>
            </tr>
          </thead>
          <tbody>
            {#each rows as row, index (row.trip.id)}
              {@const isNext = next?.row.trip.id === row.trip.id}
              {@const gone = row.dep < nowOp && !next?.tomorrow}
              <tr class:next-row-l={isNext} class:gone style:--row={index}>
                <td class="t tabular"
                  >{row.at.time}{#if row.dep >= 1440}
                    <span class="moon">(+1 den)</span>{/if}</td
                >
                <td class="tabular"
                  >{row.end?.time ?? '…'}{#if row.night}
                    <span class="moon">(+1 den)</span>{/if}
                  <span class="toward-tag">do {row.trip.destination.name}</span></td
                >
                <td class="r"
                  ><a
                    class="detail"
                    href={tripHref(row.trip.id)}
                    aria-label={`Detail spoje: ${label(row)}`}>Detail spoje ›</a
                  ></td
                >
              </tr>
            {/each}
          </tbody>
        </table>
      {:else}
        {#key selected?.id}
          <table class="grid">
            <caption class="sr-only">
              Odjezdy linky {lineNumber} ze zastávky {selected?.name} směr {route.direction}, po
              hodinách. Odkaz minuty otevře detail spoje.
            </caption>
            <thead class="grid-head">
              <tr><th scope="col">Hod.</th><th scope="col">Odjezd (min.)</th></tr>
            </thead>
            <tbody>
              {#each hours as h, index (h.kind === 'hour' ? h.hour : `gap-${h.from}`)}
                {#if h.kind === 'gap'}
                  <tr class="gap" style:--row={index}>
                    <th scope="row" class="hour tabular">{h.label}</th>
                    <td><span class="gap-text">nic nejede</span></td>
                  </tr>
                {:else}
                  {@const isNow = h.hour === currentHour}
                  <tr
                    class:now={isNow}
                    class:late={h.hour >= 24}
                    aria-current={isNow ? 'time' : undefined}
                    style:--row={index}
                  >
                    <th scope="row" class="hour tabular">{h.label}</th>
                    <td>
                      <ul class="mins">
                        {#each h.trips as row (row.trip.id)}
                          <li>
                            <a
                              class="min tabular"
                              class:next-min={next?.row.trip.id === row.trip.id}
                              class:gone={row.dep < nowOp && !next?.tomorrow}
                              href={tripHref(row.trip.id)}
                              title={label(row)}
                              aria-label={label(row)}>{row.at.time.slice(3, 5)}</a
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
      {/if}

      {#if hasAfterMidnight || hasNight}
        <p class="inline-note">(+1 den) = po půlnoci, spoj patří k večernímu provozu.</p>
      {/if}

      {#if waiting > 0}
        <p class="note" role="status">
          {failedHere > 0
            ? `U ${czechCount(failedHere, 'spoje', 'spojů', 'spojů')} se časy nepodařilo načíst.`
            : 'Načítáme další časy…'}
          {#if failedHere > 0}<Button variant="outline" size="sm" onclick={timetable.retry}
              >Zkusit znovu</Button
            >{/if}
        </p>
      {/if}
    {/if}
  </div>
</section>

<style>
  .grid-head th {
    padding: 0.5rem 0.75rem;
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-align: left;
    text-transform: uppercase;
  }
  .inline-note {
    padding: 0.5rem 1rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .board-pick {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem;
    margin-top: 0.5rem;
    font-weight: var(--weight-strong);
  }
  .board-pick select {
    min-height: 2.5rem;
    padding-inline: 0.75rem;
    border: 1.5px solid var(--border);
    border-radius: var(--radius-md);
    background: var(--background);
    color: var(--foreground);
    font: inherit;
  }
  .list {
    width: 100%;
    border-collapse: collapse;
  }
  .list td,
  .list th {
    padding: 0.625rem 1rem;
    text-align: left;
  }
  .list .r {
    text-align: right;
  }
  .list tbody tr {
    border-top: 1px solid var(--border);
    animation: reflow var(--motion-slow) var(--ease-out) both;
    animation-delay: calc(var(--row) * var(--motion-stagger));
  }
  /* The whole row opens the trip; the visible link is only the label. */
  .list tbody tr {
    position: relative;
  }
  .list tbody tr:hover {
    background: var(--accent);
  }
  .detail::after {
    content: '';
    position: absolute;
    inset: 0;
  }
  .list th:first-child,
  .list td:first-child {
    width: 7rem;
  }
  .list .t {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    font-variant-numeric: tabular-nums;
  }
  .list tr.gone {
    color: var(--muted-foreground);
  }
  .list tr.next-row-l {
    background: color-mix(in oklch, var(--sign-marker) 35%, transparent);
  }
  .detail {
    font-weight: var(--weight-strong);
    color: var(--primary);
    white-space: nowrap;
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .trips {
    min-width: 0;
    scroll-margin-top: calc(var(--header-h, 4rem) + 1rem);
  }
  a:focus-visible {
    outline: 3px solid var(--ring);
    outline-offset: 2px;
  }

  /* The posted sheet: paper card, line-colour band on top like a stop timetable frame. */
  .sheet {
    border: 1px solid var(--border);
    border-top: 6px solid var(--line, var(--primary));
    border-radius: var(--radius-xl);
    background: var(--card);
    /* clip, not hidden: hidden would stop the sheet header from sticking. */
    overflow: clip;
  }
  .sheet-head {
    position: sticky;
    top: var(--header-h, 0px);
    z-index: 2;
    background: var(--card);
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 1rem 1.25rem;
    border-bottom: 2px solid var(--foreground);
  }
  .plate {
    /* Morphs into the trip page's line plate (View Transitions, see +layout.svelte). */
    view-transition-name: line-plate;
    display: grid;
    place-items: center;
    flex-shrink: 0;
    min-width: 2.5rem;
    height: 2.5rem;
    padding-inline: 0.5rem;
    border-radius: var(--radius-lg);
    background: var(--line, var(--primary));
    color: var(--line-ink, var(--primary-foreground));
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
  }
  .sheet-title {
    display: grid;
    gap: 0.25rem;
    min-width: 0;
  }
  .dir-title {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: var(--lh-tight);
  }
  .next-gone {
    color: var(--board-fg);
    font-weight: var(--weight-strong);
  }
  .toward {
    color: var(--muted-foreground);
  }
  .travel {
    white-space: nowrap;
  }

  /* Day ribbon: the whole operating day on one axis, a tick per departure. */
  .gap .hour {
    font-size: var(--fs-sm);
    color: var(--muted-foreground);
    background: transparent;
  }
  .gap-text {
    display: inline-flex;
    align-items: center;
    min-height: 2rem;
    padding-inline: 0.5rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-style: italic;
  }
  .note {
    padding: 1rem 1.25rem;
    color: var(--muted-foreground);
  }

  .next {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 1rem;
    padding: 0.875rem 1.25rem;
    background: var(--board-bg);
    color: var(--board-fg);
  }
  .next-tag {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-pill);
    background: var(--sign-marker);
    color: var(--sign-marker-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--font-display-weight);
    text-transform: uppercase;
    letter-spacing: var(--tracking-eyebrow);
  }
  .next-time {
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    line-height: 1;
    color: var(--board-led);
  }
  .next {
    flex-direction: column;
    align-items: flex-start;
  }
  .next-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 0.75rem;
  }
  .next-cd {
    color: var(--board-fg);
  }
  .next-info {
    display: block;
    max-width: 100%;
    color: var(--board-muted);
    font-weight: var(--weight-strong);
  }
  .next:hover .next-time {
    text-decoration: underline;
  }

  .grid {
    width: 100%;
    border-collapse: collapse;
  }
  .grid tr {
    border-bottom: 1px solid var(--border);
  }
  .grid tr:last-child {
    border-bottom: 0;
  }
  .hour {
    width: 3.5rem;
    padding: 0.5rem 0;
    background: var(--muted);
    border-right: 2px solid var(--foreground);
    text-align: center;
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    vertical-align: top;
  }
  .grid td {
    padding: 0.25rem 0.5rem;
  }
  tr.late .hour {
    background: color-mix(in oklch, var(--foreground) 12%, var(--muted));
  }
  tr.now .hour {
    background: var(--line, var(--primary));
    color: var(--line-ink, var(--primary-foreground));
  }
  tr.now td {
    background: color-mix(in oklch, var(--line, var(--primary)) 8%, transparent);
  }
  .mins {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(4.5rem, 1fr));
    gap: 0.25rem;
  }
  .min {
    position: relative;
    display: inline-grid;
    justify-items: center;
    align-content: center;
    min-width: 4.5rem;
    min-height: 3.25rem;
    padding-inline: 0.5rem;
    border-radius: var(--radius-lg);
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    color: var(--foreground);
    transition: background var(--motion-fast) var(--ease-out);
  }
  .min:hover {
    background: var(--accent);
    text-decoration: underline;
  }
  .min.gone {
    color: var(--muted-foreground);
    font-weight: var(--weight-regular);
  }
  .min.next-min {
    background: var(--sign-marker);
    color: var(--sign-marker-foreground);
    font-weight: var(--font-display-weight);
    box-shadow: inset 0 0 0 2px var(--foreground);
  }
  .moon {
    margin-left: 0.25rem;
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    white-space: nowrap;
  }
  .toward-tag {
    display: block;
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
  }
  /* Picking a stop re-flows the minutes row by row. */
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
  @media (max-width: 30rem) {
    .hour {
      width: 2.75rem;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .min,
    .list tbody tr,
    .grid tr {
      animation: none;
    }
  }
</style>
