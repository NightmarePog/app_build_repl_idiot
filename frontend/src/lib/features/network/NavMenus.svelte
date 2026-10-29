<script lang="ts">
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  import { Accessibility, House, Ticket } from '@lucide/svelte';
  import { routesFrom, isTransfer } from '$lib/features/network/lines';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import RouteStrips from '$lib/features/network/RouteStrips.svelte';
  import { rememberStop } from '$lib/features/stops/selected-stop';

  let { kind, id, onclose }: { kind: 'stops' | 'lines'; id: string; onclose: () => void } =
    $props();

  const grouped = $derived(routesFrom(stopsList() ?? []));
  onMount(() => void loadStops());
</script>

<div class="menu-panel" class:routes={kind === 'stops'} {id}>
  {#if kind === 'lines'}
    <RouteStrips compact />
  {:else if stopsList() === null}
    <p class="menu-note">Načítáme zastávky…</p>
  {:else}
    {#each grouped.routes as route (route.id)}
      <div class="route" style:--lc={route.color}>
        <p class="route-head">
          <LineBadge id={route.id} /> Linka {route.id} · {route.stops.length} zastávek
        </p>
        {#each route.stops as stop (stop.id)}
          <a
            href={resolve('/stops/[id]', { id: String(stop.id) })}
            class="route-stop"
            onclick={() => {
              rememberStop(stop);
              onclose();
            }}
          >
            <span class="node" aria-hidden="true"
              ><i class:transfer={isTransfer(stop.name)}></i></span
            >
            <span>{stop.name}</span>
            <span
              class="amenities"
              aria-label={[
                stop.wheelchair_accessible ? 'bez schodů' : 'se schody',
                stop.has_shelter ? 'se střechou' : 'bez střechy',
                stop.has_ticket_machine ? 'jízdenky na místě' : 'bez jízdenek'
              ].join(', ')}
            >
              <i class:off={!stop.wheelchair_accessible} title="Nástup bez schodů"
                ><Accessibility size={13} aria-hidden="true" /></i
              >
              <i class:off={!stop.has_shelter} title="Střecha nad hlavou"
                ><House size={13} aria-hidden="true" /></i
              >
              <i class:off={!stop.has_ticket_machine} title="Jízdenky na místě"
                ><Ticket size={13} aria-hidden="true" /></i
              >
            </span>
          </a>
        {/each}
      </div>
    {/each}
    {#if grouped.other.length}
      <div class="route" style:--lc="var(--muted-foreground)">
        <p class="route-head">Další zastávky</p>
        {#each grouped.other as stop (stop.id)}
          <a
            href={resolve('/stops/[id]', { id: String(stop.id) })}
            class="route-stop"
            onclick={() => {
              rememberStop(stop);
              onclose();
            }}
          >
            <span class="node" aria-hidden="true"><i></i></span>
            <span>{stop.name}</span>
          </a>
        {/each}
      </div>
    {/if}
    <p class="menu-note wide">
      <span class="legend"
        ><span class="node legend-node" aria-hidden="true"><i class="transfer"></i></span
        >přestup</span
      >
      <a href={resolve('/stops')} onclick={onclose}>Úplný seznam s vyhledáváním</a>
    </p>
  {/if}
</div>

<style>
  .menu-panel {
    position: absolute;
    left: 0;
    top: calc(100% + 0.375rem);
    width: min(32rem, calc(100vw - 2.5rem));
    padding: 1.125rem 1.25rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    background: var(--card);
    box-shadow: var(--shadow-pop);
    animation: drop var(--motion-fast) var(--ease-out) both;
  }
  .menu-panel.routes {
    width: min(54rem, calc(100vw - 2.5rem));
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.5rem 1.75rem;
  }
  @keyframes drop {
    from {
      opacity: 0;
      transform: translateY(-4px);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
  .route {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .route-head {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    padding-bottom: 0.625rem;
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--muted-foreground);
  }
  .route-stop {
    display: grid;
    grid-template-columns: 1.125rem minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.625rem;
    padding: 0.375rem 0.375rem 0.375rem 0;
    border-radius: 0.25rem;
    color: var(--foreground);
    font-size: 0.9375rem;
    font-weight: 600;
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .route-stop:hover,
  .route-stop:focus-visible {
    background: var(--accent);
  }
  .node {
    position: relative;
    display: grid;
    place-items: center;
    width: 1.125rem;
  }
  .node::before {
    content: '';
    position: absolute;
    top: -0.5rem;
    bottom: -0.5rem;
    left: 50%;
    width: 4px;
    margin-left: -2px;
    background: var(--lc);
  }
  .route-stop:nth-of-type(1) .node::before {
    top: 50%;
  }
  .route-stop:last-of-type .node::before {
    bottom: 50%;
  }
  .node i {
    position: relative;
    display: block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--card);
    border: 3px solid var(--lc);
  }
  .node i.transfer {
    border-color: var(--foreground);
  }
  .amenities {
    display: flex;
    gap: 0.25rem;
    opacity: 0.55;
    transition: opacity var(--motion-fast) var(--ease-out);
  }
  .route-stop:hover .amenities,
  .route-stop:focus-visible .amenities {
    opacity: 1;
  }
  .amenities i {
    display: inline-grid;
    place-items: center;
    width: 1.25rem;
    height: 1.25rem;
    border-radius: 0.25rem;
    color: var(--foreground);
  }
  .amenities i.off {
    color: var(--muted-foreground);
    opacity: 0.45;
  }
  .menu-note {
    font-size: 0.8125rem;
    color: var(--muted-foreground);
  }
  .menu-note.wide {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem 1.5rem;
    grid-column: 1 / -1;
    margin-top: 0.375rem;
    padding-top: 0.625rem;
    border-top: 1px solid var(--border);
  }
  .legend {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
  }
  .legend-node {
    --lc: var(--muted-foreground);
  }
  .legend-node::before {
    display: none;
  }
  .menu-note a {
    color: var(--foreground);
    font-weight: 700;
    text-decoration: underline;
    text-underline-offset: 0.25em;
    text-decoration-color: var(--primary);
  }
  @media (max-width: 979px) {
    .menu-panel.routes {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .menu-panel {
      animation: none;
    }
  }
</style>
