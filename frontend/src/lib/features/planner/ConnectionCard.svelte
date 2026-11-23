<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight, ChevronDown, Footprints, Route, Clock as TripIcon } from '@lucide/svelte';
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
    ontoggle
  }: {
    connection: Connection;
    id: string;
    open?: boolean;
    /** "za 5 min" when searching from now. */
    countdown?: string | null;
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
  const longWait = $derived(
    connection.legs.some((l, i) => i > 0 && (l.transfer_minutes ?? 0) >= LONG_WAIT)
  );

  const spoken = $derived(
    `Odjezd ${connection.departure_time} ze zastávky ${first.from.name}, příjezd ` +
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
      {#if dayNote(connection.arrival_day_offset) || dayNote(connection.departure_day_offset)}
        <span class="day"
          >{connection.departure_day_offset !== 0
            ? dayNote(connection.departure_day_offset)
            : `příjezd ${dayNote(connection.arrival_day_offset)}`}</span
        >
      {/if}
    </span>
    <span class="facts" aria-hidden="true">
      {#if countdown}<span class="countdown">{countdown}</span>{/if}
      <span><strong>{duration(connection.duration_minutes)}</strong></span>
      <span class:direct={connection.transfers === 0}>{transfersLabel(connection.transfers)}</span>
      {#if longWait}<span class="long-flag">dlouhé čekání</span>{/if}
    </span>
    <span class="bar" aria-hidden="true">
      {#each bar as part, index (index)}
        {#if part.kind === 'ride'}
          <span
            class="bar-ride"
            style:--line={part.leg.line.color}
            style:flex-grow={Math.max(part.share, 0.04)}
          ></span>
        {:else}
          <span class="bar-wait" class:long={part.long} style:flex-grow={Math.max(part.share, 0.03)}
          ></span>
        {/if}
      {/each}
    </span>
    <span class="strip" aria-hidden="true">
      {#each connection.legs as leg, index (index)}
        {#if index > 0}
          <span class="change" class:long={(leg.transfer_minutes ?? 0) >= LONG_WAIT}>
            <Footprints class="size-3.5" />
            {leg.transfer_minutes} min
          </span>
        {/if}
        <span class="strip-leg">
          <LinePlate number={leg.line.number} color={leg.line.color} size="sm" />
          <span class="strip-text">
            <span class="strip-time">{leg.departure_time}</span>
            {leg.from.name}
          </span>
        </span>
      {/each}
      <span class="strip-end">
        <span class="strip-time">{connection.arrival_time}</span>
        {last.to.name}
      </span>
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
          <p class="transfer">
            <Footprints class="size-4" aria-hidden="true" />
            <span>
              Přestup v zastávce <a href={stopHref(leg.from.id)}>{leg.from.name}</a>:
              <strong>{leg.transfer_minutes} min</strong> na přestup
            </span>
          </p>
        {/if}
        <section class="leg" aria-label={`Úsek ${index + 1}: linka ${leg.line.number}`}>
          <header class="leg-head">
            <LinePlate number={leg.line.number} color={leg.line.color} size="md" label />
            <div class="leg-title">
              <p class="leg-kind">Úsek {index + 1} · {duration(leg.duration_minutes)}</p>
              <p class="leg-name">
                Linka {leg.line.number} <span class="muted">směr</span>
                {leg.destination.name}
              </p>
            </div>
            <nav class="leg-links" aria-label={`Odkazy úseku ${index + 1}`}>
              <a href={tripHref(leg)}><TripIcon class="size-4" aria-hidden="true" /> Spoj</a>
              <a href={lineDirectionHref(leg.line.id, leg.direction, leg.from.id)}
                ><Route class="size-4" aria-hidden="true" /> Linka {leg.line.number}</a
              >
            </nav>
          </header>
          <ol class="calls" style:--line={leg.line.color}>
            {#each leg.stops as call, at (call.stop.id)}
              {@const board = at === 0}
              {@const alight = at === leg.stops.length - 1}
              <li class:edge={board || alight}>
                <span class="call-time tabular">
                  {#if board}
                    {#if waits(call)}<span class="sub">příj. {call.arrival_time}</span>{/if}
                    <span class="main"><span class="sub">odj.</span> {call.departure_time}</span>
                  {:else if alight}
                    <span class="main"><span class="sub">příj.</span> {call.arrival_time}</span>
                  {:else if waits(call)}
                    <span class="sub">příj. {call.arrival_time}</span>
                    <span class="sub">odj. {call.departure_time}</span>
                  {:else}
                    <span>{call.departure_time}</span>
                  {/if}
                  {#if dayNote(alight ? call.arrival_day_offset : call.departure_day_offset)}
                    <span class="sub day-sub"
                      >{dayNote(alight ? call.arrival_day_offset : call.departure_day_offset)}</span
                    >
                  {/if}
                </span>
                <span class="dot" aria-hidden="true"></span>
                <span class="call-name">
                  <a href={stopHref(call.stop.id)}>{call.stop.name}</a>
                  {#if board}<span class="tag">nástup</span>{/if}
                  {#if alight}<span class="tag">výstup</span>{/if}
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
    border-radius: var(--radius-xl);
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
    grid-template-areas: 'times facts' 'bar bar' 'strip toggle';
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
    border-radius: var(--radius-xl);
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
  .day {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-full);
    background: var(--muted);
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
  }
  .facts {
    grid-area: facts;
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    align-items: center;
    gap: 0.25rem 0.75rem;
    color: var(--muted-foreground);
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
  .countdown {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-full);
    background: color-mix(in oklch, var(--primary) 14%, var(--background));
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .strip {
    grid-area: strip;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.375rem 0.625rem;
    font-size: var(--fs-sm);
  }
  .strip-leg {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
  }
  .strip-text,
  .strip-end {
    color: var(--muted-foreground);
    white-space: nowrap;
  }
  .strip-time {
    color: var(--foreground);
    font-weight: var(--weight-strong);
    font-variant-numeric: tabular-nums;
  }
  .strip-end::before {
    content: '→ ';
  }
  .change {
    display: inline-flex;
    align-items: center;
    gap: 0.2rem;
    padding: 0.1rem 0.4rem;
    border: 1px dashed var(--border);
    border-radius: var(--radius-sm);
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
  }
  .change.long {
    border-color: var(--destructive);
    color: var(--destructive);
    font-weight: var(--weight-strong);
  }
  .long-flag {
    padding: 0.1rem 0.5rem;
    border-radius: var(--radius-full);
    background: color-mix(in oklch, var(--destructive) 12%, var(--background));
    color: var(--destructive);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
  }
  /* The journey bar: one segment per ride in its line colour, waits dashed grey. */
  .bar {
    grid-area: bar;
    display: flex;
    align-items: center;
    gap: 3px;
    height: 0.625rem;
  }
  .bar-ride {
    flex-basis: 0;
    height: 100%;
    border-radius: var(--radius-full);
    background: var(--line);
    transform-origin: left;
    animation: bar-in var(--motion-normal) var(--ease-out) both;
  }
  .bar-wait {
    flex-basis: 0;
    height: 0;
    border-top: 2px dashed var(--muted-foreground);
    opacity: 0.6;
  }
  .bar-wait.long {
    border-top-color: var(--destructive);
    opacity: 1;
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
  .leg-title {
    flex: 1 1 12rem;
    min-width: 0;
  }
  .leg-kind {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .leg-name {
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
  }
  .muted {
    color: var(--muted-foreground);
    font-weight: var(--weight-regular);
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
    border-radius: var(--radius-md);
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
    grid-template-columns: 6.5rem 1.25rem minmax(0, 1fr);
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
    left: calc(6.5rem + 0.625rem + 0.625rem - 3px);
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
  .sub {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-regular);
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
  .tag {
    padding: 0.05rem 0.4rem;
    border-radius: var(--radius-sm);
    background: var(--muted);
    color: var(--muted-foreground);
    font-size: var(--fs-2xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .transfer {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-left: 7.75rem;
    padding: 0.5rem 0.75rem;
    border: 1px dashed var(--border);
    border-radius: var(--radius-md);
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
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
      grid-template-areas: 'times' 'facts' 'bar' 'strip' 'toggle';
      padding: 0.875rem 1rem;
    }
    .facts {
      justify-content: flex-start;
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
