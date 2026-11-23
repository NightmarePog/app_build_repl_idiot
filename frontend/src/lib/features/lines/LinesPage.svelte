<script lang="ts">
  import { onMount } from 'svelte';
  import { Route as RouteIcon } from '@lucide/svelte';
  import PlannerForm from '$lib/features/planner/PlannerForm.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import LineCards from '$lib/features/network/LineCards.svelte';
  import {
    LINES,
    LINE_IDS,
    loadNetwork,
    networkFailed,
    networkReady
  } from '$lib/features/network/lines.svelte';
  import NetworkDiagram from './NetworkDiagram.svelte';
  import { goto } from '$app/navigation';
  import { resolve } from '$app/paths';
  import TransitMap from '$lib/features/network/TransitMap.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import type { LineId } from '$lib/features/network/lines.svelte';

  onMount(() => {
    void loadNetwork();
    void loadStops();
  });

  let hovered = $state<LineId | null>(null);

  const lines = $derived(LINE_IDS.map((id) => LINES[id]!));
</script>

<svelte:head>
  <title>Linky · TdA</title>
</svelte:head>

<PageHeader kind="Linky" icon={RouteIcon} title="Všechny linky" />

<section class="search" aria-labelledby="search-heading">
  <h2 id="search-heading" class="sr-only">Vyhledat spojení</h2>
  <!-- Same form, same layout as on /spojeni, where its results open. -->
  <PlannerForm headingId="search-heading" />
</section>

{#if networkFailed()}
  <PageState title="Linky se nepodařilo načíst" error onretry={() => loadNetwork(true)} />
{:else if !networkReady()}
  <LoadingState label="Načítáme linky…" />
{:else if lines.length === 0}
  <PageState title="Zatím nejsou evidované žádné linky" />
{:else}
  <section class="cards" aria-labelledby="cards-heading">
    <h2 id="cards-heading">Seznam linek</h2>
    <div class="overview">
      <LineCards onhover={(id) => (hovered = id)} />
      <div class="map">
        <TransitMap
          compact
          label="Mapa linek"
          stops={stopsList() ?? []}
          focusLines={hovered ? [hovered] : null}
          focusStops={hovered
            ? (stopsList() ?? [])
                .filter((stop) => LINES[hovered!]?.stops.includes(stop.name))
                .map((stop) => stop.id)
            : null}
          onselect={(stop) => goto(resolve('/stops/[id]', { id: String(stop.id) }))}
        />
      </div>
    </div>
  </section>

  <section class="network" aria-labelledby="network-heading">
    <h2 id="network-heading">Síť linek</h2>
    <NetworkDiagram {lines} />
  </section>
{/if}

<style>
  /* PageHeader already ends with its own bottom spacing, same as on every other page. */
  .cards {
    margin-top: 3rem;
  }
  .network {
    margin-top: 3rem;
  }
  .overview {
    display: grid;
    gap: 1.25rem;
  }
  @media (min-width: 64rem) {
    .overview {
      grid-template-columns: minmax(17rem, 22rem) minmax(0, 1fr);
      align-items: start;
    }
    .overview :global(.line-cards) {
      grid-template-columns: minmax(0, 1fr);
    }
    .map {
      position: sticky;
      top: calc(var(--header-h, 4rem) + 1rem);
    }
  }
  .cards h2,
  .search h2 {
    margin-bottom: 0.75rem;
  }
  h2 {
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
</style>
