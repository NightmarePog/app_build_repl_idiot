<script lang="ts">
  import { czechCount } from '$lib/plural';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { ArrowRight, ChevronDown, Clock as TripIcon } from '@lucide/svelte';
  import BackLink from '$lib/components/BackLink.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { listLineTrips, type TripDetail } from '$lib/api/generated';
  import { createResource, ResourceError, unwrap } from '$lib/api/resource.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { inkFor, isPale } from '$lib/features/lines/line-style';
  import { LINES, linesOf, loadNetwork } from '$lib/features/network/lines.svelte';
  import LinePlate from './LinePlate.svelte';
  import { clock } from './clock.svelte';
  import { countdown, duration, minutesUntil, spokenTime } from './time';
  import {
    boarding,
    nextTrip as nextTripOf,
    ribbon as ribbonOf,
    timedStops,
    tripPosition
  } from './trip-position';
  import { lineDirectionHref, tripDetail, withQuery } from './trips';

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
  let showEarlier = $state(false);
  $effect(() => clock.subscribe());
  $effect(() => {
    void loadNetwork();
  });
  const now = $derived(clock.now);

  /** Rem per minute of travel: long hops get more room, but rows never overlap (flow layout). */
  const SCALE = 0.42;

  const fromId = $derived(idOf(page.url.searchParams.get('od')));

  const stops = $derived(timedStops(trip?.stops ?? []));

  const from = $derived(boarding(stops, fromId));
  /** ?od= named a stop this trip does not call at: say so and start from the origin. */
  const fromMissing = $derived(from.missing);
  const fromIndex = $derived(from.index);
  const board = $derived(stops[fromIndex]);
  const last = $derived(stops.at(-1));
  const hidden = $derived(fromId && !showEarlier ? fromIndex : 0);

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

  /** Share 0..1 of the ride from boarding to the end already done, while the bus is under way. */
  const progress = $derived.by(() => {
    if (!vehicle || !board || !last) return null;
    const t = [now, now + 1440].find((m) => m >= board.dep && m <= last.arr);
    return t === undefined ? null : (t - board.dep) / Math.max(1, last.arr - board.dep);
  });

  /**
   * What the trip is doing right now, in words: the one thing the side card adds to the
   * timetable (which already lists every time).
   */
  const status = $derived.by(() => {
    if (!board || !last) return null;
    if (vehicle?.kind === 'at')
      return { tone: 'live', title: `Stojí v zastávce ${stops[vehicle.index].stop.name}` };
    if (vehicle?.kind === 'between')
      return {
        tone: 'live',
        title: `Jede z ${stops[vehicle.index].stop.name} do ${stops[vehicle.index + 1].stop.name}`
      };
    const until = minutesUntil(board.dep, now);
    const soon = countdown(until);
    if (soon)
      return { tone: 'soon', title: soon === 'odjíždí' ? 'Právě odjíždí' : `Odjíždí ${soon}` };
    if (board.dep > now) return { tone: 'later', title: `Odjíždí dnes v ${board.departure_time}` };
    return { tone: 'later', title: `Dnes už jel, znovu jede zítra v ${board.departure_time}` };
  });

  /** When this run of the trip leaves, as a word next to the time in the header. */
  const day = $derived.by(() => {
    if (!board) return '';
    if (vehicle) return 'právě jede';
    return board.dep > now || countdown(minutesUntil(board.dep, now)) ? 'dnes' : 'zítra';
  });

  /** One labelled line under the bar when the bus is not on the road, so the empty bar is explained. */
  const idle = $derived(
    day === 'zítra' ? 'Dnes už dojel · příští jízda zítra' : 'Spoj zatím nevyjel'
  );

  /** Ticket ribbon: the trip's span with the night band where midnight falls, if it does. */
  const ribbon = $derived(board && last ? ribbonOf(board, last) : null);

  // The next trip on the same route after this one, for the end of the page.
  const lineTrips = createResource(
    async (signal) => {
      if (!trip) return null;
      return unwrap(listLineTrips({ path: { lineId: trip.line.id }, signal }));
    },
    () => trip?.line.id
  );
  const next = $derived(trip ? nextTripOf(lineTrips.data ?? [], trip) : null);

  /** Lines to change to at the terminus. */
  const transfers = $derived(
    trip && last
      ? linesOf(last.stop.name)
          .filter((number) => number !== trip.line.number)
          .map((number) => LINES[number])
          .filter(Boolean)
      : []
  );

  function lineHref(trip: TripDetail) {
    return lineDirectionHref(trip.line.id, trip.direction);
  }
</script>

