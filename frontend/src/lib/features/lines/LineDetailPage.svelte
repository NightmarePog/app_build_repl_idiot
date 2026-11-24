<script lang="ts">
  import BackLink from '$lib/components/BackLink.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { resolve } from '$app/paths';
  import { linesHref } from '$lib/links';
  import { page } from '$app/state';
  import { ArrowRight, BusFront, ChevronRight, Clock, MapPin, TramFront } from '@lucide/svelte';
  import { getLine, type LineDetail, type Route } from '$lib/api/generated';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { goto } from '$app/navigation';
  import { LINES, busAt, lineDetail, linePath, linesOf } from '$lib/features/network/lines.svelte';
  import { focusMap } from '$lib/map-focus.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import Isa from './Isa.svelte';
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import LineTrips from '$lib/features/timetable/LineTrips.svelte';
  import BusIllustration from '$lib/features/network/BusIllustration.svelte';
  import { clock } from '$lib/features/timetable/clock.svelte';
  import { createLineTimetable } from '$lib/features/timetable/line-timetable.svelte';
  import { median, travelMinutes } from '$lib/features/timetable/line-trips';
  import { duration, minutesOf } from '$lib/features/timetable/time';
  import { tripPosition } from '$lib/features/timetable/trip-position';
  import { inkFor, isPale, typeNoun } from './line-style';

  let { id }: { id: string } = $props();

  let line = $state<LineDetail | null>(null);
  let failure = $state<'missing' | 'unavailable' | null>(null);
  let attempt = $state(0);

  $effect(() => {
    const lineId = Number(id);
    void attempt;
    // Known from the network load: show it now, the request below refreshes it.
    line = lineDetail(lineId);
    failure = null;
    if (!Number.isSafeInteger(lineId) || lineId < 1) {
      failure = 'missing';
      return;
    }
    const controller = new AbortController();
    (async () => {
      try {
        const { data, response } = await getLine({
          path: { id: lineId },
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)])
        });
        if (controller.signal.aborted) return;
        if (response?.status === 404) failure = 'missing';
        else if (data) line = data;
        else failure = 'unavailable';
      } catch {
        if (!controller.signal.aborted) failure = 'unavailable';
      }
    })();
    return () => controller.abort();
  });

  // Step-free access comes from the stops API; the route only carries names and ids.
  $effect(() => {
    void loadStops();
  });
  const stepFree = $derived(
    new Map((stopsList() ?? []).map((stop) => [stop.id, stop.wheelchair_accessible]))
  );

  const asked = $derived(page.url.searchParams.get('smer'));
  const timetable = createLineTimetable(() => Number(id));
  /**
   * Direction from the URL; without one, the first direction that has trips, so the page never
   * opens on an empty timetable when the other direction runs.
   */
  const route = $derived.by<Route | undefined>(() => {
    if (!line) return undefined;
    const pick = line.routes.find((r) => r.bound === asked);
    if (pick) return pick;
    const served = timetable.trips
      ? line.routes.find((r) => timetable.trips!.some((trip) => trip.route_id === r.id))
      : undefined;
    return served ?? line.routes.find((r) => r.bound === 'outbound') ?? line.routes[0];
  });

  // Trips of the line: drive the trips sheet, the per-direction travel time on the tabs and the
  // minutes between stops on the diagram. One request set per line, shared by all three.
  $effect(() => clock.subscribe());

  /** Typical end-to-end minutes per route, shown on the direction tabs (C differs by direction). */
  const travelByRoute = $derived(
    new Map(
      (line?.routes ?? []).map((r) => [
        r.id,
        median(
          (timetable.trips ?? [])
            .filter((trip) => trip.route_id === r.id)
            .flatMap((trip) => {
              const m = travelMinutes(timetable.details.get(trip.id)?.stops ?? []);
              return m === null ? [] : [m];
            })
        )
      ])
    )
  );

  const routeDetails = $derived(
    (timetable.trips ?? [])
      .filter((trip) => trip.route_id === route?.id)
      .flatMap((trip) => {
        const detail = timetable.details.get(trip.id);
        return detail ? [detail] : [];
      })
  );

  /** Minutes from the first stop to each stop (its departure, arrival at the terminus). */
  const cumulative = $derived.by(() => {
    const stops = routeDetails[0]?.stops ?? [];
    const start = stops[0] ? minutesOf(stops[0].departure_time, stops[0].departure_day_offset) : 0;
    return new Map(
      stops.map((s) => [s.stop.id, minutesOf(s.arrival_time, s.arrival_day_offset) - start])
    );
  });

  /** A bus of this direction under way right now, as a fractional stop index on the diagram. */
  const bus = $derived.by(() => {
    for (const detail of routeDetails) {
      const calls = detail.stops.map((s) => ({
        arr: minutesOf(s.arrival_time, s.arrival_day_offset),
        dep: minutesOf(s.departure_time, s.departure_day_offset)
      }));
      const position = tripPosition(calls, clock.now);
      if (!position) continue;
      const stopIdAt = detail.stops[position.index]?.stop.id;
      const index = route?.stops.findIndex((s) => s.id === stopIdAt) ?? -1;
      if (index < 0) continue;
      return {
        index,
        share: position.kind === 'between' ? position.share : 0,
        tripId: detail.id
      };
    }
    return null;
  });

  const routeNames = $derived(route?.stops.map((s) => s.name) ?? []);
  const mapPaths = $derived(
    line && route ? [{ coords: linePath(line.number, routeNames), color: line.color }] : []
  );
  const mapBus = $derived(
    line && bus
      ? {
          at: busAt(line.number, routeNames, {
            kind: bus.share ? 'between' : 'at',
            index: bus.index,
            share: bus.share
          }),
          color: line.color,
          label: `Spoj linky ${line.number} na trase`
        }
      : null
  );

  /** Boarding stop picked in the timetable, highlighted on the route so the panels read as one. */
  let boardStop = $state<number | null>(null);

  function pickStop(stopId: number) {
    boardStop = stopId;
    // The timetable sits below the route: bring it into view so the tap visibly does something.
    document.getElementById('schedule-heading')?.scrollIntoView({ block: 'start' });
  }

  // The map page's big map draws the route and the bus under way on it.
  $effect(() => {
    if (!line || !route) return;
    return focusMap({
      stops: stopsList() ?? [],
      focusLines: [line.number],
      focusStops: route.stops.map((s) => s.id),
      paths: mapPaths,
      bus: mapBus?.at ? { ...mapBus, at: mapBus.at } : null,
      fit: mapPaths[0]?.coords ?? null,
      selected: boardStop,
      onselect: (stop) => goto(resolve('/(map)/stops/[id]', { id: String(stop.id) }))
    });
  });

  /** Other lines at the stop, linked straight to that stop on their own diagram. */
  function transfersAt(stopName: string) {
    return linesOf(stopName)
      .filter((number) => number !== line?.number)
      .flatMap((number) => (LINES[number] ? [LINES[number]] : []));
  }
