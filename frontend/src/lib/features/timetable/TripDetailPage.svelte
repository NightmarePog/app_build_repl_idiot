<script lang="ts">
  import { czechCount } from '$lib/plural';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { ArrowLeft, ArrowRight, ChevronDown, Ticket as TicketIcon } from '@lucide/svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { listLineTrips, type TripDetail, type TripSummary } from '$lib/api/generated';
  import { createResource, ResourceError, unwrap } from '$lib/api/resource.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { inkFor, isPale } from '$lib/features/lines/line-style';
  import { LINES, linesOf, loadNetwork } from '$lib/features/network/lines.svelte';
  import { Button } from '$lib/ui/button';
  import FlapTime from './FlapTime.svelte';
  import LinePlate from './LinePlate.svelte';
  import { clock } from './clock.svelte';
  import { duration, minutesOf, spokenTime } from './time';
  import { tripPosition } from './trip-position';
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

  const stops = $derived.by(() => {
    const timetable = trip?.stops ?? [];
    let previous: number | null = null;
    return timetable.map((stop, index) => {
      const arr = minutesOf(stop.arrival_time, stop.arrival_day_offset);
      const dep = minutesOf(stop.departure_time, stop.departure_day_offset);
      const gap = previous === null ? 0 : arr - previous;
      const crossesMidnight =
        index > 0 && stop.arrival_day_offset > timetable[index - 1].departure_day_offset;
      previous = dep;
      return { ...stop, arr, dep, dwell: dep - arr, gap, crossesMidnight };
    });
  });

  const fromFound = $derived(fromId ? stops.findIndex((stop) => stop.stop.id === fromId) : 0);
  /** ?od= named a stop this trip does not call at: say so and start from the origin. */
  const fromMissing = $derived(fromId !== null && stops.length > 0 && fromFound < 0);
  const fromIndex = $derived(Math.max(0, fromFound));
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

  /** Ticket ribbon: the trip's span with the night band where midnight falls, if it does. */
  const ribbon = $derived.by(() => {
    if (!board || !last) return null;
    const total = Math.max(1, last.arr - board.dep);
    const midnight = 1440 * (board.departure_day_offset + 1);
    const crosses = last.arr >= midnight && board.dep < midnight;
    return { total, night: crosses ? ((midnight - board.dep) / total) * 100 : null };
  });

  // The next trip on the same route after this one, for the end of the page.
  const lineTrips = createResource(
    async (signal) => {
      if (!trip) return null;
      return unwrap(listLineTrips({ path: { lineId: trip.line.id }, signal }));
    },
    () => trip?.line.id
  );
  const nextTrip = $derived.by<TripSummary | null>(() => {
    if (!trip) return null;
    const same = (lineTrips.data ?? []).filter((t) => t.route_id === trip.route_id);
    const index = same.findIndex((t) => t.id === trip.id);
    return index >= 0 ? (same[index + 1] ?? same[0] ?? null) : null;
  });
  const nextIsTomorrow = $derived.by(() => {
    if (!trip || !nextTrip) return false;
    return (
      minutesOf(nextTrip.departure_time, nextTrip.departure_day_offset) <=
      minutesOf(trip.departure_time, trip.departure_day_offset)
    );
  });

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
    <Button href={lineHref(trip)} variant="ghost" class="-ml-4">
      <ArrowLeft aria-hidden="true" data-icon="inline-start" /> Linka {trip.line.number}
    </Button>

    {#if fromMissing}
      <p class="notice" role="status">
        Tento spoj zastávkou, ze které jste přišli, neprojíždí. Ukazujeme ho celý od výchozí
        zastávky.
      </p>
    {/if}

    <PageHeader
      kind="Spoj"
      icon={TicketIcon}
      title={`${trip.departure_time} ${trip.origin.name} → ${trip.destination.name}`}
      color={trip.line.color}
    >
      {#snippet meta()}<span
          >Linka {trip.line.number} · jízda {duration(ribbon.total)} · {czechCount(
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
          aria-label="Shrnutí spoje"
        >
          <div class="ticket-line">
            <span class="vt-plate"
              ><LinePlate number={trip.line.number} color={trip.line.color} size="lg" /></span
            >
            <div>
              <p class="dest">
                směr
                <strong>{trip.destination.name}</strong>
              </p>
              {#if fromIndex > 0}<p class="sub">
                  výchozí {trip.origin.name}
                  {trip.departure_time}
                </p>{/if}
            </div>
          </div>
          <dl class="ticket-times">
            <div class="from">
              <dt>{fromIndex > 0 ? 'Odjezd odsud' : 'Odjezd'}</dt>
              <dd>
                <FlapTime
                  time={board.departure_time}
                  dayOffset={board.departure_day_offset}
                  size="lg"
                  showDay={false}
                />
                <span class="place">{board.stop.name}</span>
              </dd>
            </div>
            <!-- One continuous ribbon from departure to arrival; a night band marks midnight. -->
            <div class="ribbon" aria-hidden="true">
              <span class="ribbon-track">
                {#if ribbon.night !== null}
                  <span class="ribbon-night" style:left={`${ribbon.night}%`}>
                    <span class="moon">☾ půlnoc</span>
                  </span>
                {/if}
              </span>
              <span class="ribbon-time tabular">{duration(ribbon.total)}</span>
            </div>
            <div class="to">
              <dt>Příjezd</dt>
              <dd>
                <FlapTime
                  time={last.arrival_time}
                  dayOffset={last.arrival_day_offset}
                  size="lg"
                  delay={200}
                  showDay={false}
                />
                <span class="place">{last.stop.name}</span>
              </dd>
            </div>
          </dl>
          {#if ribbon.night !== null}
            <p class="night-note">
              Spoj jede přes půlnoc: odjezd {board.departure_time}, příjezd {last.arrival_time} následující
              den.
            </p>
          {/if}
        </section>

        {#if vehicle}
          <p class="live">
            <span class="live-dot" aria-hidden="true"></span>
            {#if vehicle.kind === 'at'}Právě stojí v zastávce {stops[vehicle.index].stop
                .name}{:else}Právě jede mezi {stops[vehicle.index].stop.name} a {stops[
                vehicle.index + 1
              ].stop.name}{/if}
          </p>
        {/if}
        <p class="sr-only" aria-live="polite">{vehicleStop}</p>
      </aside>
      <div class="main">
        {#if hidden > 0}
          <button type="button" class="earlier" onclick={() => (showEarlier = true)}>
            <ChevronDown aria-hidden="true" class="size-4" />
            {hidden === 1 ? '1 předchozí zastávka' : `${hidden} předchozí zastávky`}
          </button>
        {/if}

        <h2 class="section-title">Jízdní řád spoje</h2>
        <p class="cols" aria-hidden="true">
          <span>Čas</span><span>Zastávka</span><span class="r"
            >{fromIndex > 0 ? 'od nástupu' : 'od výchozí'}</span
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
              class:no-wait={stop.dwell === 0 && !first && !end}
              style:--gap={`${index === 0 ? 0 : stop.gap * SCALE}rem`}
              style:--dwell={`${stop.dwell * SCALE}rem`}
              style:--i={index}
            >
              {#if vehicle?.kind === 'at' && vehicle.index === i}
                <span
                  class="vehicle"
                  aria-hidden="true"
                  style:top={`calc(var(--gap) + 0.75rem + ${vehicle.share} * var(--dwell))`}
                ></span>
              {:else if vehicle?.kind === 'between' && vehicle.index === i - 1}
                <span
                  class="vehicle"
                  aria-hidden="true"
                  style:top={`calc(${vehicle.share} * var(--gap))`}
                ></span>
              {/if}
              <div class="times" aria-hidden="true">
                {#if first}
                  <span class="t dep">{stop.departure_time}</span>
                {:else if end || stop.dwell === 0}
                  <span class="t">{stop.arrival_time}</span>
                {:else}
                  <span class="t arr"><small>příj.</small> {stop.arrival_time}</span>
                  <span class="t dep"><small>odj.</small> {stop.departure_time}</span>
                {/if}
              </div>
              <span class="node" aria-hidden="true"></span>
              <div class="body">
                <a class="name" href={resolve('/stops/[id]', { id: String(stop.stop.id) })}
                  >{stop.stop.name}</a
                >
                {#if isFrom}<span class="here">tady nastupujete</span>{/if}
                {#if !first && !passed}<a
                    class="from-here"
                    href={resolve('/stops/[id]', { id: String(stop.stop.id) })}
                    >{end ? 'navazující odjezdy ›' : 'odjezdy odsud ›'}</a
                  >{/if}
                {#if stop.dwell > 0 && !first && !end}<span class="wait" aria-hidden="true"
                    >stojí {stop.dwell} min</span
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
        <section class="onward" aria-labelledby="onward-heading">
          <h2 id="onward-heading" class="section-title">Navazující odjezdy · {last.stop.name}</h2>
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
          {#if nextTrip && nextTrip.id !== trip.id}
            <div class="onward-block">
              <h3>Další spoj této trasy</h3>
              <a
                class="next-trip"
                href={withQuery(resolve('/trips/[id]', { id: String(nextTrip.id) }), {
                  od: fromId
                })}
              >
                <span class="next-trip-time tabular">{nextTrip.departure_time}</span>
                <span
                  >{nextIsTomorrow ? 'zítra · ' : ''}z {nextTrip.origin.name} do {nextTrip
                    .destination.name}</span
                >
                <ArrowRight aria-hidden="true" class="size-4" />
              </a>
            </div>
          {/if}
        </section>
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
  .t small {
    font-size: var(--fs-2xs);
    font-weight: var(--weight-regular);
    color: var(--muted-foreground);
  }
  .from-here {
    color: var(--primary);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    text-decoration: underline;
    text-underline-offset: 3px;
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
  .ticket-line {
    display: flex;
    align-items: center;
    gap: 1rem;
    min-height: 4rem;
    padding-bottom: 1.25rem;
    border-bottom: 2px dashed var(--board-line);
  }
  .dest {
    font-size: var(--fs-h2, var(--fs-h1));
    font-weight: var(--weight-regular);
    letter-spacing: var(--tracking-display);
    line-height: var(--lh-tight);
  }
  .dest strong {
    font-weight: var(--font-display-weight);
  }
  .sub {
    margin-top: 0.25rem;
    color: var(--board-muted);
    font-size: var(--fs-base);
    font-weight: var(--weight-regular);
  }
  .ticket-times {
    display: grid;
    grid-template-columns: auto minmax(4rem, 1fr) auto;
    align-items: end;
    gap: 1rem;
    margin-top: 1.5rem;
  }
  .to {
    text-align: right;
  }
  .to dd {
    justify-items: end;
  }
  dt {
    color: var(--sign-marker);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  dd {
    display: grid;
    gap: 0.375rem;
    margin-top: 0.375rem;
  }
  .place {
    color: var(--board-place);
    font-weight: var(--weight-strong);
  }
  /* The ribbon between the two times: line colour by day, a dark band with a moon by night. */
  .ribbon {
    position: relative;
    align-self: center;
    margin-bottom: 1.75rem;
  }
  .ribbon-track {
    position: relative;
    display: block;
    height: 0.625rem;
    border-radius: 999px;
    background: var(--line);
    overflow: visible;
  }
  .ribbon-night {
    position: absolute;
    top: -0.25rem;
    bottom: -0.25rem;
    right: 0;
    border-radius: 0 999px 999px 0;
    background: linear-gradient(90deg, oklch(0.16 0.03 270), oklch(0.22 0.05 270));
    box-shadow: inset 2px 0 0 var(--sign-marker);
  }
  .moon {
    position: absolute;
    left: 0;
    top: -1.5rem;
    transform: translateX(-50%);
    color: var(--sign-marker);
    font-size: var(--fs-xs);
    font-weight: var(--font-display-weight);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
    white-space: nowrap;
  }
  .ribbon-time {
    position: absolute;
    left: 50%;
    top: 1rem;
    transform: translateX(-50%);
    white-space: nowrap;
    color: var(--board-muted);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .night-note {
    margin-top: 1rem;
    color: var(--board-muted);
    font-size: var(--fs-sm);
  }
  .live {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-top: 1rem;
    font-weight: var(--weight-strong);
  }
  .live-dot {
    width: 0.75rem;
    height: 0.75rem;
    border-radius: 999px;
    background: var(--status-ok);
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
    animation-delay: calc(var(--i) * 55ms);
  }
  .vehicle {
    position: absolute;
    z-index: 3;
    left: calc(var(--rail-x) - 0.625rem);
    width: 1.25rem;
    height: 1.25rem;
    margin-top: -0.625rem;
    border: 3px solid var(--background);
    border-radius: 999px;
    background: var(--status-ok);
    box-shadow: 0 0 0 0 color-mix(in oklch, var(--status-ok) 60%, transparent);
    animation: ping 1.6s var(--ease-out) infinite;
  }
  /* Dwell thickens the spine: the stop marker becomes a capsule as long as the wait. */
  .node {
    grid-area: node;
    justify-self: center;
    position: relative;
    z-index: 2;
    width: 1.125rem;
    height: max(1.125rem, calc(var(--dwell) + 1.125rem));
    margin-top: 0.1875rem;
    border-radius: 999px;
    border: 3px solid var(--line-stroke, var(--line));
    background: var(--background);
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .stop:not(.no-wait):not(.terminus) .node {
    width: 1.375rem;
    background: color-mix(in oklch, var(--line) 35%, var(--background));
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
  /* Zero dwell: a small solid dot and a single time. */
  .no-wait .node {
    width: 0.75rem;
    height: 0.75rem;
    margin-top: 0.375rem;
    border-width: 0;
    background: var(--line-stroke, var(--line));
  }
  /* Arrival above, departure below, aligned with the two ends of the capsule. */
  .times {
    grid-area: times;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: space-between;
    min-height: max(1.5rem, calc(var(--dwell) + 1.5rem));
    font-variant-numeric: tabular-nums;
    line-height: 1.5rem;
  }
  .t {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    letter-spacing: -0.01em;
  }
  .t.arr {
    color: var(--muted-foreground);
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
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
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .here {
    padding: 0.125rem 0.5rem;
    border-radius: 999px;
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
    border-radius: 999px;
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
  @media (max-width: 639px) {
    .ticket-times {
      grid-template-columns: 1fr;
      gap: 1.25rem;
    }
    .to {
      text-align: left;
    }
    .to dd {
      justify-items: start;
    }
    .ribbon {
      margin: 1.25rem 0 1.5rem;
    }
    .timeline {
      --rail-x: 5rem;
    }
    .offset {
      min-width: 3.5rem;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .stop,
    .vehicle {
      animation: none;
    }
    .node {
      transition: none;
    }
  }
</style>