<svelte:head>
  <title>
    {trip
      ? `${trip.line.number} ${trip.departure_time} → ${trip.destination.name}`
      : failure === 'missing'
        ? 'Spoj nenalezen'
        : 'Spoj'} · TdA
  </title>
</svelte:head>

{#if failure}
  <div class="state">
    <PageState
      title={failure === 'missing' ? `Spoj ${id} nebyl nalezen` : `Spoj ${id} se nepodařilo načíst`}
      description={failure === 'missing'
        ? 'Spoj s tímto číslem v jízdním řádu není. Vyberte linku a spoj z jejího jízdního řádu.'
        : undefined}
      headingLevel={1}
      error={failure === 'unavailable'}
      onretry={failure === 'unavailable' ? resource.retry : undefined}
      href={failure === 'missing' ? resolve('/lines') : undefined}
      linkLabel="Přehled linek"
    />
  </div>
{:else if !trip || !board || !last || !ribbon}
  <LoadingState label="Načítáme jízdní řád spoje…" detail />
{:else}
  <article
    class="trip"
    class:line-cased={isPale(trip.line.color)}
    style:--line={trip.line.color}
    style:--line-ink={inkFor(trip.line.color)}
  >
    <BackLink href={lineHref(trip)} label={`Linka ${trip.line.number} · jízdní řád`} />

    {#if fromMissing}
      <p class="notice" role="status">
        Tento spoj zastávkou, ze které jste přišli, neprojíždí. Ukazujeme ho celý od výchozí
        zastávky.
      </p>
    {/if}

    <PageHeader
      kind={`Spoj linky ${trip.line.number}`}
      icon={TripIcon}
      title={`${trip.departure_time} ${day} → ${trip.destination.name}`}
      color={trip.line.color}
    >
      {#snippet lead()}<span class="vt-plate" aria-hidden="true"
          ><LinePlate number={trip.line.number} color={trip.line.color} size="lg" /></span
        >{/snippet}
      {#snippet meta()}<span
          >Jeden spoj linky {trip.line.number} · z {trip.origin.name} · {czechCount(
            trip.stops.length,
            'zastávka',
            'zastávky',
            'zastávek'
          )}</span
        >{/snippet}
    </PageHeader>

    <div class="trip-grid">
      <aside class="side">
        <section
          class="ticket board-surface"
          style:--stripe={trip.line.color}
          aria-labelledby="status-heading"
        >
          <h2 id="status-heading" class="ticket-label">Stav spoje</h2>
          {#if status}
            <p class="status" data-tone={status.tone}>
              {#if status.tone === 'live'}<span class="live-dot" aria-hidden="true"></span>{/if}
              {status.title}
            </p>
          {/if}
          <!-- Progress from boarding to the terminus; the dot is the bus while it runs. -->
          <p class="progress-label">
            {progress !== null ? `Ujeto ${Math.round(progress * 100)} % trasy` : idle}
          </p>
          <div class="progress" class:idle={progress === null} aria-hidden="true">
            <span class="progress-track">
              {#if ribbon.night !== null}<span
                  class="progress-night"
                  style:left={`${ribbon.night}%`}
                ></span>{/if}
              <span class="progress-fill" style:width={`${(progress ?? 0) * 100}%`}></span>
              <span
                class="progress-dot"
                class:ghost={progress === null}
                style:left={`${(progress ?? 0) * 100}%`}
              ></span>
            </span>
          </div>
          <dl class="ends">
            <div>
              <dt>{fromIndex > 0 ? 'Nástup' : 'Odjezd'}</dt>
              <dd><strong class="tabular">{board.departure_time}</strong> {board.stop.name}</dd>
            </div>
            <div class="r">
              <dt>Příjezd</dt>
              <dd><strong class="tabular">{last.arrival_time}</strong> {last.stop.name}</dd>
            </div>
          </dl>
          <p class="ride tabular">
            jízda <strong>{duration(ribbon.total)}</strong>{#if ribbon.night !== null}
              · ☾ přes půlnoc, příjezd další den{/if}
          </p>
        </section>
        <p class="sr-only" aria-live="polite">{vehicleStop}</p>
      </aside>
      <div class="main">
        {#if hidden > 0}
          <button type="button" class="earlier" onclick={() => (showEarlier = true)}>
            <ChevronDown aria-hidden="true" class="size-4" />
            {czechCount(hidden, 'předchozí zastávka', 'předchozí zastávky', 'předchozích zastávek')}
          </button>
        {/if}

        <h2 class="section-title">Jízdní řád spoje</h2>
        <p class="cols" aria-hidden="true">
          <span>Odjezd</span><span>Zastávka</span><span class="r"
            >{fromIndex > 0 ? 'min. od nástupu' : 'min. od startu'}</span
          >
        </p>
        <ol class="timeline" aria-label={`Zastávky spoje směr ${trip.destination.name}`}>
          {#each stops.slice(hidden) as stop, index (stop.stop_order)}
            {@const i = index + hidden}
            {@const passed = i < fromIndex}
            {@const isFrom = fromId !== null && i === fromIndex}
            {@const first = i === 0}
            {@const end = i === stops.length - 1}
            {#if stop.crossesMidnight && i > hidden}
              <li class="midnight" aria-hidden="true"><span>☾ půlnoc · další den</span></li>
            {/if}
            <li
              class="stop"
              class:passed
              class:from={isFrom}
              class:terminus={first || end}
              style:--gap={`${index === 0 ? 0 : stop.gap * SCALE}rem`}
              style:--i={index}
            >
              {#if vehicle?.kind === 'at' && vehicle.index === i}
                <span class="vehicle" aria-hidden="true" style:top="calc(var(--gap) + 0.75rem)"
                ></span>
              {:else if vehicle?.kind === 'between' && vehicle.index === i - 1}
                <span
                  class="vehicle"
                  aria-hidden="true"
                  style:top={`calc(${vehicle.share} * var(--gap))`}
                ></span>
              {/if}
              <div class="times" aria-hidden="true">
                <!-- One time per row: the departure, or the arrival at the terminus. -->
                <span class="t">{end ? stop.arrival_time : stop.departure_time}</span>
              </div>
              <span class="node" aria-hidden="true"></span>
              <div class="body">
                <a class="name" href={resolve('/stops/[id]', { id: String(stop.stop.id) })}
                  >{stop.stop.name}</a
                >
                {#if isFrom}<span class="here">tady nastupujete</span>{/if}
                {#if end}<span class="end-tag">konečná · příjezd</span>{/if}
                {#if stop.dwell > 0 && !first && !end}<span class="wait" aria-hidden="true"
                    >příjezd {stop.arrival_time} · stojí {stop.dwell} min</span
                  >{/if}
                <span class="sr-only">
                  {first
                    ? `odjezd ${spokenTime(stop.departure_time, stop.departure_day_offset)}`
                    : end
                      ? `příjezd ${spokenTime(stop.arrival_time, stop.arrival_day_offset)}`
                      : stop.dwell === 0
                        ? `příjezd a odjezd ${spokenTime(stop.departure_time, stop.departure_day_offset)}`
                        : `příjezd ${spokenTime(stop.arrival_time, stop.arrival_day_offset)}, odjezd ${spokenTime(stop.departure_time, stop.departure_day_offset)}, stojí ${stop.dwell} min`}
                </span>
              </div>
              <!-- Cumulative travel time, from the boarding stop when there is one. -->
              <span class="offset tabular" aria-hidden="true">
                {#if i > fromIndex}+{stop.arr - board.dep} min{/if}
              </span>
            </li>
          {/each}
        </ol>

        <!-- The end of the trip: where to go next. -->
        {#if transfers.length || next || lineTrips.error}
          <section class="onward" aria-labelledby="onward-heading">
            <h2 id="onward-heading" class="section-title">Dál z konečné {last.stop.name}</h2>
            {#if transfers.length}
              <div class="onward-block">
                <h3>Změnit na</h3>
                <ul class="change">
                  {#each transfers as other (other.number)}
                    <li>
                      <a
                        href={resolve('/lines/[id]', { id: String(other.apiId) })}
                        class="change-link"
                      >
                        <LinePlate number={other.number} color={other.color} />
                        <span>{other.name}</span>
                        <ArrowRight aria-hidden="true" class="size-4" />
                      </a>
                    </li>
                  {/each}
                </ul>
              </div>
            {/if}
            {#if next}
              <div class="onward-block">
                <h3>Další spoj této trasy</h3>
                <a
                  class="next-trip"
                  href={withQuery(resolve('/trips/[id]', { id: String(next.trip.id) }), {
                    od: fromId
                  })}
                >
                  <span class="next-trip-time tabular">{next.trip.departure_time}</span>
                  <span
                    >{next.tomorrow ? 'zítra · ' : ''}z {next.trip.origin.name} do {next.trip
                      .destination.name}</span
                  >
                  <ArrowRight aria-hidden="true" class="size-4" />
                </a>
              </div>
            {:else if lineTrips.error}
              <p class="muted-note">
                Další spoj této trasy se nepodařilo načíst.
                <button type="button" class="retry" onclick={lineTrips.retry}>Zkusit znovu</button>
              </p>
            {/if}
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
  .trip-grid {
    display: grid;
    gap: 1.5rem;
  }
  @media (min-width: 1024px) {
    .trip-grid {
      grid-template-columns: minmax(0, 1fr) 22rem;
      align-items: start;
    }
    .side {
      position: sticky;
      top: calc(var(--header-h, 4rem) + 1rem);
      order: 2;
    }
  }
  .cols {
    display: grid;
    grid-template-columns: 6rem 1fr auto;
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .cols .r {
    text-align: right;
  }
  .trip {
    max-width: 80rem;
    /* Room for the fixed mobile tab bar so the end of the page is never hidden under it. */
    padding-bottom: calc(var(--tabbar-h, 0px) + env(safe-area-inset-bottom) + 2rem);
  }
  .ticket {
    position: relative;
    margin-top: 0.75rem;
    padding: 1.25rem;
    overflow: hidden;
    border-radius: var(--radius-2xl);
    color: var(--board-fg);
  }
  .ticket-label {
    color: var(--sign-marker);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .status {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    margin-top: 0.5rem;
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: var(--lh-tight);
  }
  .status[data-tone='soon'] {
    color: var(--board-led);
  }
  .progress-label {
    margin-top: 1rem;
    color: var(--board-muted);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .progress {
    margin-top: 0.5rem;
  }
  .progress-track {
    position: relative;
    display: block;
    height: 0.5rem;
    border-radius: var(--radius-pill);
    background: var(--board-raised);
  }
  .progress-fill {
    position: absolute;
    inset: 0 auto 0 0;
    border-radius: inherit;
    background: var(--stripe, var(--sign-marker));
    transition: width var(--motion-xslow) var(--ease-out);
  }
  .progress-night {
    position: absolute;
    top: -0.25rem;
    bottom: -0.25rem;
    width: 2px;
    background: var(--board-muted);
  }
  .progress-dot {
    position: absolute;
    top: 50%;
    width: 1rem;
    height: 1rem;
    border: 3px solid var(--board-bg);
    border-radius: var(--radius-pill);
    background: var(--status-ok);
    transform: translate(-50%, -50%);
    transition: left var(--motion-xslow) var(--ease-out);
  }
  /* Not on the road: a hollow ghost bus waiting at the start. */
  .progress-dot.ghost {
    border: 2px dashed var(--board-muted);
    background: var(--board-bg);
  }
  .ends {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    margin-top: 0.75rem;
  }
  .ends .r {
    text-align: right;
  }
  .ends dt {
    color: var(--board-muted);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .ends dd {
    color: var(--board-place);
  }
  .ends strong {
    display: block;
    color: var(--board-led);
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
  .ride {
    margin-top: 1rem;
    padding-top: 0.75rem;
    border-top: 2px dashed var(--board-line);
    color: var(--board-muted);
  }
  .ride strong {
    color: var(--board-fg);
  }
  .live-dot {
    flex: none;
    width: 0.75rem;
    height: 0.75rem;
    border-radius: var(--radius-pill);
    background: var(--status-ok);
    animation: ping var(--loop-pulse) var(--ease-out) infinite;
  }
  .earlier {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.75rem;
    margin-top: 1rem;
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
  }
  .earlier:hover {
    color: var(--foreground);
  }

  /*
   * The timeline is drawn to time but in normal flow: each stop gets top padding for the minutes
   * since the previous one, so long names or large text push rows down instead of overlapping.
   */
  .timeline {
    position: relative;
    margin-top: 1.5rem;
    --rail-x: 6.25rem;
  }
  .timeline::before {
    content: '';
    position: absolute;
    left: calc(var(--rail-x) - 0.1875rem);
    top: 0.75rem;
    bottom: 0.75rem;
    width: 0.375rem;
    border-radius: var(--radius-md);
    background: var(--line);
    box-shadow: 0 0 0 1.5px var(--line-stroke, var(--line));
  }
  .stop {
    position: relative;
    display: grid;
    grid-template-columns: calc(var(--rail-x) - 1.25rem) 2.5rem minmax(0, 1fr) auto;
    grid-template-areas: 'times node body offset';
    align-items: start;
    padding-top: max(0.75rem, var(--gap));
    animation: settle var(--motion-slow) var(--ease-spring-out) both;
    animation-delay: calc(var(--i) * var(--motion-stagger));
  }
  .vehicle {
    position: absolute;
    z-index: 3;
    left: calc(var(--rail-x) - 0.625rem);
    width: 1.25rem;
    height: 1.25rem;
    margin-top: -0.625rem;
    border: 3px solid var(--background);
    border-radius: var(--radius-pill);
    background: var(--status-ok);
    box-shadow: 0 0 0 0 color-mix(in oklch, var(--status-ok) 60%, transparent);
    animation: ping var(--loop-pulse) var(--ease-out) infinite;
  }
  /* One dot style for every stop; the termini are filled. Dwell is spelled out next to the name. */
  .node {
    grid-area: node;
    justify-self: center;
    position: relative;
    z-index: 2;
    width: 1rem;
    height: 1rem;
    margin-top: 0.25rem;
    border-radius: var(--radius-pill);
    border: 3px solid var(--line-stroke, var(--line));
    background: var(--background);
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .stop:hover .node {
    transform: scale(1.15);
  }
  .terminus .node {
    background: var(--line);
  }
  .from .node {
    border-color: var(--foreground);
    box-shadow: 0 0 0 5px color-mix(in oklch, var(--line) 40%, transparent);
  }
  .times {
    grid-area: times;
    display: flex;
    justify-content: flex-end;
    font-variant-numeric: tabular-nums;
    line-height: 1.5rem;
  }
  .t {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    letter-spacing: -0.01em;
  }
  .end-tag {
    padding: 0.0625rem 0.5rem;
    border-radius: var(--radius-pill);
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .body {
    grid-area: body;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.625rem;
  }
  .name {
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    line-height: 1.5rem;
    text-underline-offset: 3px;
  }
  .terminus .name,
  .from .name {
    font-weight: var(--font-display-weight);
  }
  .name:hover {
    text-decoration: underline;
  }
  .wait {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-md);
    background: var(--secondary);
    color: var(--foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .here {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-pill);
    background: var(--sign-marker);
    color: var(--sign-marker-foreground);
    box-shadow: inset 0 0 0 1.5px var(--foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
  }
  .offset {
    grid-area: offset;
    min-width: 4.5rem;
    color: var(--muted-foreground);
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
    line-height: 1.5rem;
    text-align: right;
  }
  .notice {
    margin-top: 0.75rem;
    padding: 0.75rem 1rem;
    border-radius: var(--radius-lg);
    background: var(--secondary);
    font-weight: var(--weight-strong);
  }
  .vt-plate {
    view-transition-name: line-plate;
  }
  .passed {
    opacity: 0.45;
  }
  /* Midnight: a dark night band across the timeline, with the moon on the rail. */
  .midnight {
    position: relative;
    z-index: 2;
    display: flex;
    justify-content: center;
    margin-top: 0.75rem;
    padding: 0.375rem;
    border-radius: var(--radius-pill);
    background: linear-gradient(
      90deg,
      transparent,
      oklch(0.2 0.04 270) 20%,
      oklch(0.2 0.04 270) 80%,
      transparent
    );
  }
  .midnight span {
    color: var(--sign-marker);
    font-size: var(--fs-xs);
    font-weight: var(--font-display-weight);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }

  .onward {
    margin-top: 2.5rem;
    padding: 1.25rem;
    border: 1px solid var(--border);
    border-top: 6px solid var(--line);
    border-radius: var(--radius-xl);
    background: var(--card);
  }
  .onward h2 {
    margin-top: 0;
  }
  .onward-block {
    margin-top: 1rem;
  }
  h3 {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .change {
    display: grid;
    gap: 0.25rem;
    margin-top: 0.5rem;
  }
  .change-link,
  .next-trip {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    min-height: 3rem;
    margin-top: 0.5rem;
    padding: 0.375rem 0.75rem;
    border-radius: var(--radius-lg);
    background: var(--secondary);
    font-weight: var(--weight-strong);
  }
  .change-link:hover,
  .next-trip:hover {
    background: var(--accent);
  }
  .change-link :global(svg:last-child),
  .next-trip :global(svg:last-child) {
    margin-left: auto;
  }
  .next-trip-time {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  a:focus-visible,
  button:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: var(--radius-sm);
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
  .muted-note {
    margin-top: 1rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .retry {
    margin-left: 0.25rem;
    color: var(--primary);
    font-weight: var(--weight-strong);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  /* Phones: a compact one-row ticket so the stop list starts near the top of the screen. */
  @media (max-width: 639px) {
    .ticket {
      padding: 1rem;
    }
    .status {
      font-size: var(--fs-lg);
    }
    .timeline {
      --rail-x: 6.25rem;
    }
    .offset {
      min-width: 3.5rem;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .stop,
    .vehicle,
    .live-dot {
      animation: none;
    }
    .node {
      transition: none;
    }
  }
</style>
