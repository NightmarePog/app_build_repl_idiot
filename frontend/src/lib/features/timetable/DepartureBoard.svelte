<script lang="ts">
  import { resolve } from '$app/paths';
  import { flip } from 'svelte/animate';
  import {
    listLineTrips,
    listStopDepartures,
    type Departure,
    type TripDetail
  } from '$lib/api/generated';
  import { createResource, unwrap } from '$lib/api/resource.svelte';
  import { LINES, loadNetwork } from '$lib/features/network/lines.svelte';
  import { motionMs } from '$lib/motion';
  import { arrivalsAt, byTrip, overtakes, type Arrival } from './board';
  import { clock as appClock } from './clock.svelte';
  import LinePlate from './LinePlate.svelte';
  import { clock, minutesOf, minutesUntil, relative, spokenTime } from './time';
  import { lineDirectionHref, tripDetail, withQuery } from './trips';

  let { stopId, stopName }: { stopId: number; stopName: string } = $props();

  /** Upcoming rows shown at first; "Zobrazit další" adds this many more. */
  const PAGE = 20;

  const board = createResource(
    (signal) => unwrap(listStopDepartures({ path: { stopId }, signal })),
    () => stopId
  );
  const departures = $derived(board.data);

  $effect(() => appClock.subscribe());
  const now = $derived(appClock.now);

  let mode = $state<'upcoming' | 'day' | 'arrivals'>('upcoming');
  /** Rows that get a countdown: the next two, like a platform display. */
  const COUNTDOWNS = 2;
  let limit = $state(PAGE);
  let lineFilter = $state<string[]>([]);
  /** "Kam jedete": only trips that later reach this stop, with the arrival time there. */
  let target = $state<number | null>(null);
  let destQuery = $state('');
  const fold = (text: string) =>
    text
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase();

  $effect(() => {
    void loadNetwork();
  });

  // Stops reachable from here: every stop after this one on a route of a line calling here.
  const reachable = $derived.by(() => {
    const later = Object.values(LINES)
      .flatMap((line) => line.routes)
      .flatMap((route) => {
        const here = route.stops.findIndex((stop) => stop.id === stopId);
        return here < 0 ? [] : route.stops.slice(here + 1);
      })
      .map((stop) => [stop.id, stop.name] as const);
    return [...new Map(later)].sort((a, b) => a[1].localeCompare(b[1], 'cs'));
  });

  // Timetables of the trips calling here, read only once a target is chosen (cached per trip).
  const trips = createResource(async (signal) => {
    if (target === null || !departures) return null;
    const details = await Promise.all(departures.map((d) => tripDetail(d.trip_id, signal)));
    return byTrip(details.map((detail) => ({ ...detail, trip_id: detail.id })));
  });

  /** Lines whose route ends here: those trips only arrive, so the departures API leaves them out. */
  const endingLines = $derived(
    Object.values(LINES).filter((line) =>
      line.routes.some((route) => route.stops.at(-1)?.id === stopId)
    )
  );
  const ending = createResource(async (signal) => {
    if (mode !== 'arrivals') return null;
    const lists = await Promise.all(
      endingLines.map((line) => unwrap(listLineTrips({ path: { lineId: line.apiId }, signal })))
    );
    const ends = lists.flat().filter((trip) => trip.destination.id === stopId);
    return Promise.all(ends.map((trip) => tripDetail(trip.id, signal)));
  });
  const originOf = (d: Departure) =>
    LINES[d.line.number]?.routes.find((r) => r.bound === d.direction)?.stops[0]?.name ?? '';
  const arrivals = $derived<Arrival[] | null>(
    mode === 'arrivals' && departures && ending.data
      ? arrivalsAt(stopId, departures, ending.data, originOf)
      : null
  );

  /** When the trip reaches the target stop after calling here, or null. */
  function reachOf(trip: TripDetail | undefined) {
    if (!trip || target === null) return null;
    const here = trip.stops.findIndex((s) => s.stop.id === stopId);
    const there = trip.stops.findIndex((s) => s.stop.id === target);
    if (here < 0 || there <= here) return null;
    const stop = trip.stops[there];
    return {
      time: stop.arrival_time,
      dayOffset: stop.arrival_day_offset,
      minutes: minutesOf(stop.arrival_time, stop.arrival_day_offset)
    };
  }

  type Row = Departure & {
    key: string;
    arr: number;
    dep: number;
    /** Arrival at the "kam jedete" stop, when one is chosen. */
    reach: { time: string; dayOffset: number; minutes: number } | null;
    label: string;
    /** The next stop after this one, so two trips to the same terminus are told apart. */
    via: string | null;
  };

  function nextStopOf(d: Departure) {
    const route = LINES[d.line.number]?.routes.find((r) => r.bound === d.direction);
    const here = route?.stops.findIndex((stop) => stop.id === stopId) ?? -1;
    return here < 0 ? null : (route?.stops[here + 1]?.name ?? null);
  }

  const lines = $derived(
    [...new Map((departures ?? []).map((d) => [d.line.number, d.line])).values()].sort((a, b) =>
      a.number.localeCompare(b.number, 'cs')
    )
  );

  // Everything that does not depend on the clock: filters and overtakes.
  const filtered = $derived.by<Row[]>(() =>
    (departures ?? [])
      .map((d) => ({
        ...d,
        key: `${d.trip_id}`,
        arr: minutesOf(d.arrival_time, d.arrival_day_offset),
        dep: minutesOf(d.departure_time, d.departure_day_offset),
        reach: reachOf(trips.data?.get(d.trip_id)),
        label: `${d.line.number} → ${d.destination.name} ${d.departure_time}`,
        via: nextStopOf(d)
      }))
      .filter(
        (d) =>
          (lineFilter.length === 0 || lineFilter.includes(d.line.number)) &&
          (target === null || d.reach !== null)
      )
  );
  // Only trips the passenger can see on the board are named as overtaking.
  const overtaken = $derived(overtakes(filtered));

  /** Now on the operating-day clock: just after midnight, last evening's 24:xx trips still run. */
  const nowOp = $derived(filtered.some((d) => d.dep >= now + 1440) ? now + 1440 : now);

  type Shown = Row & { inMinutes: number | null; tomorrow: boolean; gone: boolean };
  const allRows = $derived.by<Shown[]>(() => {
    if (mode === 'day')
      return filtered
        .map((d) => ({ ...d, inMinutes: null, tomorrow: false, gone: d.dep < nowOp }))
        .sort((a, b) => a.dep - b.dep);
    const timed = filtered.map((d) => ({ ...d, inMinutes: minutesUntil(d.dep, now) }));
    const upcoming = timed
      .filter((d) => d.inMinutes !== null)
      .sort((a, b) => (a.inMinutes ?? 0) - (b.inMinutes ?? 0))
      .map((d) => ({ ...d, tomorrow: false, gone: false }));
    const later = timed
      .filter((d) => d.inMinutes === null)
      .sort((a, b) => a.dep - b.dep)
      .map((d) => ({ ...d, tomorrow: true, gone: false }));
    return [...upcoming, ...later];
  });
  const rows = $derived(mode === 'upcoming' ? allRows.slice(0, limit) : allRows);
  const more = $derived(allRows.length - rows.length);

  const nextRow = $derived(mode === 'upcoming' ? rows.find((r) => !r.tomorrow) : undefined);
  const firstTomorrow = $derived(rows.findIndex((row) => row.tomorrow));
  /** Full-day view: the "teď" divider goes before the first row still to come. */
  const nowIndex = $derived(mode === 'day' ? rows.findIndex((row) => !row.gone) : -1);
  const tomorrowDate = $derived.by(() => {
    const date = new Date(Date.now() + 86_400_000);
    return new Intl.DateTimeFormat('cs-CZ', {
      timeZone: 'Europe/Prague',
      weekday: 'long',
      day: 'numeric',
      month: 'numeric'
    }).format(date);
  });

  function toggleLine(number: string) {
    lineFilter = lineFilter.includes(number)
      ? lineFilter.filter((n) => n !== number)
      : [...lineFilter, number];
    limit = PAGE;
  }

  const tripHref = (tripId: number) =>
    withQuery(resolve('/trips/[id]', { id: String(tripId) }), { od: stopId });

  function rowLabel(row: Shown, by: string[] | undefined) {
    const waits = row.dep - row.arr;
    return [
      `Linka ${row.line.number} směr ${row.destination.name}`,
      waits > 0
        ? `příjezd ${spokenTime(row.arrival_time, row.arrival_day_offset)}, odjezd ${spokenTime(row.departure_time, row.departure_day_offset)}`
        : `příjezd a odjezd ${spokenTime(row.departure_time, row.departure_day_offset)}`,
      waits > 0 ? `stojí ${waits} min` : '',
      by ? `přijede dřív, ale odjede až po spoji ${by.join(', ')}` : '',
      row.reach
        ? `v zastávce ${targetName} ${spokenTime(row.reach.time, row.reach.dayOffset)}`
        : '',
      relative(row.inMinutes) ?? '',
      row.tomorrow ? 'zítra' : '',
      row.gone ? 'už odjel' : ''
    ]
      .filter(Boolean)
      .join(', ');
  }

  const targetName = $derived(reachable.find(([id]) => id === target)?.[1] ?? '');
  const stripe = $derived(lines.length === 1 ? lines[0].color : undefined);
  /** Re-keys the rows when a filter changes, so they flap in again, staggered. */
  const filterKey = $derived(`${mode}|${lineFilter.join(',')}|${target ?? ''}`);
  /** One line picked: link to its full timetable (the stop's per-line list lives here now). */
  const onlyLine = $derived(
    lineFilter.length === 1 ? lines.find((line) => line.number === lineFilter[0]) : undefined
  );
  const onlyRoute = $derived(
    onlyLine
      ? (departures ?? []).find((d) => d.line.number === onlyLine.number)?.direction
      : undefined
  );
