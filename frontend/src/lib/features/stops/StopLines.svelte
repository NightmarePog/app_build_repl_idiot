<script lang="ts">
  import { resolve } from '$app/paths';
  import { getStopLines, type Line, type Route } from '$lib/api/generated';
  import { inkFor, isPale } from '$lib/features/lines/line-style';
  import {
    LINES,
    loadNetwork,
    networkFailed,
    networkReady
  } from '$lib/features/network/lines.svelte';

  let { stopId }: { stopId: number } = $props();

  let lines = $state<Line[] | null>(null);
  let failed = $state(false);
  let attempt = $state(0);

  $effect(() => {
    void attempt;
    lines = null;
    failed = false;
    const controller = new AbortController();
    getStopLines({
      path: { id: stopId },
      throwOnError: true,
      signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)])
    })
      .then(({ data }) => (lines = data))
      .catch(() => {
        if (!controller.signal.aborted) failed = true;
      });
    return () => controller.abort();
  });

  /** Every direction a line leaves this stop in, with the next stop on the way. */
  function departures(number: string) {
    const routes = LINES[number]?.routes ?? [];
    return routes.flatMap((route) => {
      const index = route.stops.findIndex((stop) => stop.id === stopId);
      if (index < 0 || index === route.stops.length - 1) return [];
      return [{ route, next: route.stops[index + 1] }];
    });
  }

  function directionHref(line: Line, route: Route) {
    const base =
      route.bound === 'inbound'
        ? resolve('/lines/[id]?smer=inbound', { id: String(line.id) })
        : resolve('/lines/[id]', { id: String(line.id) });
    return `${base}#stop-${stopId}`;
  }

  function retry() {
    attempt++;
    if (networkFailed()) void loadNetwork(true);
  }
</script>

<section class="stop-lines" aria-labelledby="stop-lines-heading">
  <h2 id="stop-lines-heading">Odjezdy linek</h2>

  {#if failed || networkFailed()}
    <p class="note">
      Linky se nepodařilo načíst.
      <button type="button" class="retry" onclick={retry}>Zkusit znovu</button>
    </p>
  {:else if !lines || !networkReady()}
    <p class="note" role="status">Načítáme linky…</p>
  {:else if lines.length === 0}
    <p class="note">Tudy zatím žádná linka nejezdí.</p>
  {:else}
    <ul class="list">
      {#each lines as line (line.id)}
        {@const trips = departures(line.number)}
        <li
          class="line"
          class:line-cased={isPale(line.color)}
          style:--line={line.color}
          style:--line-ink={inkFor(line.color)}
        >
          <span class="plate tabular" aria-hidden="true">{line.number}</span>
          <div class="rows">
            {#each trips as { route, next } (route.id)}
              {@const lastStop = next.id === route.stops[route.stops.length - 1].id}
              <div class="row">
                <!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- resolved in directionHref -->
                <a class="direction" href={directionHref(line, route)}>
                  <span class="sr-only">Linka {line.number}, </span>směr
                  <strong>{route.direction}</strong>
                </a>
                {#if !lastStop}
                  <span class="next">
                    další
                    <a href={resolve('/stops/[id]', { id: String(next.id) })}>{next.name}</a>
                  </span>
                {/if}
              </div>
            {:else}
              <div class="row">
                <a class="direction" href={resolve('/lines/[id]', { id: String(line.id) })}>
                  <span class="sr-only">Linka {line.number}, </span>konečná zastávka
                </a>
              </div>
            {/each}
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  .stop-lines {
    margin-top: 2rem;
  }
  h2 {
    margin-bottom: 0.5rem;
    font-size: 1.25rem;
    font-weight: var(--font-display-weight);
  }
  .note {
    color: var(--muted-foreground);
  }
  .retry {
    min-height: 2.75rem;
    margin-left: 0.25rem;
    color: var(--primary);
    font-weight: 600;
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .list {
    border-top: 1px solid var(--border);
  }
  .line {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    gap: 1rem;
    align-items: start;
    padding-block: 0.75rem;
    border-bottom: 1px solid var(--border);
  }
  .plate {
    display: grid;
    place-items: center;
    min-width: 2.75rem;
    height: 2.75rem;
    padding-inline: 0.375rem;
    border-radius: 0.3125rem;
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: 1.375rem;
    font-weight: 800;
    line-height: 1;
  }
  .rows {
    display: grid;
  }
  .row {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 0 1rem;
  }
  .direction {
    display: inline-flex;
    align-items: center;
    gap: 0.3em;
    min-height: 2.75rem;
    font-size: 1.0625rem;
    text-underline-offset: 3px;
  }
  .direction strong {
    font-weight: 700;
  }
  .direction:hover {
    text-decoration: underline;
  }
  .next {
    font-size: 0.9375rem;
    font-weight: 500;
    color: var(--muted-foreground);
  }
  .next a {
    color: var(--foreground);
    font-weight: 600;
    text-decoration: underline;
    text-decoration-color: var(--border);
    text-underline-offset: 3px;
  }
  .next a:hover {
    text-decoration-color: currentColor;
  }
  a:focus-visible,
  .retry:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: 4px;
  }
</style>
