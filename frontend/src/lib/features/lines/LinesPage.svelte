<script lang="ts">
  import { onMount } from 'svelte';
  import { fly } from 'svelte/transition';
  import { resolve } from '$app/paths';
  import { ChevronRight, X } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import PlannerForm from '$lib/features/planner/PlannerForm.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import TransitMap from '$lib/features/network/TransitMap.svelte';
  import StopSummary from '$lib/features/stops/StopSummary.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import {
    LINES,
    LINE_IDS,
    linePath,
    loadNetwork,
    networkFailed,
    networkReady,
    type LineId
  } from '$lib/features/network/lines.svelte';
  import { motionMs } from '$lib/motion';
  import LineSummary from './LineSummary.svelte';
  import { typeNoun } from './line-style';

  onMount(() => {
    void loadNetwork();
    void loadStops();
    const query = matchMedia('(max-width: 767px)');
    const update = () => (mobile = query.matches);
    update();
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  });

  let mobile = $state(false);
  let height = $state(0);
  /** Phones: the panel is a bottom sheet, collapsed or expanded. */
  let expanded = $state(false);
  let line = $state<LineId | null>(null);
  let hovered = $state<LineId | null>(null);
  let stop = $state<Stop | null>(null);

  const stops = $derived(stopsList() ?? []);
  const shown = $derived(hovered ?? line);
  const path = $derived(line ? linePath(line, LINES[line]?.stops ?? []) : []);

  function pickLine(id: LineId) {
    line = line === id ? null : id;
    stop = null;
    if (mobile) expanded = false;
  }

  function pickStop(next: Stop) {
    stop = stop?.id === next.id ? null : next;
  }
</script>

<svelte:head>
  <title>Linky · TdA</title>
</svelte:head>

