<script lang="ts">
  import { untrack } from 'svelte';
  import { resolve } from '$app/paths';
  import { ArrowRight } from '@lucide/svelte';
  import { searchConnections, type Connection } from '$lib/api/generated';
  import { createResource, unwrap } from '$lib/api/resource.svelte';
  import {
    absolute,
    connectionKey,
    plannerHref,
    transfersLabel
  } from '$lib/features/planner/planner';
  import LinePlate from './LinePlate.svelte';
  import { clock as clockText, duration, relative } from './time';
  import { withQuery } from './trips';

  /**
   * Rides from the boarding stop that reach this trip's terminus sooner than this trip does.
   * `arrival` is this trip's next arrival there, in minutes from today's midnight in Prague.
   */
  let {
    from,
    to,
    toName,
    arrival,
    now
  }: { from: number; to: number; toName: string; arrival: number; now: number } = $props();

  const search = createResource(
    (signal) =>
      unwrap(
        searchConnections({
          query: { from, to, time: clockText(untrack(() => now)), mode: 'departure', limit: 8 },
          signal
        })
      ),
    () => `${from}-${to}`
  );

  const sooner = $derived(
    (search.data?.connections ?? [])
      .map((connection) => ({
        connection,
        dep: absolute(connection.departure_time, connection.departure_day_offset),
        arr: absolute(connection.arrival_time, connection.arrival_day_offset)
      }))
      .filter((c) => c.dep >= now && c.arr < arrival)
      .slice(0, 3)
  );

  /** A direct ride opens its trip; one with changes opens the planner on that connection. */
  function href(connection: Connection) {
    const [leg] = connection.legs;
    if (connection.legs.length === 1)
      return withQuery(resolve('/trips/[id]', { id: String(leg.trip_id) }), { od: leg.from.id });
    return plannerHref(
      { from, to, time: connection.departure_time, mode: 'departure' },
      connectionKey(connection)
    );
  }
</script>

{#if sooner.length}
  <section class="sooner" aria-labelledby="sooner-heading">
    <h2 id="sooner-heading" class="label">Jede tam dřív</h2>
    <p class="lead">Dřívější spojení do zastávky {toName}</p>
    <ul>
      {#each sooner as { connection, dep, arr } (connectionKey(connection))}
        <li>
          <a
            href={href(connection)}
            class="row"
            aria-label={`${connection.departure_time} až ${connection.arrival_time}, ${relative(dep - now)}, linka ${connection.legs.map((l) => l.line.number).join(' a ')}, ${transfersLabel(connection.transfers)}, o ${duration(arrival - arr)} dřív`}
          >
            <span class="times tabular">
              <strong>{connection.departure_time}</strong>
              <ArrowRight aria-hidden="true" class="size-3.5" />
              <strong>{connection.arrival_time}</strong>
            </span>
            <span class="when">{relative(dep - now)}</span>
            <span class="plates">
              {#each connection.legs as leg, i (i)}
                <LinePlate number={leg.line.number} color={leg.line.color} size="sm" />
              {/each}
              <span class="transfers">{transfersLabel(connection.transfers)}</span>
            </span>
            <span class="gain">o {duration(arrival - arr)} dřív</span>
          </a>
        </li>
      {/each}
    </ul>
  </section>
{/if}

<style>
  .sooner {
    margin-top: 1rem;
    padding: 1.25rem;
    border: 1px solid var(--border);
    border-top: 6px solid var(--sign-marker);
    border-radius: var(--radius-xl);
    background: var(--card);
  }
  .label {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .lead {
    margin-top: 0.25rem;
    font-weight: var(--weight-strong);
  }
  ul {
    display: grid;
    gap: 0.5rem;
    margin-top: 0.75rem;
  }
  .row {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 0.375rem 0.75rem;
    align-items: center;
    min-height: 3rem;
    padding: 0.625rem 0.75rem;
    border-radius: var(--radius-lg);
    background: var(--secondary);
  }
  .row:hover {
    background: var(--accent);
  }
  .row:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .times {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  .when {
    justify-self: end;
    font-weight: var(--weight-strong);
  }
  .plates {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.375rem;
  }
  .transfers,
  .gain {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .gain {
    justify-self: end;
    color: var(--status-ok);
  }
</style>
