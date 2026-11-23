<script lang="ts">
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { BusFront, TramFront } from '@lucide/svelte';
  import { getLine, type LineDetail, type Route } from '$lib/api/generated';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { LINES, linesOf } from '$lib/features/network/lines.svelte';
  import { loadStops, stopsFailed, stopsList } from '$lib/features/network/stops-store.svelte';
  import Isa from './Isa.svelte';
  import LineTrips from '$lib/features/timetable/LineTrips.svelte';
  import BusIllustration from '$lib/features/network/BusIllustration.svelte';
  import { clock } from '$lib/features/timetable/clock.svelte';
  import { createLineTimetable } from '$lib/features/timetable/line-timetable.svelte';
  import { hops, median, travelMinutes } from '$lib/features/timetable/line-trips';
  import { minutesOf } from '$lib/features/timetable/time';
  import { tripPosition } from '$lib/features/timetable/trip-position';
  import { inkFor, isPale, typeLabel, zOrZe } from './line-style';

  let { id }: { id: string } = $props();

  let line = $state<LineDetail | null>(null);
  let failure = $state<'missing' | 'unavailable' | null>(null);
  let attempt = $state(0);

  $effect(() => {
    const lineId = Number(id);
    void attempt;
    line = null;
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

  const bound = $derived(page.url.searchParams.get('smer') === 'inbound' ? 'inbound' : 'outbound');
  const route = $derived<Route | undefined>(
    line ? (line.routes.find((r) => r.bound === bound) ?? line.routes[0]) : undefined
  );
  const accessible = $derived(
    route ? route.stops.filter((stop) => stepFree.get(stop.id)).length : 0
  );

  // Trips of the line: drive the trips sheet, the per-direction travel time on the tabs and the
  // minutes between stops on the diagram. One request set per line, shared by all three.
  const timetable = createLineTimetable(() => Number(id));
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
  /** Minutes between stops in this direction, from the first trip with times. */
  const legs = $derived(routeDetails[0] ? hops(routeDetails[0].stops) : null);

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

  /** Other lines at the stop, linked straight to that stop on their own diagram. */
  function transfersAt(stopName: string) {
    return linesOf(stopName)
      .filter((number) => number !== line?.number)
      .map((number) => LINES[number]);
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
      title={failure === 'missing' ? 'Linka nebyla nalezena' : 'Linku se nepodařilo načíst'}
      headingLevel={1}
      error={failure === 'unavailable'}
      onretry={failure === 'unavailable' ? () => attempt++ : undefined}
      href={failure === 'missing' ? resolve('/lines') : undefined}
      linkLabel="Přehled linek"
    />
  </div>
{:else if !line}
  <LoadingState label="Načítáme detail linky…" detail />
{:else}
  <article
    class="line"
    class:line-cased={isPale(line.color)}
    style:--line={line.color}
    style:--line-ink={inkFor(line.color)}
  >
    <div class="band">
      <PageHeader
        kind="Linka"
        icon={line.type.toLowerCase() === 'tram' ? TramFront : BusFront}
        title={line.name}
        color={line.color}
      >
        {#snippet lead()}<span class="plate tabular" aria-hidden="true">{line?.number}</span
          >{/snippet}
        {#snippet back()}<a class="back" href={resolve('/lines')}>← Všechny linky</a>{/snippet}
        {#snippet meta()}<span
            >{typeLabel(line?.type ?? '')} linka {line?.number} ·
            {(line?.routes.length ?? 0) > 1 ? 'dva směry' : 'jeden směr'}</span
          >{/snippet}
      </PageHeader>
      <div class="band-bus" aria-hidden="true"><BusIllustration stripe={line.color} /></div>
    </div>

    {#if !route || route.stops.length === 0}
      <PageState title="Trasa linky zatím není zadaná" />
    {:else}
      {#if line.routes.length > 1}
        <!-- Headsigns as tabs, like a stop timetable: pick where you are going. -->
        <h2 class="section-title">Zobrazený směr trasy</h2>
        <nav class="directions" aria-label="Směr jízdy">
          {#each line.routes as option (option.id)}
            <a
              href={option.bound === 'inbound'
                ? resolve('/lines/[id]?smer=inbound', { id })
                : resolve('/lines/[id]', { id })}
              aria-current={option.id === route.id ? 'page' : undefined}
              data-sveltekit-replacestate
              data-sveltekit-noscroll
            >
              <span class="dir-name"
                >{option.id === route.id ? '✓ ' : ''}Směr {option.direction}</span
              >
              {#if travelByRoute.get(option.id) != null}<span class="dir-label tabular"
                  >jízda {travelByRoute.get(option.id)} min</span
                >{/if}
            </a>
          {/each}
        </nav>
      {:else}
        <p class="single-direction">směr <strong>{route.direction}</strong></p>
      {/if}
      <p class="sr-only" aria-live="polite">Směr {route.direction}</p>

      <p class="summary">
        {#if stopsList()}
          <Isa size={20} />
          <span class="tabular">
            Bezbariérové zastávky: {accessible}
            {zOrZe(route.stops.length)}
            {route.stops.length}
          </span>
        {:else if stopsFailed()}
          Bezbariérovost zastávek se nepodařilo zjistit.
        {/if}
      </p>

      <h2 class="section-title">Trasa a zastávky linky</h2>
      {#if cumulative.size}<p class="leg-head">min od výchozí</p>{/if}
      <ol class="diagram" aria-label={`Zastávky směr ${route.direction}`}>
        {#each route.stops as stop, index (stop.id)}
          {@const others = transfersAt(stop.name)}
          {@const first = index === 0}
          {@const last = index === route.stops.length - 1}
          {@const leg = cumulative.get(stop.id)}
          {@const wait = legs?.dwell.get(stop.id) ?? 0}
          <li
            id={`stop-${stop.id}`}
            class:terminus={first || last}
            class:interchange={others.length > 0}
            class:waits={wait > 0 && !first && !last}
          >
            <span class="dot" aria-hidden="true"></span>
            {#if bus && bus.index === index}
              <a
                class="bus"
                href={resolve('/trips/[id]', { id: String(bus.tripId) })}
                style:--share={bus.share}
                aria-label="Spoj, který teď jede v tomto směru"
                ><BusIllustration stripe={line.color} /></a
              >
            {/if}
            {#if leg !== undefined}
              <span class="leg tabular" title="minut od výchozí zastávky">+{leg}</span>
            {/if}
            <div class="stop">
              <a class="name" href={resolve('/stops/[id]', { id: String(stop.id) })}
                >{stop.name}<span class="chev" aria-hidden="true">›</span></a
              >
              {#if stepFree.get(stop.id)}
                <Isa size={20} label="bezbariérový nástup" />
              {/if}
              {#if others.length}
                <span class="transfer">
                  <span class="transfer-label">přestup na</span>
                  {#each others as other (other.number)}
                    <a
                      class="badge tabular"
                      href={resolve(`/lines/[id]#stop-${stop.id}`, { id: String(other.apiId) })}
                      style:background={other.color}
                      style:color={other.ink}
                      ><span aria-hidden="true">{other.number}</span><span class="sr-only"
                        >linka {other.number}</span
                      ></a
                    >
                  {/each}
                </span>
              {/if}
              {#if wait > 0 && !first && !last}<span class="note">stojí {wait} min</span>{/if}
              {#if last}<span class="note">konečná</span>{/if}
            </div>
          </li>
        {/each}
      </ol>

      <h2 class="section-title trips-title">Spoje linky {line.number} v obou směrech</h2>
      {#each [route, ...line.routes.filter((r) => r.id !== route?.id)] as r (r.id)}
        <LineTrips {timetable} route={r} lineNumber={line.number} />
      {/each}
    {/if}
  </article>
{/if}

<style>
  .state {
    margin-top: 1rem;
  }
  .band {
    position: relative;
    margin-bottom: 1rem;
    padding: 1.25rem 1.25rem 0.25rem;
    border-radius: var(--radius-xl);
    border-top: 6px solid var(--line);
    background: color-mix(in oklch, var(--line) 12%, var(--background));
    overflow: hidden;
  }
  .band-bus {
    position: absolute;
    right: 1.25rem;
    bottom: 0.75rem;
    width: 9rem;
    opacity: 0.9;
  }
  @media (max-width: 639px) {
    .band-bus {
      display: none;
    }
  }

  .back {
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .plate {
    display: grid;
    place-items: center;
    flex-shrink: 0;
    min-width: 4rem;
    height: 4rem;
    padding-inline: 0.625rem;
    border-radius: var(--radius-md);
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    letter-spacing: 0;
    line-height: 1;
  }

  .directions {
    display: grid;
    grid-auto-columns: minmax(0, 1fr);
    grid-auto-flow: column;
    max-width: 36rem;
    margin-top: 1.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    overflow: hidden;
  }
  .directions a {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-height: 3.5rem;
    padding: 0.5rem 1rem;
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .directions a + a {
    border-left: 1px solid var(--border);
  }
  .directions a:hover {
    background: var(--accent);
  }
  .directions a[aria-current='page'] {
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
  }
  .dir-label {
    font-size: var(--fs-sm);
    font-weight: var(--weight-regular);
    opacity: 0.8;
  }
  .dir-name {
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
    line-height: 1.2;
  }
  .single-direction {
    margin-top: 1.75rem;
    font-size: var(--fs-lg);
  }
  .directions a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -4px;
  }

  .summary {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 1.5rem;
    margin-top: 1rem;
    font-weight: var(--weight-regular);
    color: var(--muted-foreground);
  }

  /*
   * Vertical line diagram, as on a PID line timetable. Columns: rail with the stop symbol,
   * then the stop. The rail runs through every row and stops at the termini.
   */
  .diagram {
    max-width: 44rem;
    margin-top: 1.25rem;
  }
  .diagram li {
    position: relative;
    display: grid;
    grid-template-columns: 2rem minmax(0, 1fr);
    align-items: center;
    column-gap: 0.75rem;
    min-height: 3.5rem;
    scroll-margin-top: calc(var(--header-h) + 2rem);
  }
  .diagram li::before {
    content: '';
    position: absolute;
    left: calc(1rem - 0.25rem);
    top: 0;
    bottom: 0;
    width: 0.5rem;
    background: var(--line);
    box-shadow: 0 0 0 1.5px var(--line-stroke, var(--line));
  }
  .diagram li:first-child::before {
    top: 50%;
  }
  .diagram li:last-child::before {
    bottom: 50%;
  }
  /* Arriving from a transfer link: show which stop was meant. */
  .diagram li:target {
    background: linear-gradient(to right, transparent 2.5rem, var(--accent) 2.5rem);
  }
  .dot {
    position: relative;
    z-index: 1;
    justify-self: center;
    width: 1rem;
    height: 1rem;
    border-radius: 999px;
    border: 3px solid var(--line-stroke, var(--line));
    background: var(--background);
  }
  .terminus .dot {
    width: 1.375rem;
    height: 1.375rem;
    background: var(--line);
  }
  .stop {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.625rem;
    padding-block: 0.375rem;
  }
  .name {
    padding-block: 0.5rem;
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
  /* Interchange pill: the other lines' colours joined into one capsule, as on a metro map. */
  .transfer {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.125rem 0.125rem 0.125rem 0.625rem;
    border: 1.5px solid var(--foreground);
    border-radius: 999px;
  }
  .transfer-label {
    font-size: var(--fs-sm);
    font-weight: var(--weight-regular);
    color: var(--muted-foreground);
  }
  .badge {
    position: relative;
    display: inline-grid;
    place-items: center;
    min-width: 1.75rem;
    height: 1.75rem;
    padding-inline: 0.375rem;
    border-radius: 999px;
    font-size: var(--fs-sm);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  /* 44px hit area around the small badge. */
  .badge::after {
    content: '';
    position: absolute;
    inset: -0.5rem;
  }
  /* Minutes from the first stop, right-aligned like an IDOS / SBB stop list. */
  .leg {
    position: absolute;
    right: 0;
    top: 50%;
    transform: translateY(-50%);
    color: var(--foreground);
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .leg-head {
    max-width: 44rem;
    margin-top: 0.75rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    text-align: right;
  }
  .chev {
    margin-left: 0.375rem;
    color: var(--muted-foreground);
  }
  .diagram li:hover .chev {
    color: var(--foreground);
  }
  .trips-title {
    margin-top: 3rem;
  }
  .bus {
    position: absolute;
    z-index: 2;
    left: -0.25rem;
    top: calc(50% + var(--share) * 100%);
    width: 2.5rem;
    transform: translateY(-50%);
    transition: top var(--motion-xslow) var(--ease-out);
    filter: drop-shadow(0 2px 4px rgb(0 0 0 / 0.25));
  }
  .note {
    font-size: var(--fs-sm);
    font-weight: var(--weight-regular);
    color: var(--muted-foreground);
  }
  .diagram a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: var(--radius-sm);
  }

  @media (max-width: 639px) {
    .plate {
      min-width: 3.25rem;
      height: 3.25rem;
      font-size: var(--fs-xl);
    }
    .directions {
      max-width: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .directions a {
      transition: none;
    }
  }
</style>