<section class="explorer" class:sheet-open={expanded} bind:clientHeight={height}>
  <div class="map">
    <TransitMap
      {stops}
      selected={stop?.id ?? null}
      focusLines={shown ? [shown] : null}
      focusStops={shown
        ? stops.filter((s) => LINES[shown]?.stops.includes(s.name)).map((s) => s.id)
        : null}
      paths={line ? [{ coords: path, color: LINES[line].color }] : []}
      fit={line ? path : null}
      inset={mobile ? Math.round(height * 0.42) + (line || stop ? 150 : 0) : 0}
      insetLeft={mobile ? 0 : 420}
      onselect={pickStop}
      legend={false}
    />
  </div>

  <div class="panel" class:expanded>
    <button
      type="button"
      class="sheet-handle"
      aria-expanded={expanded}
      aria-label={expanded ? 'Sbalit panel linek' : 'Rozbalit panel linek'}
      onclick={() => (expanded = !expanded)}
    ></button>
    <h1>Všechny linky</h1>
    <section class="search" aria-labelledby="search-heading">
      <h2 id="search-heading" class="sr-only">Vyhledat spojení</h2>
      <PlannerForm headingId="search-heading" compact />
    </section>

    <section class="list" aria-labelledby="lines-heading">
      <h2 id="lines-heading" class="sr-only">Seznam linek</h2>
      {#if networkFailed()}
        <PageState title="Linky se nepodařilo načíst" error onretry={() => loadNetwork(true)} />
      {:else if !networkReady()}
        <LoadingState label="Načítáme linky…" />
      {:else if LINE_IDS.length === 0}
        <PageState title="Zatím nejsou evidované žádné linky" />
      {:else}
        <ul class="lines">
          {#each LINE_IDS as id (id)}
            {@const item = LINES[id]}
            <li class:active={line === id}>
              <button
                type="button"
                class="pick"
                aria-pressed={line === id}
                onclick={() => pickLine(id)}
                onpointerenter={() => (hovered = id)}
                onpointerleave={() => (hovered = null)}
                onfocus={() => (hovered = id)}
                onblur={() => (hovered = null)}
              >
                <LineBadge {id} size="lg" />
                <span class="name">
                  <strong>{item.name}</strong>
                  <span>{typeNoun(item.type)}</span>
                </span>
              </button>
              <a
                class="detail"
                href={resolve('/lines/[id]', { id: String(item.apiId) })}
                aria-label={`Linka ${item.number}: ${item.name}`}
              >
                <ChevronRight size={20} aria-hidden="true" />
              </a>
            </li>
          {/each}
        </ul>
      {/if}
    </section>
  </div>

  {#if stop || line}
    <div class="card">
      <button
        type="button"
        class="close"
        aria-label="Zavřít"
        onclick={() => (stop ? (stop = null) : (line = null))}
      >
        <X size={18} aria-hidden="true" />
      </button>
      {#key stop?.id ?? line}
        <div in:fly={{ y: 10, duration: motionMs(240) }}>
          {#if stop}
            <StopSummary {stop} compact />
          {:else if line && LINES[line]}
            <LineSummary line={LINES[line]} />
          {/if}
        </div>
      {/key}
    </div>
  {/if}
</section>

<style>
  .explorer {
    position: relative;
    height: calc(100dvh - var(--header-h));
    min-height: 32rem;
    overflow: hidden;
  }
  .map {
    position: absolute;
    inset: 0;
  }
  .map :global(.network-map) {
    height: 100%;
    aspect-ratio: auto;
    border: 0;
    border-radius: 0;
  }
  .panel {
    position: absolute;
    top: 1rem;
    left: 1rem;
    max-height: calc(100% - 2rem);
    z-index: 3;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    width: 25rem;
    padding: 1.25rem;
    overflow-y: auto;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    box-shadow: var(--shadow-pop);
  }
  h1 {
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    line-height: 1.1;
  }
  .panel :global(.planner) {
    padding: 0;
    border: 0;
    background: none;
    box-shadow: none;
  }
  .lines {
    display: grid;
    gap: 0.375rem;
  }
  .lines li {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .lines li:hover {
    background: var(--muted);
  }
  .lines li.active {
    background: var(--accent);
    border-color: var(--foreground);
  }
  .pick {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    min-height: 3.5rem;
    padding: 0.5rem 0.75rem;
    text-align: left;
  }
  .name {
    display: grid;
    min-width: 0;
  }
  .name strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .name span {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .detail {
    display: grid;
    place-items: center;
    width: 3rem;
    border-left: 1px solid var(--border);
    color: var(--muted-foreground);
  }
  .detail:hover {
    color: var(--foreground);
  }
  .pick:focus-visible,
  .detail:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -2px;
    border-radius: var(--radius);
  }
  .card {
    position: absolute;
    left: 27rem;
    bottom: 1rem;
    z-index: 2;
    width: min(26rem, calc(100% - 32rem));
    border-radius: var(--radius);
    box-shadow: var(--shadow-pop);
  }
  .card :global(.stop-summary) {
    grid-template-columns: 8.5rem minmax(0, 1fr);
    gap: 1rem;
    padding: 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
  }
  .card :global(.summary-image) {
    min-height: 0;
    border-radius: var(--radius);
  }
  .close {
    position: absolute;
    top: 0.5rem;
    right: 0.5rem;
    z-index: 1;
    display: grid;
    place-items: center;
    width: 2.25rem;
    height: 2.25rem;
    border-radius: var(--radius);
    color: var(--muted-foreground);
  }
  .close:hover {
    background: var(--muted);
    color: var(--foreground);
  }
  .sheet-handle {
    display: none;
  }

  /* Tablets: the card sits under the panel's right edge is too tight; stack it at the bottom. */
  @media (max-width: 1100px) and (min-width: 768px) {
    .card {
      left: auto;
      right: 1rem;
      width: min(24rem, calc(100% - 28rem));
    }
  }

  /* Phones: full-screen map, the panel in a bottom sheet, the card floating above it. */
  @media (max-width: 767px) {
    .explorer {
      height: calc(100dvh - var(--header-h) - var(--tabbar-h, 0px) - env(safe-area-inset-bottom));
      min-height: 26rem;
    }
    .panel {
      inset: auto 0 0;
      width: auto;
      max-height: none;
      height: 42%;
      gap: 0.875rem;
      padding: 0 1rem 1.25rem;
      border: 0;
      border-radius: var(--radius) var(--radius) 0 0;
      overscroll-behavior: contain;
      transition: height var(--motion-slow) var(--ease-out);
    }
    .panel.expanded {
      height: calc(100% - 0.75rem);
    }
    .list {
      order: 1;
    }
    .search {
      order: 2;
    }
    .sheet-handle {
      order: 0;
    }
    .sheet-handle {
      position: sticky;
      top: 0;
      z-index: 2;
      display: grid;
      place-items: center;
      flex-shrink: 0;
      height: 1.75rem;
      margin: 0 -1rem -0.5rem;
      background: var(--background);
    }
    .sheet-handle::before {
      content: '';
      width: 2.5rem;
      height: 0.3125rem;
      border-radius: var(--radius);
      background: color-mix(in oklch, var(--muted-foreground) 45%, transparent);
    }
    h1 {
      font-size: var(--fs-xl);
    }
    .card {
      inset: auto 0.75rem calc(42% + 0.75rem);
      width: auto;
    }
    .sheet-open .card {
      display: none;
    }
    .card :global(.stop-summary) {
      grid-template-columns: 5.5rem minmax(0, 1fr);
      gap: 0.75rem;
      padding: 0.625rem;
    }
    .card :global(.summary-image) {
      height: 100%;
      min-height: 5.5rem;
    }
    .card :global(.stop-summary dl) {
      display: none;
    }
    .card :global(.line-summary .next) {
      display: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .panel,
    .lines li {
      transition: none;
    }
  }
</style>
