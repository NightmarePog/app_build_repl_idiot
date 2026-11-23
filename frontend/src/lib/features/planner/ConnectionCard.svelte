<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight, ChevronDown, Hourglass, Clock as TripIcon } from '@lucide/svelte';
  import type { Connection, ConnectionLeg, ConnectionStopTime } from '$lib/api/generated';
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import { duration } from '$lib/features/timetable/time';
  import { lineDirectionHref, withQuery } from '$lib/links';
  import { dayNote, journeyBar, LONG_WAIT, transfersLabel } from './planner';

  /**
   * One search result, IDOS style: times, duration, changes and the line plates up front; the
   * detail lists every leg with each stop and time, the waits between legs and links onward.
   */
  let {
    connection,
    id,
    open = false,
    countdown = null,
    fastest = false,
    ontoggle
  }: {
    connection: Connection;
    id: string;
    open?: boolean;
    /** "za 5 min" when searching from now. */
    countdown?: string | null;
    /** The shortest travel time of the list: the one to pick. */
    fastest?: boolean;
    ontoggle: () => void;
  } = $props();

  const first = $derived(connection.legs[0]);
  const last = $derived(connection.legs.at(-1)!);

  const stopHref = (stopId: number) => resolve('/stops/[id]', { id: String(stopId) });
  const tripHref = (leg: ConnectionLeg) =>
    withQuery(resolve('/trips/[id]', { id: String(leg.trip_id) }), {
      od: leg.from.id,
      do: leg.to.id
    });

  /** Arrival and departure differ: the bus waits here (Syntax Square), show both. */
  const waits = (call: ConnectionStopTime) =>
    call.arrival_time !== call.departure_time ||
    call.arrival_day_offset !== call.departure_day_offset;

  const bar = $derived(journeyBar(connection));

  const spoken = $derived(
    `Odjezd ${dayNote(connection.departure_day_offset)} ${connection.departure_time} ze zastávky ${first.from.name}, příjezd ` +
      `${connection.arrival_time} do ${last.to.name}, ${duration(connection.duration_minutes)}, ` +
      `${transfersLabel(connection.transfers)}, linky ${connection.legs.map((l) => l.line.number).join(', ')}`
  );
</script>

