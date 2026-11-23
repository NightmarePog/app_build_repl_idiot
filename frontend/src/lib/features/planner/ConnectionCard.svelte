<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight, Bus, ChevronDown, Hourglass, TriangleAlert } from '@lucide/svelte';
  import type { Connection, ConnectionLeg, ConnectionStopTime } from '$lib/api/generated';
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import { duration } from '$lib/features/timetable/time';
  import { inkFor } from '$lib/features/lines/line-style';
  import { lineDirectionHref, withQuery } from '$lib/links';
  import {
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

  /** Arrival and departure differ: the bus waits here (Syntax Square), show both. */
  const waits = (call: ConnectionStopTime) =>
    call.arrival_time !== call.departure_time ||
    call.arrival_day_offset !== call.departure_day_offset;

  const bar = $derived(journeyBar(connection));
  const maxWait = $derived(longestWait(connection));
  /** "+1" / "−1" next to a time on another day than the search day. */
  const dayMark = (offset: number) => (offset > 0 ? `+${offset}` : offset < 0 ? `−${-offset}` : '');
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
      <span class="time"
        >{connection.departure_time}{#if dayMark(connection.departure_day_offset)}<sup
            class="day-mark">{dayMark(connection.departure_day_offset)}</sup
          >{/if}</span
      >
      <ArrowRight class="size-5 arrow" />
      <span class="time"
        >{connection.arrival_time}{#if dayMark(connection.arrival_day_offset)}<sup class="day-mark"
            >{dayMark(connection.arrival_day_offset)}</sup
          >{/if}</span
      >
    </span>
    <span class="facts" aria-hidden="true">
      {#if fastest}<span class="fastest">Nejrychlejší</span>{/if}
      {#if maxWait >= LONG_WAIT}
        <span class="long-wait"
          ><TriangleAlert class="size-4" aria-hidden="true" /> dlouhé čekání {duration(
            maxWait
          )}</span
        >
      {/if}
      {#if countdown}<span class="countdown">{countdown}</span>{/if}
      <span class="duration-chip"
        >{duration(connection.duration_minutes)} · {transfersLabel(connection.transfers)}</span
      >
    </span>
    <span class="bar" aria-hidden="true">
      {#each bar as part, index (index)}
        {#if part.kind === 'ride'}
          <span class="bar-leg" style:flex-grow={Math.max(part.share, 0.12)}>
            <span
              class="bar-ride"
              style:--line={part.leg.line.color}
              style:--line-ink={inkFor(part.leg.line.color)}
            >
              <LinePlate number={part.leg.line.number} color={part.leg.line.color} size="xs" />
              <span class="bar-vehicle">{vehicle(part.leg)}</span>
            </span>
            <span class="bar-times">
              <span
                >{part.leg.departure_time}<sup class="day-mark"
                  >{dayMark(part.leg.departure_day_offset)}</sup
                ></span
              >
              <span
                >{part.leg.arrival_time}<sup class="day-mark"
                  >{dayMark(part.leg.arrival_day_offset)}</sup
                ></span
              >
            </span>
          </span>
        {:else}
          {@const at = connection.legs[bar.slice(0, index).filter((p) => p.kind === 'ride').length]}
          <span class="bar-wait" class:long={part.long} style:flex-grow={Math.max(part.share, 0.1)}>
            <span class="bar-gap">
              {#if part.long}<span class="bar-break">//</span>{/if}
            </span>
            <span class="bar-wait-label">
              {#if part.long}<TriangleAlert class="size-3.5 warn" />{/if}
              <span class="bar-stop">{at.from.name}</span>
              <span>{duration(part.minutes)}</span>
            </span>
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
            {#if (leg.transfer_minutes ?? 0) >= LONG_WAIT}
              <TriangleAlert class="size-4 warn" aria-hidden="true" />
            {:else}
              <Hourglass class="size-4" aria-hidden="true" />
            {/if}
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
                  {#if !alight && !board && waits(call)}
                    <span class="pair"
                      ><span class="sr-only">příjezd</span>{call.arrival_time}<span
                        aria-hidden="true"
                      >
                        –
                      </span><span class="sr-only">odjezd</span>{call.departure_time}</span
                    >
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
  .long-wait {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.125rem 0.5rem;
    border: 1px solid var(--destructive);
    border-radius: var(--radius);
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .day-mark {
    margin-left: 0.125rem;
    font-size: 0.6em;
    font-weight: var(--weight-strong);
    vertical-align: super;
  }
  .day-mark:empty {
    display: none;
  }
  .conn :global(.warn) {
    flex-shrink: 0;
    color: var(--destructive);
  }
  .countdown {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius);
    background: color-mix(in oklch, var(--primary) 14%, var(--background));
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  /* The journey bar: one segment per ride in its line colour, times always under its two ends. */
  .bar {
    grid-area: bar;
    display: flex;
    align-items: flex-start;
    gap: 3px;
  }
  .bar-leg {
    display: grid;
    gap: 0.25rem;
    flex-basis: 0;
    min-width: 6.5rem;
  }
  .bar-ride {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    height: 2.25rem;
    padding: 0 0.375rem;
    overflow: hidden;
    border-radius: var(--radius);
    background: var(--line);
    color: var(--line-ink);
    transform-origin: left;
    animation: bar-in var(--motion-normal) var(--ease-out) both;
  }
  .bar-vehicle {
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }
  .bar-times {
    display: flex;
    justify-content: space-between;
    gap: 0.25rem;
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .bar-wait {
    display: grid;
    gap: 0.25rem;
    flex-basis: 0;
    min-width: 5rem;
    font-size: var(--fs-xs);
    font-variant-numeric: tabular-nums;
  }
  .bar-gap {
    display: grid;
    place-items: center;
    height: 2.25rem;
    background: linear-gradient(var(--border), var(--border)) center / 100% 2px no-repeat;
  }
  .bar-wait.long .bar-gap {
    background-image: repeating-linear-gradient(
      90deg,
      var(--destructive) 0 4px,
      transparent 4px 8px
    );
  }
  .bar-break {
    padding: 0 0.125rem;
    background: var(--card);
    color: var(--foreground);
    font-weight: var(--weight-strong);
    line-height: 1;
  }
  .bar-wait-label {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    align-items: center;
    gap: 0 0.25rem;
    text-align: center;
    color: var(--foreground);
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
    grid-template-columns: 7rem 1.25rem minmax(0, 1fr);
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
    left: calc(7rem + 0.625rem + 0.625rem - 3px);
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
  .call-time .pair {
    white-space: nowrap;
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
    margin-left: 8.25rem;
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
    .bar-leg {
      min-width: 6.75rem;
    }
    .bar-wait {
      min-width: 3.5rem;
    }
    .toggle {
      justify-self: start;
    }
    .detail {
      padding-inline: 1rem;
    }
    .calls li {
      grid-template-columns: 7rem 1.25rem minmax(0, 1fr);
    }
    .calls li::before {
      left: calc(7rem + 0.625rem + 0.625rem - 3px);
    }
    .transfer {
      margin-left: 0;
    }
  }
</style>
