<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import {
    ArrowRight,
    CalendarDays,
    ChevronRight,
    Clock,
    LogIn,
    LogOut,
    Moon,
    Timer as TimerIcon,
    BusFront as TripIcon
  } from '@lucide/svelte';
  import BackLink from '$lib/components/BackLink.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { listLineTrips, type TripDetail } from '$lib/api/generated';
  import { createResource, ResourceError, unwrap } from '$lib/api/resource.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { inkFor, isPale } from '$lib/features/lines/line-style';
  import { goto } from '$app/navigation';
  import { LINES, busAt, linePath, linesOf, loadNetwork } from '$lib/features/network/lines.svelte';
  import TransitMap from '$lib/features/network/TransitMap.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import LinePlate from './LinePlate.svelte';
  import { clock } from './clock.svelte';
  import { duration } from './time';
  import {
    dayRank,
    rideProgress,
    segment,
    spokenStop,
    ribbon as ribbonOf,
    timedStops,
    tripPosition,
    tripStatus
  } from './trip-position';
  import { lineDirectionHref, tripDetail } from './trips';

  let { id }: { id: string } = $props();

  /** A positive whole id from the URL, or null for anything else (1.5, -3, "abc"). */
  const idOf = (value: string | null) => {
    const n = Number(value);
    return value !== null && Number.isSafeInteger(n) && n >= 1 ? n : null;
  };

  // Same cached request as the line sheet, so opening a trip from there does not refetch it.
  const resource = createResource(
    (signal) => {
      const tripId = idOf(id);
      if (tripId === null) return Promise.reject(new ResourceError('missing'));
      return tripDetail(tripId, signal);
    },
    () => id
  );
  const trip = $derived(resource.data);
  const failure = $derived(resource.error);

  /** The line's trips, only to tell whether this is the first or last service of the day. */
  const lineTrips = createResource(
    (signal) =>
      trip
        ? unwrap(listLineTrips({ path: { lineId: trip.line.id }, signal }))
        : Promise.reject(new ResourceError('missing')),
    () => trip?.line.id
  );
  const rank = $derived(trip && lineTrips.data ? dayRank(lineTrips.data, trip) : null);

  $effect(() => clock.subscribe());
  $effect(() => {
    void loadNetwork();
  });
  const now = $derived(clock.now);

  const fromId = $derived(idOf(page.url.searchParams.get('od')));
  const toId = $derived(idOf(page.url.searchParams.get('do')));

  const stops = $derived(timedStops(trip?.stops ?? []));

  /** The passenger's part of the trip: boarding (?od=) to getting off (?do=, else the terminus). */
  const part = $derived(segment(stops, fromId, toId));
  const board = $derived(stops[part.from]);
  const alight = $derived(stops[part.to]);
  /** True when the passenger rides only part of the route, so the page talks about "your" ride. */
  const partial = $derived(part.from > 0 || part.to < stops.length - 1);

  /** Where the bus is right now, if the trip is under way. */
  const vehicle = $derived(tripPosition(stops, now));
  /** Announced only when the bus reaches another stop, not on every clock tick. */
  const vehicleStop = $derived(
    vehicle
      ? vehicle.kind === 'at'
        ? `Spoj právě stojí v zastávce ${stops[vehicle.index].stop.name}.`
        : `Spoj právě odjel ze zastávky ${stops[vehicle.index].stop.name}.`
      : ''
  );

  $effect(() => void loadStops());
  const names = $derived(stops.map((s) => s.stop.name));
  const mine = $derived(
    trip ? linePath(trip.line.number, names.slice(part.from, part.to + 1)) : []
  );
  const mapPaths = $derived(
    trip
      ? [
          ...(partial
            ? [{ coords: linePath(trip.line.number, names), color: trip.line.color, opacity: 0.35 }]
            : []),
          { coords: mine, color: trip.line.color }
        ]
      : []
  );
  const busPoint = $derived(trip ? busAt(trip.line.number, names, vehicle) : null);

  /** Share 0..1 of my ride done; null (and no bar) while I am not on the road. */
  const progress = $derived(board && alight ? rideProgress(board, alight, now) : null);

  /** What the trip is doing right now, in one sentence. */
  const status = $derived(board ? tripStatus(stops, part.from, board.departure_time, now) : null);

  /** My ride: its length and where midnight falls on it. */
  const ribbon = $derived(board && alight ? ribbonOf(board, alight) : null);

  /** Lines to change to where I get off. */
  const transfers = $derived(
    trip && alight
      ? linesOf(alight.stop.name)
          .filter((number) => number !== trip.line.number)
          .map((number) => LINES[number])
          .filter(Boolean)
      : []
  );

  /** Vehicle run name ("Bus B03") from block_ref. */
  const vehicleName = $derived(trip?.block_ref ? `Bus ${trip.block_ref}` : null);

  /** Other lines at a stop, shown as badges on its row (IDOS-style transfers). */
  const otherLines = (stopName: string) =>
    trip
      ? linesOf(stopName)
          .filter((number) => number !== trip.line.number)
          .map((number) => LINES[number])
          .filter(Boolean)
      : [];

  /** Shown time at a stop: arrival where the passenger gets off, else departure. */
  const boardTime = $derived(board?.departure_time ?? '');
  const alightTime = $derived(alight?.arrival_time ?? '');

  /** "Bus B03 · Ada Exchange 23:06 → Hopper Hall 23:10": vehicle plus my ride, always. */
  const title = $derived(
    board && alight
      ? `${vehicleName ? `${vehicleName} · ` : ''}${board.stop.name} ${boardTime} → ${alight.stop.name} ${alightTime}`
      : ''
  );

  /** The calendar day this run belongs to: today, or tomorrow once today's has gone. */
  const runDate = $derived(
    new Date(Date.now() + (status?.tone === 'done' ? 86_400_000 : 0)).toLocaleDateString('cs-CZ', {
      weekday: 'long',
      day: 'numeric',
      month: 'numeric'
    })
  );

  function lineHref(trip: TripDetail) {
    return lineDirectionHref(trip.line.id, trip.direction);
  }