<article class="conn" class:open aria-labelledby={`${id}-summary`}>
  <button
    type="button"
    class="summary"
    id={`${id}-summary`}
    aria-expanded={open}
    aria-controls={`${id}-detail`}
    onclick={ontoggle}
  >
    <span class="sr-only">{spoken}</span>
    <span class="times" aria-hidden="true">
      <span class="time">{connection.departure_time}</span>
      <ArrowRight class="size-5 arrow" />
      <span class="time">{connection.arrival_time}</span>
    </span>
    <span class="facts" aria-hidden="true">
      {#if fastest}<span class="fastest">Nejrychlejší</span>{/if}
      {#if countdown}<span class="countdown">{countdown}</span>{/if}
      <span><strong>{duration(connection.duration_minutes)}</strong></span>
      <span class:direct={connection.transfers === 0}>{transfersLabel(connection.transfers)}</span>
    </span>
    <span class="bar" aria-hidden="true">
      {#each bar as part, index (index)}
        {#if part.kind === 'ride'}
          <span
            class="bar-ride"
            style:--line={part.leg.line.color}
            style:flex-grow={Math.max(part.share, 0.12)}
          >
            <span class="bar-tick">{part.leg.departure_time}</span>
            <span class="bar-badge">{part.leg.line.number}</span>
            <span class="bar-tick">{part.leg.arrival_time}</span>
          </span>
        {:else}
          <span
            class="bar-wait"
            class:long={part.long}
            style:flex-grow={Math.max(part.share, 0.08)}
          >
            <span class="bar-wait-label">{duration(part.minutes)}</span>
            <span class="bar-node"></span>
          </span>
        {/if}
      {/each}
    </span>
    <span class="toggle" aria-hidden="true">
      {open ? 'Skrýt detail' : 'Detail spojení'}
      <ChevronDown class="size-4 chevron" />
    </span>
  </button>

  {#if open}
    <div class="detail" id={`${id}-detail`}>
      {#each connection.legs as leg, index (index)}
        {#if index > 0}
          <p class="transfer" class:long={(leg.transfer_minutes ?? 0) >= LONG_WAIT}>
            <Hourglass class="size-4" aria-hidden="true" />
            <span>
              Přestup <strong>{duration(leg.transfer_minutes ?? 0)}</strong> na zastávce
              <a href={stopHref(leg.from.id)}>{leg.from.name}</a>
              {#if (leg.transfer_minutes ?? 0) >= LONG_WAIT}
                · dlouhé čekání na zastávce{dayNote(leg.departure_day_offset) &&
                leg.departure_day_offset !== connection.legs[index - 1].arrival_day_offset
                  ? ', přes noc'
                  : ''}
              {/if}
            </span>
          </p>
        {/if}
        <section class="leg" aria-label={`Úsek ${index + 1}: linka ${leg.line.number}`}>
          <header class="leg-head">
            <LinePlate number={leg.line.number} color={leg.line.color} size="md" label />
            <p class="leg-name">
              <span class="leg-dir">směr</span>
              {leg.destination.name}
            </p>
            <span class="leg-duration">{duration(leg.duration_minutes)}</span>
            <nav class="leg-links" aria-label={`Odkazy úseku ${index + 1}`}>
              <a href={tripHref(leg)}><TripIcon class="size-4" aria-hidden="true" /> Spoj</a>
              <a href={lineDirectionHref(leg.line.id, leg.direction, leg.from.id)}
                ><LinePlate number={leg.line.number} color={leg.line.color} size="sm" /> Linka {leg
                  .line.number}</a
              >
            </nav>
          </header>
          <ol class="calls" style:--line={leg.line.color}>
            {#each leg.stops as call, at (call.stop.id)}
              {@const board = at === 0}
              {@const alight = at === leg.stops.length - 1}
              <li class:edge={board || alight}>
                <span class="call-time tabular">
                  {#if !alight && waits(call)}
                    <span class="arr">příj. {call.arrival_time}</span>
                    <span class:main={board}>{call.departure_time}</span>
                  {:else}
                    <span class="main">{alight ? call.arrival_time : call.departure_time}</span>
                  {/if}
                  {#if dayNote(alight ? call.arrival_day_offset : call.departure_day_offset)}
                    <span class="day-sub"
                      >{dayNote(alight ? call.arrival_day_offset : call.departure_day_offset)}</span
                    >
                  {/if}
                </span>
                <span class="dot" aria-hidden="true"></span>
                <span class="call-name">
                  <a href={stopHref(call.stop.id)}>{call.stop.name}</a>
                  {#if board}<span class="sr-only">(nástup)</span>{/if}
                  {#if alight}<span class="sr-only">(výstup)</span>{/if}
                </span>
              </li>
            {/each}
          </ol>
        </section>
      {/each}
    </div>
  {/if}
</article>

<style>
  .conn {
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    overflow: hidden;
    transition: box-shadow var(--motion-fast) var(--ease-out);
  }
  .conn:hover,
  .conn.open {
    box-shadow: var(--shadow-raised);
  }
  .summary {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas: 'times facts' 'bar bar' '. toggle';
    align-items: center;
    gap: 0.625rem 1rem;
    width: 100%;
    padding: 1rem 1.25rem;
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    text-align: left;
    cursor: pointer;
  }
  .summary:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -2px;
    border-radius: var(--radius);
  }
  .times {
    grid-area: times;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.5rem;
  }
  .time {
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    font-variant-numeric: tabular-nums;
    line-height: 1;
  }
  .times :global(.arrow) {
    color: var(--muted-foreground);
  }
  .facts {
    grid-area: facts;
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    align-items: center;
    gap: 0.25rem 0.75rem;
    color: var(--foreground);
    font-size: var(--fs-sm);
  }
  .facts strong {
    color: var(--foreground);
    font-size: var(--fs-base);
  }
  .direct {
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .fastest {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: var(--primary);
    color: var(--primary-foreground);
    font-weight: var(--weight-strong);
  }
  .countdown {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: color-mix(in oklch, var(--primary) 14%, var(--background));
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  /* The journey bar: one segment per ride in its line colour, waits dashed grey. */
  .bar {
    grid-area: bar;
    display: flex;
    align-items: center;
    gap: 3px;
    min-height: 1.75rem;
  }
  .bar-ride {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.25rem;
    flex-basis: 0;
    min-width: 4.5rem;
    height: 1.75rem;
    padding: 0 0.375rem;
    border-radius: var(--radius);
    background: var(--line);
    color: var(--primary-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
    transform-origin: left;
    min-height: 1.75rem;
    animation: bar-in var(--motion-normal) var(--ease-out) both;
  }
  .bar-badge {
    font-size: var(--fs-sm);
  }
  .bar-wait {
    display: flex;
    justify-content: center;
    flex-basis: 0;
    min-width: 3.5rem;
    position: relative;
    align-items: center;
    height: 0.625rem;
    border-radius: var(--radius);
    background: repeating-linear-gradient(135deg, var(--muted) 0 4px, var(--border) 4px 6px);
    margin-top: 1.25rem;
    font-size: var(--fs-xs);
    font-variant-numeric: tabular-nums;
  }
  .bar-wait-label {
    position: absolute;
    bottom: 100%;
    white-space: nowrap;
    color: var(--muted-foreground);
  }
  /* The transfer stop: a node where the wait meets the next ride. */
  .bar-node {
    position: absolute;
    right: -0.5rem;
    width: 0.75rem;
    height: 0.75rem;
    border: 2.5px solid var(--foreground);
    border-radius: 50%;
    background: var(--background);
  }
  .bar-wait.long {
    font-weight: var(--weight-strong);
  }
  @keyframes bar-in {
    from {
      transform: scaleX(0);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .bar-ride {
      animation: none;
    }
  }
  .toggle {
    grid-area: toggle;
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    justify-self: end;
    color: var(--primary);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    white-space: nowrap;
  }
  .toggle :global(.chevron) {
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .open .toggle :global(.chevron) {
    transform: rotate(180deg);
  }
  .detail {
    display: grid;
    gap: 0.75rem;
    padding: 0.25rem 1.25rem 1.25rem;
    border-top: 1px solid var(--border);
  }
  .leg {
    display: grid;
    gap: 0.5rem;
    padding-top: 0.75rem;
  }
  .leg-head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 0.875rem;
  }
  .leg-dir {
    margin-right: 0.25rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: normal;
  }
  .leg-name {
    flex: 1 1 10rem;
    min-width: 0;
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
  }
  .leg-duration {
    padding: 0.125rem 0.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    font-size: var(--fs-sm);
    font-variant-numeric: tabular-nums;
  }
  .leg-links {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .leg-links a {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.5rem;
    padding: 0 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .leg-links a:hover {
    border-color: var(--input);
  }
  .calls {
    display: grid;
    margin: 0;
    padding: 0;
    list-style: none;
  }
  .calls li {
    position: relative;
    display: grid;
    grid-template-columns: 4.5rem 1.25rem minmax(0, 1fr);
    align-items: center;
    gap: 0.625rem;
    min-height: 2.5rem;
  }
  /* The line's colour runs down between the dots, like the trip page. */
  .calls li::before {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    left: calc(4.5rem + 0.625rem + 0.625rem - 3px);
    width: 6px;
    background: var(--line);
  }
  .calls li:first-child::before {
    top: 50%;
  }
  .calls li:last-child::before {
    bottom: 50%;
  }
  .dot {
    position: relative;
    justify-self: center;
    width: 0.75rem;
    height: 0.75rem;
    border: 2.5px solid var(--foreground);
    border-radius: 50%;
    background: var(--background);
  }
  .edge .dot {
    width: 1rem;
    height: 1rem;
    background: var(--foreground);
  }
  .call-time {
    display: grid;
    justify-items: end;
    font-variant-numeric: tabular-nums;
  }
  .edge .call-time .main {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  .call-time .arr {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
  }
  .day-sub {
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
  }
  .call-name {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.5rem;
  }
  .call-name a {
    text-decoration: underline;
    text-decoration-color: color-mix(in oklch, currentColor 30%, transparent);
    text-underline-offset: 0.2em;
  }
  .edge .call-name a {
    font-weight: var(--weight-strong);
  }
  .transfer {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-left: 5.75rem;
    padding: 0.5rem 0.75rem;
    border-radius: var(--radius);
    background: var(--muted);
    font-size: var(--fs-sm);
  }
  .transfer.long {
    font-weight: var(--weight-strong);
  }
  .transfer a {
    color: var(--foreground);
    font-weight: var(--weight-strong);
    text-decoration: underline;
    text-underline-offset: 0.2em;
  }
  .transfer strong {
    color: var(--foreground);
  }
  @media (max-width: 639px) {
    .summary {
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas: 'times' 'facts' 'bar' 'toggle';
      padding: 0.875rem 1rem;
    }
    .facts {
      justify-content: flex-start;
    }
    /* Narrow: only the line letter fits inside a ride segment. */
    .bar-tick {
      display: none;
    }
    .bar-ride {
      justify-content: center;
      min-width: 2.25rem;
    }
    .toggle {
      justify-self: start;
    }
    .detail {
      padding-inline: 1rem;
    }
    .calls li {
      grid-template-columns: 4.75rem 1.25rem minmax(0, 1fr);
    }
    .calls li::before {
      left: calc(4.75rem + 0.625rem + 0.625rem - 3px);
    }
    .transfer {
      margin-left: 0;
    }
  }
</style>
