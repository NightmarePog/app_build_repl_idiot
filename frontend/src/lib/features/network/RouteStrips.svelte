<script lang="ts">
  import { resolve } from '$app/paths';
  import { LINES, LINE_IDS, isTransfer } from './lines';
  import LineBadge from './LineBadge.svelte';

  let { compact = false }: { compact?: boolean } = $props();

  const routes = LINE_IDS.map((id) => {
    const stops = LINES[id].stops;
    return {
      id,
      color: LINES[id].color,
      from: stops[0],
      to: stops[stops.length - 1],
      count: stops.length,
      transfers: stops.filter(isTransfer),
      dots: stops.map((name, index) => ({
        name,
        left: `${Math.round((index / (stops.length - 1)) * 100)}%`,
        transfer: isTransfer(name)
      }))
    };
  });
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -- The route is resolved; only a query is appended. -->
<div class="route-strips" class:compact>
  {#each routes as route (route.id)}
    <a href={`${resolve('/stops')}?line=${route.id}`} class="strip" style:--lc={route.color}>
      <LineBadge id={route.id} size="lg" />
      <span class="strip-body">
        <span class="ends"><span>{route.from}</span><span>{route.to}</span></span>
        <span class="bar" aria-hidden="true">
          {#each route.dots as dot (dot.name)}
            <b class:transfer={dot.transfer} style:left={dot.left}></b>
          {/each}
        </span>
        <span class="meta tabular"
          >{route.count} zastávek · přestup: {route.transfers.join(', ')}</span
        >
      </span>
    </a>
  {/each}
</div>

<!-- eslint-enable svelte/no-navigation-without-resolve -->

<style>
  .route-strips {
    display: flex;
    flex-direction: column;
  }
  .strip {
    display: grid;
    grid-template-columns: 2.75rem minmax(0, 1fr);
    align-items: center;
    gap: 0.875rem;
    padding: 0.875rem 0.5rem;
    margin-inline: -0.5rem;
    border-top: 1px solid var(--border);
    border-radius: var(--radius-sm, 0.25rem);
    color: var(--foreground);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .strip:first-child {
    border-top: 0;
  }
  .strip:hover {
    background: var(--muted);
  }
  .strip-body {
    display: flex;
    flex-direction: column;
    gap: 0.625rem;
    min-width: 0;
  }
  .ends {
    display: flex;
    justify-content: space-between;
    gap: 0.75rem;
    font-weight: 700;
  }
  .bar {
    position: relative;
    height: 6px;
    border-radius: 3px;
    background: var(--lc);
  }
  .bar b {
    position: absolute;
    top: 50%;
    width: 12px;
    height: 12px;
    margin: -6px 0 0 -6px;
    border-radius: 50%;
    background: var(--background);
    border: 3px solid var(--lc);
  }
  .bar b.transfer {
    border-color: var(--foreground);
  }
  .meta {
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  .compact .strip {
    padding: 0.625rem 0.5rem;
  }
  .compact .ends {
    font-size: 0.9375rem;
  }
</style>