</script>

<svelte:head>
  <title>
    {failure === 'missing'
      ? 'Linka nenalezena'
      : line
        ? `${line.number} → ${route ? route.direction : line.name}`
        : 'Linka'} · TdA
  </title>
</svelte:head>

{#if failure}
  <div class="state">
    <PageState
      kind="Linka"
      title={failure === 'missing' ? 'Linka nebyla nalezena' : 'Linku se nepodařilo načíst'}
      headingLevel={1}
      error={failure === 'unavailable'}
      onretry={failure === 'unavailable' ? () => attempt++ : undefined}
      href={failure === 'missing' ? linesHref() : undefined}
      linkLabel="Přehled linek"
    />
  </div>
{:else if !line}
  <LoadingState label="Načítáme detail linky…" detail />
{:else}
  <BackLink href={linesHref()} label="Všechny linky" />
  <article
    class="line"
    class:line-cased={isPale(line.color)}
    style:--line={line.color}
    style:--line-ink={inkFor(line.color)}
  >
    <div class="head">
      <PageHeader
        kind={`Linka · ${typeNoun(line.type)}`}
        icon={line.type.toLowerCase() === 'tram' ? TramFront : BusFront}
        title={line.name}
        color={line.color}
      >
        {#snippet lead()}<LinePlate
            number={line?.number ?? ''}
            color={line?.color ?? ''}
            size="lg"
          />{/snippet}
      </PageHeader>
    </div>

    {#if !route || route.stops.length === 0}
      <PageState title="Trasa linky zatím není zadaná" />
    {:else}
      <!-- One direction switch drives the whole page: the route and the timetable below. -->
      <div class="direction-bar">
        {#if line.routes.length > 1}
          <span class="dir-caption" aria-hidden="true">Směr</span>
          <nav class="directions" aria-label="Směr jízdy">
            {#each line.routes as option (option.id)}
              <a
                href={option.bound === 'inbound'
                  ? resolve('/(map)/lines/[id]?smer=inbound', { id })
                  : resolve('/(map)/lines/[id]?smer=outbound', { id })}
                aria-current={option.id === route.id ? 'page' : undefined}
                data-sveltekit-replacestate
                data-sveltekit-noscroll
              >
                <span class="dir-name">
                  <span class="dir-from">{option.stops[0]?.name ?? ''}</span>
                  <ArrowRight size={18} aria-hidden="true" />
                  <span>{option.direction}</span>
                </span>
                {#if travelByRoute.get(option.id) != null}
                  <span class="dir-label tabular"
                    ><Clock size={13} aria-hidden="true" />
                    {duration(travelByRoute.get(option.id)!)}</span
                  >
                {/if}
              </a>
            {/each}
          </nav>
        {:else}
          <p class="single-direction dir-name">
            <span class="dir-from">{route.stops[0]?.name}</span>
            <ArrowRight size={18} aria-hidden="true" />
            <strong>{route.direction}</strong>
          </p>
        {/if}
        <p class="sr-only" aria-live="polite">Směr {route.direction}</p>
      </div>

      <div class="line-body">
        <section class="route" aria-labelledby="route-heading">
          <!-- Infobox, the same pattern as the stop and trip pages: the facts of the line. -->
          <dl class="infobox">
            <div>
              <dt>Typ</dt>
              <dd>{typeNoun(line.type)}</dd>
            </div>
            <div>
              <dt>Zastávek</dt>
              <dd class="tabular">{route.stops.length}</dd>
            </div>
            {#if route.stops.some((s) => stepFree.get(s.id) === false)}
              <div class="legend">
                <dt><Isa size={18} off /></dt>
                <dd>bez bezbariérového nástupu</dd>
              </div>
            {/if}
          </dl>
          <div class="route-head">
            <h2 id="route-heading" class="heading">Trasa linky</h2>
            <span class="leg-head"><Clock size={16} aria-hidden="true" />Jízdní doba</span>
          </div>
          <ol class="diagram" aria-label={`Zastávky směr ${route.direction}`}>
            {#each route.stops as stop, index (stop.id)}
              {@const others = transfersAt(stop.name)}
              {@const first = index === 0}
              {@const last = index === route.stops.length - 1}
              {@const leg = cumulative.get(stop.id)}
              {@const picked = !last && boardStop === stop.id}
              <li
                id={`stop-${stop.id}`}
                class:terminus={first || last}
                class:interchange={others.length > 0}
                class:boarding={picked}
              >
                <span class="dot" aria-hidden="true"></span>
                {#if bus && bus.index === index}
                  <a
                    class="bus"
                    href={resolve('/(map)/trips/[id]', { id: String(bus.tripId) })}
                    style:--share={bus.share}
                    aria-label="Spoj, který teď jede v tomto směru"
                    ><BusIllustration stripe={line.color} /></a
                  >
                {/if}
                {#if !last}
                  <!-- The whole row picks the boarding stop for the timetable. -->
                  <button
                    type="button"
                    class="pick"
                    aria-pressed={picked}
                    title={`Zobrazit odjezdy ze zastávky ${stop.name}`}
                    aria-label={`Odjezdy ze zastávky ${stop.name}`}
                    onclick={() => pickStop(stop.id)}
                  ></button>
                {/if}
                <div class="stop">
                  <span class="stop-main">
                    <a class="name" href={resolve('/(map)/stops/[id]', { id: String(stop.id) })}
                      >{stop.name}<ChevronRight size={16} aria-hidden="true" /></a
                    >
                    {#if first || last}<span class="sr-only">konečná</span>{/if}
                    {#if others.length}
                      <span class="transfers">
                        {#each others as other (other.number)}
                          <a
                            class="badge tabular"
                            href={resolve(`/(map)/lines/[id]#stop-${stop.id}`, {
                              id: String(other.apiId)
                            })}
                            style:background={other.color}
                            style:color={other.ink}
                            title={`Přestup na linku ${other.number}`}
                            ><span aria-hidden="true">{other.number}</span><span class="sr-only"
                              >přestup na linku {other.number}</span
                            ></a
                          >
                        {/each}
                      </span>
                    {/if}
                    {#if picked}<span class="board-mark"
                        ><MapPin size={14} aria-hidden="true" />zvolená zastávka</span
                      >{/if}
                    {#if stepFree.get(stop.id) === false}<Isa
                        size={18}
                        off
                        label="bez bezbariérového nástupu"
                      />{/if}
                  </span>
                  <span class="leg tabular"
                    >{#if first}0 min{:else if leg !== undefined}{leg} min<span class="sr-only">
                        od zastávky {route.stops[0]?.name}</span
                      >{/if}</span
                  >
                </div>
              </li>
            {/each}
          </ol>
        </section>

        <section class="schedule" aria-labelledby="schedule-heading">
          <h2 id="schedule-heading" class="heading">Jízdní řád</h2>
          <!-- The direction switch above drives this sheet too: one direction at a time. -->
          {#key route.id}
            <LineTrips
              {timetable}
              {route}
              lineNumber={line.number}
              lineColor={line.color}
              bind:stopId={boardStop}
            />
          {/key}
        </section>
      </div>
    {/if}
  </article>
{/if}

<style>
  .heading {
    margin-block: 1.5rem 0.75rem;
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
  .state {
    margin-top: 1rem;
  }

  .direction-bar {
    display: flex;
    align-items: stretch;
    gap: 0.5rem;
    margin-top: 1rem;
    max-width: 48rem;
  }
  /* Segmented control: both directions as "from → to", the active one in the line colour. */
  .directions {
    flex: 1;
    display: grid;
    grid-auto-columns: minmax(0, 1fr);
    grid-auto-flow: column;
    gap: 0.25rem;
    padding: 0.25rem;
    border-radius: var(--radius);
    background: var(--muted);
  }
  .directions a {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 0.125rem;
    min-height: 3.25rem;
    padding: 0.375rem 0.875rem;
    border-radius: var(--radius);
    color: var(--foreground);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .directions a:not([aria-current='page']):hover {
    background: var(--accent);
  }
  /* Same segmented look as the other toggles: the pressed option is the solid one. */
  .directions a[aria-current='page'] {
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
  }
  .dir-caption {
    align-self: center;
    font-weight: var(--weight-strong);
  }
  .infobox {
    display: grid;
    gap: 0.5rem;
    max-width: 44rem;
    margin-top: 1.5rem;
    padding: 1rem 1.25rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
  }
  .infobox div {
    display: grid;
    grid-template-columns: 6rem minmax(0, 1fr);
    align-items: center;
    gap: 0.75rem;
  }
  .infobox dt {
    font-weight: var(--weight-strong);
  }
  .infobox .legend dt {
    display: flex;
  }
  .dir-name {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.125rem 0.375rem;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    line-height: 1.2;
  }
  .dir-from {
    font-weight: var(--weight-regular);
  }
  .dir-label {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    font-size: var(--fs-sm);
    opacity: 0.85;
  }
  .directions a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }

  /* Route and timetable side by side on wide screens, stacked on phones. */
  .line-body {
    display: grid;
    gap: 0 3rem;
  }
  @container (min-width: 64rem) {
    .line-body {
      grid-template-columns: minmax(0, 4fr) minmax(0, 7fr);
      align-items: start;
    }
  }

  /*
   * Vertical line diagram, as on a PID line timetable. Columns: rail with the stop symbol,
   * then the stop. The rail runs through every row and stops at the termini.
   */
  .diagram {
    max-width: 44rem;
    margin-top: 0.25rem;
  }
  .diagram li {
    position: relative;
    display: grid;
    grid-template-columns: 2rem minmax(0, 1fr);
    align-items: start;
    column-gap: 0.75rem;
    min-height: 3.5rem;
    scroll-margin-top: calc(var(--header-h) + 2rem);
  }
  /* One solid line in the line colour, from the first terminus to the last. */
  .diagram li::before {
    content: '';
    position: absolute;
    left: calc(1rem - 0.3125rem);
    top: 0;
    bottom: 0;
    width: 0.625rem;
    background: var(--line);
    box-shadow:
      inset 1.5px 0 0 var(--line-stroke, var(--line)),
      inset -1.5px 0 0 var(--line-stroke, var(--line));
  }
  .diagram li:first-child::before {
    top: 1.625rem;
  }
  .diagram li:last-child::before {
    bottom: auto;
    height: 1.625rem;
  }
  /* Arriving from a transfer link: show which stop was meant. */
  .diagram li:target {
    background: linear-gradient(to right, transparent 2.5rem, var(--accent) 2.5rem);
  }
  /* Interchange: a bigger white ring, as on PID and TfL maps; the connecting badges follow. */
  .interchange .dot {
    border-color: var(--foreground);
  }
  /* Plain stop: a small white dot inside the line. */
  .dot {
    position: relative;
    z-index: 1;
    justify-self: center;
    margin-top: calc(1.625rem - 0.4375rem);
    width: 0.875rem;
    height: 0.875rem;
    border: 3px solid var(--line-stroke, var(--line));
    border-radius: 50%;
    background: var(--background);
  }
  /* One stop symbol everywhere; termini and transfers are said by the name row instead. */
  .route-head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 0 1rem;
    max-width: 44rem;
  }
  .leg-head {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    align-self: center;
    font-weight: var(--weight-strong);
  }
  #schedule-heading {
    scroll-margin-top: calc(var(--header-h, 4rem) + 1rem);
  }
  /* Row-wide button that picks the boarding stop; the links sit above it. */
  .pick {
    position: absolute;
    inset: 0;
    z-index: 0;
    border-radius: var(--radius);
    cursor: pointer;
  }
  .diagram li:hover:not(.boarding) {
    background: linear-gradient(
      to right,
      transparent 2.5rem,
      color-mix(in oklch, var(--accent) 60%, transparent) 2.5rem
    );
  }
  .pick:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -2px;
  }
  .stop {
    position: relative;
    pointer-events: none;
  }
  .stop a {
    pointer-events: auto;
  }
  /* Name row on the left, travel minutes pinned right on the same row, even on phones. */
  .stop {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: start;
    gap: 0.125rem 0.75rem;
    padding-block: 0.5rem;
  }
  .stop-main {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.5rem;
  }
  .name {
    padding-block: 0.25rem;
    font-size: var(--fs-lg);
    font-weight: var(--weight-regular);
    text-underline-offset: 3px;
  }
  .terminus .name {
    font-weight: var(--weight-strong);
  }
  .name:hover {
    text-decoration: underline;
  }
  .badge {
    position: relative;
    display: inline-grid;
    place-items: center;
    min-width: 1.5rem;
    height: 1.5rem;
    padding-inline: 0.375rem;
    border-radius: var(--radius);
    font-size: var(--fs-sm);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  /* 44px hit area around the small badge. */
  .badge:hover {
    text-decoration: underline;
    text-underline-offset: 2px;
    outline: 2px solid var(--foreground);
    outline-offset: 1px;
  }
  .badge::after {
    content: '';
    position: absolute;
    inset: -0.5rem;
  }
  /* Minutes from the first stop, at the end of the name row so they read with it. */
  .leg {
    padding-top: 0.375rem;
    white-space: nowrap;
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  /* Every stop gets the same node; termini and transfers are said in words next to the name. */
  /* Stop picked in the timetable. */
  .board-mark {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.125rem 0.5rem;
    border: 1px solid var(--foreground);
    border-radius: var(--radius);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .transfers {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
  }
  .name {
    display: inline-flex;
    align-items: center;
    gap: 0.125rem;
  }
  .line {
    padding-bottom: calc(5rem + env(safe-area-inset-bottom, 0px));
  }
  @container (min-width: 64rem) {
    .line {
      padding-bottom: 0;
    }
  }
  .boarding .name {
    font-weight: var(--weight-strong);
  }
  .bus {
    position: absolute;
    z-index: 2;
    left: -0.25rem;
    top: calc(1.625rem + var(--share) * 100%);
    width: 2.5rem;
    transform: translateY(-50%);
    transition: top var(--motion-xslow) var(--ease-out);
    filter: drop-shadow(0 2px 4px var(--board-shadow));
  }
  .diagram a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: var(--radius);
  }

  @container (max-width: 639px) {
    .directions {
      grid-auto-flow: row;
    }
    .dir-name {
      font-size: var(--fs-base);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .directions a {
      transition: none !important;
    }
  }
</style>