</script>

<section class="board-wrap" aria-labelledby="board-heading">
  <div class="board board-surface" style:--stripe={stripe}>
    <header class="board-head">
      <div>
        <!-- The page title already names the stop; the board only says what it lists. -->
        <h2 id="board-heading" class="board-title">Odjezdy</h2>
        <p class="board-sub">ze zastávky <strong>{stopName}</strong> · živě podle pražského času</p>
      </div>
      <p class="clock" aria-label={`Teď je ${clock(now)} pražského času`}>
        <span class="clock-label" aria-hidden="true">Nyní</span>
        <span class="clock-time tabular" aria-hidden="true">{clock(now)}</span>
      </p>
    </header>

    {#if nextRow && nextRow.inMinutes !== null && nextRow.inMinutes <= 60}
      <!-- The one big number a passenger looks for; polite so it is announced once per change. -->
      <a class="hero" class:leaving={nextRow.inMinutes <= 2} href={tripHref(nextRow.trip_id)}>
        <LinePlate number={nextRow.line.number} color={nextRow.line.color} size="lg" />
        <span class="hero-dest">
          <span class="hero-label">Nejbližší · {nextRow.departure_time}</span>
          <strong>{nextRow.destination.name}</strong>
        </span>
        <span class="hero-time" aria-live="polite">
          {nextRow.inMinutes <= 1 ? 'odjíždí' : `za ${nextRow.inMinutes} min`}
        </span>
      </a>
    {/if}

    {#if departures && (departures.length > 0 || endingLines.length > 0)}
      <div class="controls">
        <span class="chips-label" id="board-range-label">Zobrazit:</span>
        <div class="segmented" role="group" aria-labelledby="board-range-label">
          <button
            type="button"
            aria-pressed={mode === 'upcoming'}
            onclick={() => ((mode = 'upcoming'), (limit = PAGE))}>Od teď</button
          >
          <button type="button" aria-pressed={mode === 'day'} onclick={() => (mode = 'day')}
            >Celý den</button
          >
          <button
            type="button"
            aria-pressed={mode === 'arrivals'}
            onclick={() => (mode = 'arrivals')}>Příjezdy</button
          >
        </div>
        {#if lines.length > 1 && mode !== 'arrivals'}
          <div class="chips" role="group" aria-labelledby="board-lines-label">
            <span class="chips-label" id="board-lines-label">Filtrovat linky:</span>
            {#each lines as line (line.number)}
              {@const on = lineFilter.includes(line.number)}
              <button
                type="button"
                class="chip"
                aria-pressed={on}
                onclick={() => toggleLine(line.number)}
              >
                <LinePlate number={line.number} color={line.color} size="sm" />
                <span class="chip-mark" aria-hidden="true">{on ? '✓' : ''}</span>
                <span class="sr-only">linka {line.number}</span>
              </button>
            {/each}
          </div>
        {/if}
        {#if reachable.length > 0 && mode !== 'arrivals'}
          <div class="dests">
            <label class="dests-label" for="board-dest">Kam jedete</label>
            <div class="dests-field">
              <input
                id="board-dest"
                type="search"
                list="board-dest-options"
                placeholder="Hledat cílovou zastávku"
                autocomplete="off"
                value={destQuery}
                oninput={(event) => {
                  destQuery = event.currentTarget.value;
                  const key = fold(destQuery);
                  const hit = reachable.find(([, name]) => fold(name) === key);
                  target = hit ? hit[0] : null;
                  limit = PAGE;
                }}
              />
              <datalist id="board-dest-options">
                {#each reachable as [id, name] (id)}
                  <option value={name}></option>
                {/each}
              </datalist>
            </div>
            <span class="dests-hint" aria-live="polite">
              {target === null
                ? destQuery.trim()
                  ? 'Vyberte zastávku ze seznamu'
                  : 'Zobrazujeme spoje do všech zastávek'
                : 'Jen spoje, které tam jedou, s časem příjezdu'}
            </span>
          </div>
        {/if}
      </div>
    {/if}

    {#if board.error}
      <p class="empty">
        Odjezdy se nepodařilo načíst.
        <button type="button" class="retry" onclick={board.retry}>Zkusit znovu</button>
      </p>
    {:else if !departures}
      <ol class="rows" aria-busy="true" aria-label="Načítáme odjezdy">
        {#each [0, 1, 2] as index (index)}
          <li class="row skeleton" style:animation-delay={`${index * 120}ms`}></li>
        {/each}
      </ol>
    {:else if mode === 'arrivals'}
      {#if ending.error}
        <p class="empty">
          Příjezdy se nepodařilo načíst.
          <button type="button" class="retry" onclick={ending.retry}>Zkusit znovu</button>
        </p>
      {:else if !arrivals}
        <ol class="rows" aria-busy="true" aria-label="Načítáme příjezdy">
          {#each [0, 1, 2] as index (index)}
            <li class="row skeleton" style:animation-delay={`${index * 120}ms`}></li>
          {/each}
        </ol>
      {:else if arrivals.length === 0}
        <p class="empty">Sem dnes žádný spoj nepřijíždí.</p>
      {:else}
        <div class="legend" aria-hidden="true">
          <span>Linka</span><span>Odkud</span><span class="r">Příjezd</span><span></span>
        </div>
        <ol class="rows" aria-label={`Příjezdy do zastávky ${stopName}`}>
          {#each arrivals as row, index (row.trip_id)}
            <li
              class="row"
              class:gone={row.minutes < nowOp}
              style:--i={index}
              animate:flip={{ duration: motionMs(320) }}
            >
              <a
                class="hit"
                href={tripHref(row.trip_id)}
                aria-label={`Linka ${row.line.number} ze zastávky ${row.from}, příjezd ${spokenTime(row.time, row.dayOffset)}${row.ends ? ', tady končí' : ''}`}
              ></a>
              <LinePlate number={row.line.number} color={row.line.color} />
              <div class="dest">
                <strong><span class="arrow" aria-hidden="true">← </span>{row.from}</strong>
                {#if row.ends || row.dayOffset === 1}
                  <span class="tags" aria-hidden="true">
                    {#if row.ends}<span class="tag end">⏹ tady končí</span>{/if}
                    {#if row.dayOffset === 1}<span class="tag night">☾ po půlnoci</span>{/if}
                  </span>
                {/if}
              </div>
              <span class="dep" aria-hidden="true">
                <span class="dep-label">příjezd</span>
                <span class="time tabular">{row.time}</span>
              </span>
              <span class="in" aria-hidden="true"></span>
            </li>
          {/each}
        </ol>
      {/if}
    {:else if departures.length === 0}
      <p class="empty">
        Odsud žádný spoj neodjíždí: linky tu jen končí.
        {#if endingLines.length > 0}
          <button type="button" class="retry" onclick={() => (mode = 'arrivals')}
            >Zobrazit příjezdy</button
          >
        {/if}
      </p>
    {:else if target !== null && trips.error}
      <p class="empty">
        Spoje do zastávky {targetName} se nepodařilo načíst.
        <button type="button" class="retry" onclick={trips.retry}>Zkusit znovu</button>
        <button type="button" class="retry" onclick={() => (target = null)}>Zrušit filtr</button>
      </p>
    {:else if target !== null && !trips.data}
      <p class="empty" role="status">Hledáme spoje do zastávky {targetName}…</p>
    {:else if rows.length === 0}
      <p class="empty">
        Žádný spoj neodpovídá filtru.
        <button
          type="button"
          class="retry"
          onclick={() => ((lineFilter = []), (target = null), (limit = PAGE))}>Zrušit filtry</button
        >
      </p>
    {:else}
      {#if onlyLine && onlyRoute}
        <a class="line-link" href={lineDirectionHref(onlyLine.id, onlyRoute, stopId)}
          >Celý jízdní řád linky {onlyLine.number} →</a
        >
      {/if}
      {#if rows.every((r) => r.tomorrow)}
        <p class="today-done">
          Dnes už odsud nic nejede · další odjezd zítra {rows[0].departure_time}
        </p>
      {/if}
      <div class="legend" aria-hidden="true">
        <span>Linka</span><span>Směr</span><span class="r"
          >{target !== null ? `V cíli` : 'Odjezd'}</span
        ><span class="r">{mode === 'upcoming' && nextRow ? 'Za' : ''}</span>
      </div>
      {#if target !== null}
        <!-- On a phone the column legend is gone; say what the big time means. -->
        <p class="target-note">
          Velký čas = příjezd do zastávky <strong>{targetName}</strong>, pod ním odjezd odsud.
        </p>
      {/if}
      <!-- Not a live region: rows only reorder once a minute and the hero above announces the
           next departure. -->
      {#key filterKey}
        <ol class="rows" aria-label={`Odjezdy ze zastávky ${stopName}`}>
          {#each rows as row, index (row.key)}
            {@const waits = row.dep - row.arr}
            {@const soon = relative(row.inMinutes)}
            {@const by = overtaken.get(row.key)}
            {@const band =
              index === firstTomorrow && index > 0
                ? `'zítra · ${tomorrowDate}'`
                : index === nowIndex && index > 0
                  ? `'teď · ${clock(now)}'`
                  : ''}
            <li
              class="row"
              class:next={row === nextRow}
              class:later={row.tomorrow}
              class:gone={row.gone}
              class:banded={band !== ''}
              class:now-band={index === nowIndex && index > 0}
              style:--band={band}
              style:--i={index}
              animate:flip={{ duration: motionMs(320) }}
            >
              <a class="hit" href={tripHref(row.trip_id)} aria-label={rowLabel(row, by)}></a>
              <LinePlate number={row.line.number} color={row.line.color} />
              <div class="dest">
                <strong
                  ><span class="arrow" aria-hidden="true">→ </span>{row.destination.name}</strong
                >
                {#if row.via && row.via !== row.destination.name}<span class="via"
                    >přes {row.via}</span
                  >{/if}
                {#if by || row.departure_day_offset === 1}
                  <span class="tags" aria-hidden="true">
                    {#if row.departure_day_offset === 1}<span class="tag night"
                        >{row.tomorrow ? '☾ v noci' : '☾ dnes v noci, po půlnoci'}</span
                      >{/if}
                    {#if by}<span class="tag warn">odjede až po {by.join(', ')}</span>{/if}
                  </span>
                {/if}
              </div>
              <span class="dep" aria-hidden="true">
                {#if row.reach}
                  <span class="time tabular">{row.reach.time}</span>
                  <span class="arr-m tabular" style:display="block"
                    >{#if waits > 0}příjezd {row.arrival_time} ·
                    {/if}odjezd {row.departure_time}
                    · {row.reach.minutes - row.dep} min</span
                  >
                {:else}
                  <span class="dep-label">odjezd</span>
                  <span class="time tabular"
                    >{#if row.tomorrow}<span class="day">zítra</span>{/if}{row.departure_time}</span
                  >
                  <!-- Every row says when the bus pulls in, so arrival and departure read the same
                       way whether it waits or not (diary #6, #8). -->
                  <span class="arr-m tabular" class:dwell={waits > 0}
                    >přijede {row.arrival_time}{#if row.arrival_day_offset > row.departure_day_offset}&nbsp;+1{/if}{#if waits > 0}
                      · čeká {waits} min{/if}</span
                  >
                {/if}
              </span>
              <span class="in" aria-hidden="true">
                {#if !row.tomorrow && soon && mode === 'upcoming' && index < COUNTDOWNS}
                  <span class="countdown tabular" class:first={row === nextRow}>{soon}</span>
                {/if}
              </span>
            </li>
          {/each}
        </ol>
      {/key}
      {#if more > 0}
        <button type="button" class="more" onclick={() => (limit += PAGE)}>
          Zobrazit další ({more})
        </button>
      {/if}
    {/if}
  </div>
</section>

<style>
  .board-wrap {
    margin-top: 2.5rem;
  }
  /* Always dark, like the platform display it imitates, in both themes. */
  .board {
    position: relative;
    overflow: hidden;
    padding: 1.25rem;
    border-radius: var(--radius-2xl);
    color: var(--board-fg);
    box-shadow: var(--shadow-raised);
  }
  .board-head {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 1rem;
    padding-bottom: 1rem;
    border-bottom: 1px dashed var(--board-line);
  }
  .tag.night {
    background: var(--board-line);
    color: var(--board-fg);
  }
  .board-title {
    font-weight: var(--font-display-weight);
  }
  .board-sub {
    margin-top: 0.125rem;
    color: var(--board-muted);
    font-size: var(--fs-sm);
  }
  .clock-label {
    color: var(--board-fg);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .dep-label {
    display: block;
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  h2 {
    font-size: var(--fs-h2);
    line-height: 1.1;
  }
  .clock {
    display: flex;
    align-items: center;
    gap: 0.625rem;
  }
  .controls {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
    margin-top: 1rem;
  }
  .segmented {
    display: inline-flex;
    padding: 3px;
    border-radius: var(--radius-pill);
    background: var(--board-raised);
  }
  .segmented button {
    min-height: 2.5rem;
    padding-inline: 1rem;
    border-radius: var(--radius-pill);
    color: var(--board-muted);
    font-weight: var(--weight-strong);
    transition:
      background-color var(--motion-fast) var(--ease-out),
      color var(--motion-fast) var(--ease-out);
  }
  .segmented button[aria-pressed='true'] {
    background: var(--board-fg);
    color: var(--board-bg);
  }
  .chips {
    display: flex;
    align-items: center;
    gap: 0.375rem;
  }
  .chips-label {
    margin-right: 0.25rem;
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .chip {
    position: relative;
  }
  /* Selected chips get a check mark and a solid ring, not just more opacity. */
  .chip-mark {
    position: absolute;
    top: -0.375rem;
    right: -0.375rem;
    display: grid;
    place-items: center;
    width: 1.125rem;
    height: 1.125rem;
    border-radius: var(--radius-pill);
    font-size: var(--fs-2xs);
    font-weight: var(--font-display-weight);
  }
  .chip[aria-pressed='true'] .chip-mark {
    background: var(--sign-marker);
    color: var(--sign-marker-foreground);
    box-shadow: 0 0 0 2px var(--board-bg);
  }
  .chip {
    display: grid;
    place-items: center;
    width: 2.75rem;
    height: 2.75rem;
    border-radius: var(--radius-xl);
    border: 1.5px dashed var(--board-line);
    opacity: 0.7;
    transition:
      opacity var(--motion-fast) var(--ease-out),
      transform var(--motion-fast) var(--ease-out),
      border-color var(--motion-fast) var(--ease-out);
  }
  .chip:hover {
    opacity: 0.85;
  }
  .chip:active {
    transform: scale(0.92);
  }
  .chip[aria-pressed='true'] {
    opacity: 1;
    border: 2px solid var(--sign-marker);
  }
  .legend,
  .row {
    display: grid;
    grid-template-columns: 2.75rem minmax(0, 1fr) minmax(5.5rem, auto) 7.5rem;
    align-items: center;
    gap: 0.875rem;
  }
  .legend {
    margin-top: 1.25rem;
    padding-inline: 0.75rem;
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .r {
    text-align: right;
  }
  .rows {
    margin-top: 0.5rem;
  }
  .row {
    position: relative;
    min-height: 4.25rem;
    padding: 0.625rem 0.75rem;
    border-radius: var(--radius-lg);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .row + .row {
    box-shadow: 0 -1px 0 var(--board-line);
  }
  .row:hover {
    background: var(--board-raised);
  }
  .row.next {
    background: linear-gradient(90deg, var(--board-marker-glow), transparent 70%);
  }
  .row.next::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0.75rem;
    bottom: 0.75rem;
    width: 3px;
    border-radius: var(--radius-sm);
    background: var(--sign-marker);
  }
  /* Tomorrow: text steps back, plates keep their colour. */
  .row.later .dest strong,
  .row.later .dep,
  /* Full day: buses that already left step back. */
  .row.gone .dest strong,
  .row.gone .dep,
  .time {
    color: var(--board-led);
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  .in {
    text-align: right;
  }
  .line-link {
    display: inline-flex;
    align-items: center;
    min-height: 2.75rem;
    margin-top: 0.75rem;
    color: var(--sign-marker);
    font-weight: var(--weight-strong);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  /* Rows flap in, staggered, when the filter changes: the board re-sorting itself. */
  .rows .row {
    animation: flap-in var(--motion-slow) var(--ease-out) both;
    animation-delay: calc(min(var(--i), 10) * var(--motion-stagger));
    transform-origin: top;
  }
  @keyframes flap-in {
    from {
      opacity: 0;
      transform: perspective(600px) rotateX(-70deg);
    }
  }
  .target-note {
    margin-top: 0.75rem;
    padding: 0.5rem 0.75rem;
    border-radius: var(--radius-lg);
    background: var(--board-raised);
    color: var(--board-fg);
    font-size: var(--fs-sm);
  }
  .more {
    width: 100%;
    min-height: 2.75rem;
    margin-top: 0.75rem;
    border: 1.5px solid var(--board-line);
    border-radius: var(--radius-lg);
    color: var(--board-fg);
    font-weight: var(--weight-strong);
  }
  .more:hover {
    background: var(--board-raised);
  }
  .hit {
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
  }
  .hit:focus-visible {
    outline: 2px solid var(--sign-marker);
    outline-offset: -2px;
  }
  .dest {
    display: grid;
    gap: 0.25rem;
    min-width: 0;
  }
  /* Wraps instead of truncating: the destination must always be readable in full. */
  .dest strong {
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    line-height: 1.2;
    text-transform: uppercase;
    overflow-wrap: anywhere;
  }
  .dest .arrow {
    color: var(--board-muted);
    letter-spacing: 0;
  }
  .tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
  }
  .tag {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-pill);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
  }
  .warn {
    background: var(--board-warn-bg);
    color: var(--board-fg);
    box-shadow: inset 0 0 0 1px var(--sign-marker);
  }
  .dep {
    display: grid;
    justify-items: end;
    gap: 0.25rem;
  }
  .arr-m {
    display: block;
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    white-space: nowrap;
  }
  .arr-m.dwell {
    color: var(--board-fg);
  }
  .tag.end {
    background: var(--board-raised);
    color: var(--board-fg);
    box-shadow: inset 0 0 0 1px var(--board-line);
  }
  .countdown {
    color: var(--board-muted);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  /* The first row's countdown breathes gently: it is the one that matters now. */
  .countdown.first {
    color: var(--sign-marker);
    font-size: var(--fs-base);
    animation: pulse var(--loop-slow) var(--ease-in-out) infinite;
  }
  .dests {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
    width: 100%;
    max-width: 24rem;
  }
  .dests-label {
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .dests-field input {
    width: 100%;
    min-height: 2.75rem;
    padding: 0 1rem 0 2.5rem;
    border: 1.5px solid color-mix(in oklch, var(--board-fg) 55%, transparent);
    border-radius: var(--radius-pill);
    background: var(--board-raised)
      url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23fff' stroke-width='2.5' stroke-linecap='round'%3E%3Ccircle cx='11' cy='11' r='7'/%3E%3Cpath d='m20 20-3.5-3.5'/%3E%3C/svg%3E")
      no-repeat 0.9rem center;
    color: var(--board-fg);
    font: inherit;
    font-weight: var(--weight-strong);
  }
  .dests-field input::placeholder {
    color: var(--board-muted);
    font-weight: normal;
  }
  .dests-field input:hover {
    border-color: var(--board-fg);
  }
  .dests-field input:focus-visible {
    outline: 2px solid var(--sign-marker);
    outline-offset: 2px;
  }
  .dests-hint {
    color: var(--board-muted);
    font-size: var(--fs-xs);
  }
  .time .day {
    margin-right: 0.35em;
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    text-transform: uppercase;
    letter-spacing: var(--tracking-eyebrow);
    vertical-align: 0.25em;
  }
  @media (max-width: 639px) {
    .time .day {
      display: block;
      margin: 0;
      line-height: 1;
    }
  }
  .hero {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 1rem;
    margin-top: 1rem;
    padding: 1rem;
    border-radius: var(--radius-lg);
    background: var(--board-raised);
  }
  .hero-dest {
    display: grid;
    min-width: 0;
  }
  .hero-label {
    color: var(--sign-marker);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .hero-dest strong {
    overflow: hidden;
    font-size: var(--fs-lg);
    letter-spacing: var(--tracking-eyebrow);
    text-overflow: ellipsis;
    text-transform: uppercase;
    white-space: nowrap;
  }
  .hero-time {
    color: var(--board-led);
    font-size: var(--fs-h2);
    font-weight: var(--font-display-weight);
    font-variant-numeric: tabular-nums;
  }
  /* The one ambient loop on this screen: the bus is leaving. */
  .hero.leaving .hero-time {
    animation: pulse var(--loop-pulse) var(--ease-in-out) infinite;
  }
  .row.banded {
    margin-top: 2.25rem;
  }
  .row.banded::after {
    content: var(--band);
    position: absolute;
    left: 0;
    right: 0;
    top: -1.75rem;
    display: flex;
    align-items: center;
    gap: 0.75rem;
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
    padding: 0.25rem 0.75rem;
    border-radius: var(--radius-sm);
    background: var(--board-raised);
    color: var(--board-fg);
  }
  .row.now-band::after {
    background: var(--sign-marker);
    color: var(--sign-marker-foreground);
    box-shadow: 0 0 12px var(--board-marker-glow);
  }
  .skeleton {
    background: linear-gradient(
        90deg,
        var(--board-skeleton) 0%,
        var(--board-skeleton-hi) 50%,
        var(--board-skeleton) 100%
      )
      0 0 / 200% 100%;
    animation: shimmer var(--loop-pulse) linear infinite;
  }
  .today-done {
    margin-top: 1.25rem;
    padding: 0.625rem 0.75rem;
    border-radius: var(--radius-lg);
    background: var(--board-raised);
    color: var(--board-fg);
    font-weight: var(--weight-strong);
  }
  .via {
    color: var(--board-muted);
    font-size: var(--fs-sm);
  }
  .clock-time {
    color: var(--board-led);
    font-size: var(--fs-h2);
    font-weight: var(--font-display-weight);
    line-height: 1;
    white-space: nowrap;
  }
  .row.later {
    opacity: 0.7;
  }
  .empty {
    padding: 2rem 0.75rem 0.75rem;
    color: var(--board-muted);
  }
  .retry {
    min-height: 2.75rem;
    margin-left: 0.25rem;
    color: var(--sign-marker);
    font-weight: var(--weight-strong);
    text-decoration: underline;
  }
  button:focus-visible {
    outline: 2px solid var(--sign-marker);
    outline-offset: 2px;
  }
  @keyframes pulse {
    50% {
      opacity: 0.35;
    }
  }
  @keyframes shimmer {
    to {
      background-position: -200% 0;
    }
  }
  @media (min-width: 640px) {
    .dep-label {
      display: none;
    }
  }
  @media (max-width: 639px) {
    .board-wrap {
      /* Full-bleed on phones, clear of the fixed bottom tab bar. */
      margin-inline: calc(50% - 50vw);
      margin-bottom: 5rem;
    }
    .board {
      padding: 1rem 0.75rem;
      border-radius: 0;
    }
    .legend {
      display: none;
    }
    .dest strong {
      font-size: var(--fs-base);
      letter-spacing: 0.02em;
    }
    .row {
      grid-template-columns: 2.5rem minmax(0, 1fr) 4.75rem;
      gap: 0.25rem 0.625rem;
      padding-inline: 0.5rem;
    }
    .in {
      grid-column: 3;
    }
    .in:empty {
      display: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .countdown.first,
    .hero.leaving .hero-time,
    .skeleton,
    .rows .row {
      animation: none;
    }
  }
</style>
