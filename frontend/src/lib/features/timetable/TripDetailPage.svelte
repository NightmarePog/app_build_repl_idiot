<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import {
    ArrowRight,
    ChevronRight,
    LogIn,
    LogOut,
    Timer as TimerIcon,
    Clock as TripIcon
  } from '@lucide/svelte';
  import BackLink from '$lib/components/BackLink.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import type { TripDetail } from '$lib/api/generated';
  import { createResource, ResourceError } from '$lib/api/resource.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { inkFor, isPale } from '$lib/features/lines/line-style';
  import { LINES, linesOf, loadNetwork } from '$lib/features/network/lines.svelte';
  import LinePlate from './LinePlate.svelte';
  import { clock } from './clock.svelte';
  import { duration } from './time';
  import {
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

  /** The title names MY ride: where I board and get off, with times, not the whole route. */
  const title = $derived(
    board && alight
      ? `${vehicleName ? `${vehicleName} · ` : ''}${board.stop.name} ${board.departure_time} → ${alight.stop.name} ${alight.arrival_time}`
      : ''
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
    <BackLink href={lineHref(trip)} label="Jízdní řád linky" />

    <PageHeader kind="Spoj" icon={TripIcon} {title} color={trip.line.color}>
      {#snippet lead()}<span class="vt-plate"
          ><span class="sr-only">Linka {trip.line.number}</span><LinePlate
            number={trip.line.number}
            color={trip.line.color}
            size="lg"
          /></span
        >{/snippet}
    </PageHeader>

    <!-- My ride, then its live state: one card, read left to right, top to bottom. -->
    <section class="ride-hero" aria-label="Vaše jízda">
      {#if partial}
        <div class="ends">
          <div class="end">
            <span class="flag"><LogIn aria-hidden="true" class="size-4" />Nástup</span>
            <strong class="hero-time tabular">{board.departure_time}</strong>
            <span class="hero-stop">{board.stop.name}</span>
          </div>
          <span class="hero-mid" aria-hidden="true">
            <span class="chip tabular">{duration(ribbon.total)}</span>
            <ArrowRight class="hero-arrow size-6" />
          </span>
          <span class="sr-only">jízda {duration(ribbon.total)}</span>
          <div class="end">
            <span class="flag out"><LogOut aria-hidden="true" class="size-4" />Výstup</span>
            <strong class="hero-time tabular">{alight.arrival_time}</strong>
            <span class="hero-stop">{alight.stop.name}</span>
          </div>
        </div>
      {/if}
      {#if status}
        <div class="status-row" role="status">
          <p class="status" data-tone={status.tone}>
            {#if status.tone === 'live'}<span class="live-dot" aria-hidden="true"></span>{/if}
            {#if status.detail}
              <!-- The boarding time is already the big number above: only the countdown here. -->
              <span class="chip countdown tabular"
                ><TimerIcon aria-hidden="true" class="size-4" />{status.tone === 'done'
                  ? 'zítra · '
                  : ''}{status.detail}</span
              >
            {:else}
              {status.title}
            {/if}
          </p>
          {#if progress !== null}
            <div class="progress" aria-label={`Ujeto ${Math.round(progress * 100)} % vaší jízdy`}>
              <span class="progress-track">
                <span class="progress-fill" style:width={`${progress * 100}%`}></span>
                <span class="progress-dot" style:left={`${progress * 100}%`}></span>
              </span>
            </div>
          {/if}
        </div>
      {/if}
    </section>
    <p class="sr-only" aria-live="polite">{vehicleStop}</p>

    {#if part.missing}
      <p class="notice" role="status">
        Zastávkou, ze které jste přišli, tento spoj nejede. Ukazujeme jízdu od výchozí zastávky.
      </p>
    {/if}

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
            {@const shown = end ? stop.arrival_time : stop.departure_time}
            {#if stop.crossesMidnight && i > 0}
              <li class="midnight" aria-hidden="true"><span>☾ po půlnoci</span></li>
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
              <div class="times" aria-hidden="true">
                <!-- One time column: the departure; the arrival only as a small mark where the bus waits. -->
                <span class="t">{isTo && !end ? stop.arrival_time : shown}</span>
              </div>
              <span class="node" aria-hidden="true"></span>
              <div class="body">
                <a class="name" href={resolve('/stops/[id]', { id: String(stop.stop.id) })}
                  >{stop.stop.name}<ChevronRight
                    aria-hidden="true"
                    class="name-chevron size-4"
                  /></a
                >
                {#if stop.dwell > 0 && !first && !end && !isTo}<span
                    class="dwell tabular"
                    title={`Příjezd ${stop.arrival_time}, odjezd ${stop.departure_time}`}
                    ><TimerIcon aria-hidden="true" class="size-4" />{stop.dwell} min stání</span
                  >{/if}
                {#each otherLines(stop.stop.name) as other (other.number)}<a
                    class="stop-line"
                    href={resolve('/lines/[id]', { id: String(other.apiId) })}
                    aria-label={`Přestup na linku ${other.number}`}
                    ><LinePlate number={other.number} color={other.color} size="sm" /></a
                  >{/each}
                {#if isFrom && partial}<span class="flag"
                    ><LogIn aria-hidden="true" class="size-4" />Nástup</span
                  >{/if}
                {#if isTo && partial}<span class="flag out"
                    ><LogOut aria-hidden="true" class="size-4" />Výstup</span
                  >{/if}
                <span class="sr-only">{spokenStop(stop, first, end)}</span>
              </div>
            </li>
          {/each}
        </ol>

        {#if transfers.length}
          <section class="onward" aria-labelledby="onward-heading">
            <h2 id="onward-heading" class="heading">Přestup v zastávce {alight.stop.name}</h2>
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
      </div>
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

  /* My ride: boarding → alighting in large tabular numerals, then the live state under it. */
  .ride-hero {
    margin-top: 1rem;
    padding: 1rem 1.25rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
  }
  .ends {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 0.5rem 1.25rem;
  }
  .end {
    display: grid;
    gap: 0.125rem;
  }
  .hero-mid {
    display: grid;
    justify-items: center;
    gap: 0.125rem;
    align-self: center;
  }
  .chip {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: var(--secondary);
    font-weight: var(--weight-strong);
  }
  .hero-time {
    font-size: var(--fs-2xl, 1.75rem);
    font-weight: var(--font-display-weight);
    line-height: 1.1;
  }
  .hero-stop {
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
  .flag.out {
    background: var(--background);
    color: var(--foreground);
    box-shadow: inset 0 0 0 2px var(--line-stroke, var(--line));
  }

  .status-row {
    display: grid;
    gap: 0.5rem;
    margin-top: 1rem;
    padding-top: 0.75rem;
    border-top: 1px solid var(--border);
  }
  .status-row:first-child {
    margin-top: 0;
    padding-top: 0;
    border-top: 0;
  }
  .status {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.625rem;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
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
  .progress-dot {
    position: absolute;
    top: 50%;
    width: 1rem;
    height: 1rem;
    border: 3px solid var(--card);
    border-radius: 50%;
    background: var(--status-ok);
    transform: translate(-50%, -50%);
    transition: left var(--motion-xslow) var(--ease-out);
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
    margin-top: 1.5rem;
    max-width: 48rem;
  }
  .heading {
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
  }
  .notice {
    margin-top: 0.75rem;
    padding: 0.75rem 1rem;
    border-radius: var(--radius);
    background: var(--secondary);
    font-weight: var(--weight-strong);
  }

  /*
   * The route spine, drawn to time but in normal flow: each stop gets top padding for the minutes
   * since the previous one. Each row draws the rail segment above its dot, so the passenger's
   * part is solid line colour and the rest a faint rail.
   */
  .timeline {
    position: relative;
    margin-top: 1rem;
    --rail-x: 5.5rem;
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
  .outside::before,
  .outside::after {
    width: 0;
    border-left: 0.25rem dashed color-mix(in oklch, var(--line) 50%, var(--background));
    background: none;
    left: calc(var(--rail-x) - 0.125rem);
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
  /* One time column: arrival above departure, same size, only when the bus waits. */
  .times {
    grid-area: times;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    font-variant-numeric: tabular-nums;
    line-height: 1.5rem;
  }
  .t {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  .dwell {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0 0.375rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    font-size: var(--fs-sm);
  }
  .stop-line {
    display: inline-flex;
  }
  .countdown {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-size: var(--fs-lg);
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
    border-top: 1px dashed var(--muted-foreground);
  }
  .midnight span {
    color: var(--foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }

  .onward {
    margin-top: 2rem;
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
