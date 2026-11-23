<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight, Bus, ChevronDown, Hourglass, Moon } from '@lucide/svelte';
  import type { Connection, ConnectionLeg, ConnectionStopTime } from '$lib/api/generated';
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import { duration } from '$lib/features/timetable/time';
  import { inkFor } from '$lib/features/lines/line-style';
  import { lineDirectionHref, withQuery } from '$lib/links';
  import {
    absolute,
    dayNote,
    journeyBar,
    LONG_WAIT,
    longestWait,
    transfersLabel,
    vehicleLabel
  } from './planner';

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

  const bar = $derived(journeyBar(connection));
  const maxWait = $derived(longestWait(connection));
  /** Index of the leg a bar part belongs to (a wait belongs to the leg after it). */
  const rideIndex = (partIndex: number) =>
    bar.slice(0, partIndex).filter((p) => p.kind === 'ride').length;
  /** The leg at `index` leaves on a later day than the previous one arrived: a night wait. */
  const overnight = (index: number) =>
    index > 0 &&
    connection.legs[index].departure_day_offset > connection.legs[index - 1].arrival_day_offset;
  const arrivalNextDay = $derived(connection.arrival_day_offset > connection.departure_day_offset);
  /** Minutes the bus stands at a stop; 0 when arrival and departure are the same. */
  const dwell = (call: ConnectionStopTime) =>
    absolute(call.departure_time, call.departure_day_offset) -
    absolute(call.arrival_time, call.arrival_day_offset);
  /** The one vehicle name used on every page: "Bus B03". */
  const vehicle = (leg: ConnectionLeg) => vehicleLabel(leg.block_ref) ?? `Linka ${leg.line.number}`;

  const spoken = $derived(
    `Odjezd ${dayNote(connection.departure_day_offset)} ${connection.departure_time} ze zastávky ${first.from.name}, příjezd ` +
      `${connection.arrival_time} do ${last.to.name}, ${duration(connection.duration_minutes)}, ` +
      `${transfersLabel(connection.transfers)}, linky ${connection.legs
        .map((l) => [l.line.number, vehicleLabel(l.block_ref)].filter(Boolean).join(' '))
        .join(', ')}${maxWait >= LONG_WAIT ? `, dlouhé čekání ${duration(maxWait)}` : ''}`
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
      {#if arrivalNextDay}<span class="next-day"
          ><Moon class="size-4" />příjezd {dayNote(
            connection.arrival_day_offset - connection.departure_day_offset
          )}</span
        >{/if}
    </span>
    <span class="facts" aria-hidden="true">
      {#if countdown}<span class="countdown">{countdown}</span>{/if}
      {#if fastest}<span class="fastest">Nejrychlejší</span>{/if}
      <span class="duration-chip"
        >{duration(connection.duration_minutes)} · {transfersLabel(connection.transfers)}</span
      >
    </span>
    <span class="bar" aria-hidden="true">
      {#each bar as part, index (index)}
        {#if part.kind === 'ride'}
          {@const at = rideIndex(index)}
          <span class="bar-leg" style:flex-grow={Math.max(part.share, 0.14)}>
            <span
              class="bar-ride"
              style:--line={part.leg.line.color}
              style:--line-ink={inkFor(part.leg.line.color)}
            >
              <LinePlate number={part.leg.line.number} color={part.leg.line.color} size="sm" />
              <span class="bar-vehicle">{vehicle(part.leg)}</span>
            </span>
            <span class="bar-ends">
              {#if at === 0}<span class="bar-end start">{part.leg.from.name}</span>{/if}
              {#if at === connection.legs.length - 1}<span class="bar-end finish"
                  >{part.leg.to.name}</span
                >{/if}
            </span>
          </span>
        {:else}
          {@const next = connection.legs[rideIndex(index)]}
          {@const night = overnight(rideIndex(index))}
          <span
            class="bar-wait"
            class:long={part.long}
            style:flex-grow={Math.max(part.share, part.long ? 0.22 : 0.1)}
          >
            <span class="bar-gap"><span class="node"></span></span>
            <span class="wait-chip" class:long={part.long}>
              {#if night}<Moon class="size-4" />{:else}<Hourglass class="size-4" />{/if}
              <span class="bar-stop">{next.from.name}</span>
              <span class="wait-time">{night ? 'přes noc · ' : ''}{duration(part.minutes)}</span>
            </span>
          </span>
        {/if}
      {/each}
    </span>
    <span class="toggle" aria-hidden="true">
      {open ? 'Skrýt' : 'Zastávky a časy'}
      <ChevronDown class="size-5 chevron" />
    </span>
  </button>

  {#if open}
    <div class="detail" id={`${id}-detail`}>
      {#each connection.legs as leg, index (index)}
        {#if index > 0}
          <p class="transfer" class:long={(leg.transfer_minutes ?? 0) >= LONG_WAIT}>
            {#if overnight(index)}
              <Moon class="size-5" aria-hidden="true" />
            {:else}
              <Hourglass class="size-5" aria-hidden="true" />
            {/if}
            <span>
              Přestup <strong>{duration(leg.transfer_minutes ?? 0)}</strong>{overnight(index)
                ? ' přes noc'
                : ''} na zastávce
              <a href={stopHref(leg.from.id)}>{leg.from.name}</a>
            </span>
          </p>
        {/if}
        <section class="leg" aria-label={`Úsek ${index + 1}: linka ${leg.line.number}`}>
          <header class="leg-head">
            <LinePlate number={leg.line.number} color={leg.line.color} size="md" label />
            <span class="leg-vehicle"><Bus class="size-4" aria-hidden="true" />{vehicle(leg)}</span>
            <p class="leg-name">
              <ArrowRight class="size-4 leg-arrow" aria-hidden="true" />
              <span class="sr-only">směr</span>
              {leg.destination.name}
            </p>
            <span class="leg-duration">{duration(leg.duration_minutes)}</span>
            <nav class="leg-links" aria-label={`Odkazy úseku ${index + 1}`}>
              <a href={tripHref(leg)}
                ><Bus class="size-4" aria-hidden="true" />
                Spoj {vehicle(leg)}</a
              >
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
                  {alight ? call.arrival_time : call.departure_time}
                  {#if dayNote(alight ? call.arrival_day_offset : call.departure_day_offset) && (at === 0 || dayNote(call.departure_day_offset) !== dayNote(leg.stops[at - 1].departure_day_offset))}
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
                  {#if !board && !alight && dwell(call) > 0}
                    <span class="dwell"
                      ><span class="sr-only">příjezd {call.arrival_time}, </span>stojí {duration(
                        dwell(call)
                      )}</span
                    >
                  {/if}
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
  .fastest {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: var(--primary);
    color: var(--primary-foreground);
    font-weight: var(--weight-strong);
  }
  .duration-chip {
    padding: 0.125rem 0.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .countdown {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: color-mix(in oklch, var(--primary) 14%, var(--background));
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .next-day {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    margin-left: 0.25rem;
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: var(--muted);
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  /* The journey bar: one segment per ride in its line colour, a stop node at each change. */
  .bar {
    grid-area: bar;
    display: flex;
    align-items: flex-start;
  }
  .bar-leg {
    display: grid;
    gap: 0.375rem;
    flex-basis: 0;
    min-width: 7rem;
  }
  .bar-ride {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    height: 2.5rem;
    padding: 0 0.375rem;
    overflow: hidden;
    border-radius: var(--radius);
    background: var(--line);
    color: var(--line-ink);
    transform-origin: left;
    animation: bar-in var(--motion-normal) var(--ease-out) both;
  }
  .bar-ride :global(.plate) {
    box-shadow: 0 0 0 2px var(--card);
  }
  .bar-vehicle {
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }
  .bar-ends {
    display: flex;
    justify-content: space-between;
    gap: 0.5rem;
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .bar-end.finish {
    margin-left: auto;
    text-align: right;
  }
  .bar-wait {
    display: grid;
    justify-items: center;
    gap: 0.375rem;
    flex-basis: 0;
    min-width: 6rem;
  }
  .bar-gap {
    position: relative;
    display: grid;
    place-items: center;
    width: 100%;
    height: 2.5rem;
    background: linear-gradient(var(--foreground), var(--foreground)) center / 100% 2px no-repeat;
  }
  /* A long wait is time, not an error: a hatched stretch of the bar, wider than a short one. */
  .bar-wait.long .bar-gap {
    background: repeating-linear-gradient(
        -45deg,
        color-mix(in oklch, var(--foreground) 22%, transparent) 0 6px,
        transparent 6px 12px
      )
      center / 100% 1.25rem no-repeat;
  }
  .node {
    width: 1rem;
    height: 1rem;
    border: 3px solid var(--foreground);
    border-radius: 50%;
    background: var(--card);
  }
  .wait-chip {
    display: inline-flex;
    flex-wrap: wrap;
    justify-content: center;
    align-items: center;
    gap: 0 0.375rem;
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: var(--muted);
    color: var(--foreground);
    font-size: var(--fs-base);
    font-variant-numeric: tabular-nums;
    text-align: center;
  }
  .bar-stop {
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
    color: var(--foreground);
    font-size: var(--fs-base);
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
  .leg-vehicle {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .leg-name :global(.leg-arrow) {
    display: inline;
    vertical-align: -0.125em;
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
  .edge .call-time {
    font-weight: var(--weight-strong);
  }
  .dwell {
    padding: 0 0.375rem;
    border-radius: var(--radius);
    background: var(--muted);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .day-sub {
    font-size: var(--fs-sm);
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
    .bar {
      flex-direction: column;
      align-items: stretch;
    }
    .bar-leg,
    .bar-wait {
      min-width: 0;
    }
    .bar-wait {
      grid-template-columns: 2.5rem auto;
      justify-content: start;
      align-items: center;
    }
    .bar-gap {
      width: 2.5rem;
      height: 2.5rem;
      background-size: 2px 100%;
    }
    .bar-wait.long .bar-gap {
      background-size: 1.25rem 100%;
    }
    .toggle {
      justify-self: start;
    }
    .detail {
      padding-inline: 1rem;
    }
    .transfer {
      margin-left: 0;
    }
  }
</style>