</script>

<svelte:head>
  <title>
    {trip
      ? `Spoj ${trip.line.number} · ${title}`
      : failure === 'missing'
        ? 'Spoj nenalezen'
        : 'Spoj'} · TdA
  </title>
</svelte:head>

{#if failure}
  <div class="state">
    <PageHeader kind="Spoj" icon={TripIcon} title="Spoj nenalezen" />
    <PageState
      title={failure === 'missing' ? 'Spoj nebyl nalezen' : 'Spoj se nepodařilo načíst'}
      description={failure === 'missing' ? 'Spoj vyberte v jízdním řádu linky.' : undefined}
      headingLevel={2}
      error={failure === 'unavailable'}
      onretry={failure === 'unavailable' ? resource.retry : undefined}
      href={failure === 'missing' ? resolve('/lines') : undefined}
      linkLabel="Přehled linek"
    />
  </div>
{:else if !trip || !board || !alight || !ribbon}
  <LoadingState label="Načítáme jízdní řád spoje…" detail />
{:else}
  <article
    class="trip"
    class:line-cased={isPale(trip.line.color)}
    style:--line={trip.line.color}
    style:--line-ink={inkFor(trip.line.color)}
  >
    <BackLink href={lineHref(trip)} label={`Linka ${trip.line.number}`} />

    <PageHeader kind="Spoj" icon={TripIcon} {title} color={trip.line.color}>
      {#snippet meta()}
        <div class="meta-row">
          {#if status}
            <span class="chip countdown tabular" data-tone={status.tone} role="status">
              {#if status.tone === 'live'}<span class="live-dot" aria-hidden="true"
                ></span>{:else}<TimerIcon aria-hidden="true" class="size-5" />{/if}
              {#if status.detail}{status.tone === 'done' ? 'zítra · ' : ''}odjezd {status.detail}{:else}{status.title}{/if}
            </span>
          {/if}
          <span class="chip tabular" aria-label={`Vaše jízda trvá ${duration(ribbon.total)}`}
            ><Clock aria-hidden="true" class="size-4" />{duration(ribbon.total)}</span
          >
          {#if rank?.last}<span class="chip"
              ><Moon aria-hidden="true" class="size-4" />poslední spoj dne</span
            >{/if}
          <span class="chip tabular"
            ><CalendarDays aria-hidden="true" class="size-4" />{runDate}</span
          >
          {#if progress !== null}
            <span class="progress" aria-label={`Ujeto ${Math.round(progress * 100)} % vaší jízdy`}>
              <span class="progress-track">
                <span class="progress-fill" style:width={`${progress * 100}%`}></span>
              </span>
            </span>
          {/if}
        </div>
      {/snippet}
      {#snippet lead()}<span class="vt-plate"
          ><span class="sr-only">Linka {trip.line.number}</span><LinePlate
            number={trip.line.number}
            color={trip.line.color}
            size="lg"
          /></span
        >{/snippet}
    </PageHeader>

    <p class="sr-only" aria-live="polite">{vehicleStop}</p>

    <div class="trip-grid">
      <div class="main">
        <h2 class="heading" id="timetable-heading">Trasa spoje</h2>
        <ol id="trip-timeline" class="timeline" aria-labelledby="timetable-heading">
          {#each stops as stop, i (stop.stop_order)}
            {@const outside = i < part.from || i > part.to}
            {@const isFrom = i === part.from}
            {@const isTo = i === part.to}
            {@const first = i === 0}
            {@const end = i === stops.length - 1}
            {@const waits = stop.dwell > 0 && !first && !end}
            {#if stop.crossesMidnight && i > 0}
              <li class="midnight" aria-hidden="true">
                <span><Moon class="size-4" />Půlnoc</span>
              </li>
            {/if}
            <li
              class="stop"
              class:outside
              class:inside={!outside}
              class:seg-start={isFrom}
              class:seg-end={isTo}
              class:up-in={i > part.from && i <= part.to}
              class:down-in={i >= part.from && i < part.to}
              style:--minutes={i === 0 ? 0 : stop.gap}
              style:--i={i}
            >
              {#if vehicle?.kind === 'at' && vehicle.index === i}
                <span class="vehicle" aria-hidden="true" style:top="var(--vehicle-at)"></span>
              {:else if vehicle?.kind === 'between' && vehicle.index === i - 1}
                <span
                  class="vehicle"
                  aria-hidden="true"
                  style:top={`calc(${vehicle.share} * var(--gap))`}
                ></span>
              {/if}
              <!-- One time per stop: arrival where the ride or route ends, departure elsewhere. -->
              <div class="times" aria-hidden="true">
                {#if waits && !(isTo && !isFrom)}
                  <span class="t arr"><LogIn class="size-3" />{stop.arrival_time}</span>
                  <span class="t"><LogOut class="size-3" />{stop.departure_time}</span>
                {:else}
                  <span class="t"
                    >{end || (isTo && !isFrom) ? stop.arrival_time : stop.departure_time}</span
                  >
                {/if}
              </div>
              <span class="node" aria-hidden="true"></span>
              <div class="body">
                <a class="name" href={resolve('/stops/[id]', { id: String(stop.stop.id) })}
                  >{stop.stop.name}<ChevronRight
                    aria-hidden="true"
                    class="name-chevron size-4"
                  /></a
                >
                {#each otherLines(stop.stop.name) as other (other.number)}<a
                    class="stop-line"
                    href={resolve('/lines/[id]', { id: String(other.apiId) })}
                    aria-label={`Přestup na linku ${other.number}`}
                    ><LinePlate number={other.number} color={other.color} size="sm" /></a
                  >{/each}
                {#if partial && (isFrom || isTo)}
                  <span class="tags" aria-hidden="true">
                    {#if isFrom && partial}<span class="flag"><LogIn class="size-4" />Nástup</span
                      >{/if}
                    {#if isTo && partial}<span class="flag out"
                        ><LogOut class="size-4" />Výstup</span
                      >{/if}
                  </span>
                {/if}
                <span class="sr-only"
                  >{spokenStop(stop, first, end)}{isFrom && partial ? ', nástup' : ''}{isTo &&
                  partial
                    ? ', výstup'
                    : ''}</span
                >
              </div>
            </li>
          {/each}
        </ol>
      </div>

      <div class="trip-map">
        <TransitMap
          compact
          label="Mapa spoje"
          stops={stopsList() ?? []}
          focusLines={[trip.line.number]}
          focusStops={stops.slice(part.from, part.to + 1).map((s) => s.stop.id)}
          paths={mapPaths}
          marks={{ [board.stop.id]: 'board', [alight.stop.id]: 'alight' }}
          bus={busPoint
            ? { at: busPoint, color: trip.line.color, label: vehicleStop || 'Poloha spoje' }
            : null}
          fit={mine}
          legend={false}
          onselect={(stop) => goto(resolve('/stops/[id]', { id: String(stop.id) }))}
        />
      </div>
      <aside class="infobox" aria-label="Další cesta">
        <a class="change-link" href={lineHref(trip)}>
          <LinePlate number={trip.line.number} color={trip.line.color} />
          <span>Jízdní řád linky</span>
          <ArrowRight aria-hidden="true" class="size-4" />
        </a>
        {#if transfers.length}
          <section class="onward" aria-labelledby="onward-heading">
            <h3 id="onward-heading" class="heading">Přestup v {alight.stop.name}</h3>
            <ul class="change">
              {#each transfers as other (other.number)}
                <li>
                  <a href={resolve('/lines/[id]', { id: String(other.apiId) })} class="change-link">
                    <LinePlate number={other.number} color={other.color} />
                    <span>{other.name}</span>
                    <ArrowRight aria-hidden="true" class="size-4" />
                  </a>
                </li>
              {/each}
            </ul>
          </section>
        {/if}
      </aside>
    </div>
  </article>
{/if}

<style>
  .state {
    margin-top: 1rem;
  }
  .trip {
    max-width: 80rem;
    /* Room for the fixed mobile tab bar so the end of the page is never hidden under it. */
    padding-bottom: calc(var(--tabbar-h, 0px) + env(safe-area-inset-bottom) + 2rem);
  }

  .chip {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: var(--secondary);
    font-weight: var(--weight-strong);
  }
  /* Boarding and alighting share the flag shape; the icon and fill tell them apart. */
  .flag {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    justify-self: start;
    padding: 0.0625rem 0.5rem;
    border-radius: var(--radius);
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    line-height: 1.5rem;
  }

  .meta-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem;
    font-size: var(--fs-base);
  }
  .meta-row .chip {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2rem;
  }
  .progress {
    display: block;
    width: 8rem;
  }
  .progress-track {
    position: relative;
    display: block;
    height: 0.5rem;
    border-radius: var(--radius);
    background: var(--secondary);
  }
  .progress-fill {
    position: absolute;
    inset: 0 auto 0 0;
    border-radius: inherit;
    background: var(--line);
    transition: width var(--motion-xslow) var(--ease-out);
  }
  .live-dot {
    flex: none;
    width: 0.75rem;
    height: 0.75rem;
    border-radius: 50%;
    background: var(--status-ok);
    animation: ping var(--loop-pulse) var(--ease-out) infinite;
  }

  .trip-grid {
    display: grid;
    gap: 2rem;
    margin-top: 0.5rem;
  }
  @media (min-width: 960px) {
    .trip-grid {
      grid-template-columns: minmax(0, 1fr) 22rem;
      grid-template-rows: auto 1fr;
      align-items: start;
    }
    .main {
      grid-row: 1 / 3;
    }
    .trip-map {
      grid-column: 2;
      grid-row: 1;
    }
    .infobox {
      grid-column: 2;
      grid-row: 2;
      position: sticky;
      top: calc(var(--header-h, 4rem) + 1rem);
    }
  }
  @media (max-width: 959px) {
    .trip-map {
      order: -1;
      --map-ratio: 16 / 10;
    }
  }
  /* Countdown is the one thing to read first: filled in the line colour, larger. */
  .meta-row .countdown {
    min-height: 2.5rem;
    padding: 0.25rem 0.75rem;
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: var(--fs-lg);
  }
  .infobox {
    display: grid;
    gap: 1rem;
  }
  /* Boarding, alighting and a wait sit on their own line under the name, in the same spot. */
  .tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.375rem;
    flex-basis: 100%;
  }
  .heading {
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
  }

  /*
   * The route spine, drawn to time but in normal flow: each stop gets top padding for the minutes
   * since the previous one. Each row draws the rail segment above its dot, so the passenger's
   * part is solid line colour and the rest a faint rail.
   */
  .timeline {
    position: relative;
    margin-top: 1rem;
    --rail-x: 6.5rem;
    --scale: 0.42rem;
    --row-min: 0.75rem;
    --vehicle-at: calc(max(var(--row-min), var(--gap)) + 0.75rem);
  }
  .stop {
    position: relative;
    display: grid;
    grid-template-columns: calc(var(--rail-x) - 1.25rem) 2.5rem minmax(0, 1fr);
    grid-template-areas: 'times node body';
    align-items: start;
    --gap: calc(var(--minutes) * var(--scale));
    padding-top: max(var(--row-min), var(--gap));
    animation: settle var(--motion-slow) var(--ease-spring-out) both;
    animation-delay: calc(var(--i) * var(--motion-stagger));
  }
  /* Rail: ::before runs from the top of the row to its dot, ::after from the dot down. */
  .stop::before,
  .stop::after {
    content: '';
    position: absolute;
    left: calc(var(--rail-x) - 0.1875rem);
    width: 0.375rem;
    background: color-mix(in oklch, var(--line) 35%, var(--background));
  }
  .stop::before {
    top: 0;
    height: calc(max(var(--row-min), var(--gap)) + 0.75rem);
  }
  .stop::after {
    top: calc(max(var(--row-min), var(--gap)) + 0.75rem);
    bottom: 0;
  }
  .stop:first-child::before,
  .stop:last-child::after {
    display: none;
  }
  .up-in::before,
  .down-in::after {
    background: var(--line);
    box-shadow: 0 0 0 1.5px var(--line-stroke, var(--line));
  }
  /* Stops off my ride: greyed, with a dashed rail, so my part stands out at a glance. */
  .outside {
    opacity: 0.4;
    filter: grayscale(1);
  }
  .outside .name {
    font-weight: var(--weight-normal, 400);
  }
  .node {
    grid-area: node;
    justify-self: center;
    position: relative;
    z-index: 2;
    width: 1rem;
    height: 1rem;
    margin-top: 0.25rem;
    border-radius: 50%;
    border: 3px solid var(--line-stroke, var(--line));
    background: var(--background);
  }
  .inside .node {
    background: var(--line);
  }
  .seg-start .node,
  .seg-end .node {
    width: 1.375rem;
    height: 1.375rem;
    margin-top: 0.0625rem;
    border-color: var(--foreground);
    background: var(--line);
  }
  .vehicle {
    position: absolute;
    z-index: 3;
    left: calc(var(--rail-x) - 0.625rem);
    width: 1.25rem;
    height: 1.25rem;
    margin-top: -0.625rem;
    border: 3px solid var(--background);
    border-radius: 50%;
    background: var(--status-ok);
    box-shadow: 0 0 0 0 color-mix(in oklch, var(--status-ok) 60%, transparent);
    animation: ping var(--loop-pulse) var(--ease-out) infinite;
  }
  /* One time column, one size: the shown time is the one the passenger acts on. */
  .times {
    grid-area: times;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    font-variant-numeric: tabular-nums;
    line-height: 1.5rem;
  }
  .t {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  /* A wait at the stop: arrival above departure, both full size, so the gap is plain. */
  .t.arr {
    font-weight: var(--weight-strong);
  }
  .stop-line {
    display: inline-flex;
  }
  .body {
    grid-area: body;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.625rem;
  }
  .name {
    display: inline-flex;
    align-items: center;
    gap: 0.125rem;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    line-height: 1.5rem;
    text-decoration: underline;
    text-decoration-color: color-mix(in oklch, currentColor 30%, transparent);
    text-decoration-thickness: 1px;
    text-underline-offset: 3px;
  }
  .name:hover {
    text-decoration-color: currentColor;
  }
  .seg-start .name,
  .seg-end .name {
    font-weight: var(--font-display-weight);
  }
  .name :global(.name-chevron) {
    color: var(--muted-foreground);
  }
  .vt-plate {
    view-transition-name: line-plate;
  }
  .midnight {
    position: relative;
    z-index: 2;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-top: 0.75rem;
    padding-left: calc(var(--rail-x) + 1.25rem);
  }
  .midnight::after {
    content: '';
    flex: 1;
    border-top: 1px solid var(--border);
  }
  .midnight span {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    color: var(--foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }

  .onward {
    display: grid;
    gap: 0.5rem;
  }
  .change {
    display: grid;
    gap: 0.25rem;
    margin-top: 0.5rem;
  }
  .change-link {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    min-height: 3rem;
    padding: 0.375rem 0.75rem;
    border-radius: var(--radius);
    background: var(--secondary);
    font-weight: var(--weight-strong);
  }
  .change-link:hover {
    background: var(--accent);
  }
  .change-link :global(svg:last-child) {
    margin-left: auto;
  }
  a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: var(--radius);
  }
  @keyframes settle {
    from {
      opacity: 0;
      transform: translateX(-10px);
    }
  }
  @keyframes ping {
    70%,
    100% {
      box-shadow: 0 0 0 10px transparent;
    }
  }
  @media (max-width: 639px) {
    .trip :global(.page-header h1) {
      font-size: var(--fs-xl);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .stop,
    .vehicle,
    .live-dot {
      animation: none;
    }
  }
</style>
