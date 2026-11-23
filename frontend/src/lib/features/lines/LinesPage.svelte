<script lang="ts">
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  import { BusFront, Route as RouteIcon, TramFront } from '@lucide/svelte';
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import { typeNoun } from './line-style';
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

  onMount(() => {
    void loadNetwork();
  });

  const lines = $derived(LINE_IDS.map((id) => LINES[id]!));
</script>

<svelte:head>
  <title>Linky · TdA</title>
</svelte:head>

<PageHeader kind="Linky" icon={RouteIcon} title="Všechny linky" />

{#if networkFailed()}
  <PageState title="Linky se nepodařilo načíst" error onretry={() => loadNetwork(true)} />
{:else if !networkReady()}
  <LoadingState label="Načítáme linky…" />
{:else if lines.length === 0}
  <PageState title="Zatím nejsou evidované žádné linky" />
{:else}
  <section class="cards" aria-label="Seznam linek">
    <LineCards />
  </section>

  <section class="network" aria-labelledby="network-heading">
    <h2 id="network-heading">Síť linek</h2>
    <ul class="key">
      {#each lines as line (line.number)}
        <li>
          <a href={resolve('/lines/[id]', { id: String(line.apiId) })}>
            <LinePlate number={line.number} color={line.color} size="md" />
            <span class="name"><span class="sr-only">Linka {line.number}: </span>{line.name}</span>
            <span class="type">
              {#if line.type.toLowerCase() === 'tram'}<TramFront
                  class="size-5"
                  aria-hidden="true"
                />{:else}<BusFront class="size-5" aria-hidden="true" />{/if}
              {typeNoun(line.type)}
            </span>
          </a>
        </li>
      {/each}
    </ul>
    <NetworkDiagram {lines} />
  </section>
{/if}

<style>
  .cards {
    margin-top: 1.5rem;
  }
  .network {
    margin-top: 3rem;
  }
  .key {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem 2rem;
    margin-top: 0.75rem;
  }
  .key a {
    display: inline-flex;
    align-items: center;
    gap: 0.625rem;
    min-height: 2.75rem;
  }
  .key .name {
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
  }
  .key a:hover .name {
    text-decoration: underline;
  }
  .type {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
  }
  h2 {
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
</style>
