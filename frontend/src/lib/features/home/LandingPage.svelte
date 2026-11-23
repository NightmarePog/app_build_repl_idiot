<script lang="ts">
  import { czechPlural } from '$lib/plural';
  import { withHash } from '$lib/links';
  import { reveal } from '$lib/motion';
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  import { ArrowRight } from '@lucide/svelte';
  import PlannerForm from '$lib/features/planner/PlannerForm.svelte';
  import { Button } from '$lib/ui/button';
  import NetworkView from '$lib/features/network/NetworkView.svelte';
  import LineCards from '$lib/features/network/LineCards.svelte';
  import {
    isTransfer,
    LINES,
    LINE_IDS,
    networkReady,
    POSITIONS
  } from '$lib/features/network/lines.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';

  const stopCount = Object.keys(POSITIONS).length;
  let highlightLines = $state(0);
  const transfers = $derived([
    ...new Set(LINE_IDS.flatMap((id) => LINES[id].stops).filter((name) => isTransfer(name)))
  ]);
  const lineWord = $derived(czechPlural(LINE_IDS.length, 'linka', 'linky', 'linek'));
  const transferWord = $derived(
    czechPlural(
      transfers.length,
      'přestupní zastávka',
      'přestupní zastávky',
      'přestupních zastávek'
    )
  );

  onMount(() => {
    const controller = new AbortController();
    void loadStops(controller.signal);
    return () => controller.abort();
  });
</script>

<svelte:head>
  <title>Autobusy po kampusu · TdA</title>
  <meta
    name="description"
    content={networkReady()
      ? `${stopCount} zastávek a ${LINE_IDS.length} ${lineWord} kampusu na jedné mapě, s vybavením každé zastávky.`
      : `${stopCount} zastávek kampusu na jedné mapě, s vybavením každé zastávky.`}
  />
</svelte:head>

<!-- Preserve the legacy spelling for consumers checking the homepage HTML. -->
<span hidden>Think diffrent Academy</span>

<div class="landing">
  <section class="intro" aria-labelledby="home-title">
    <div class="intro-text">
      <p class="kind-label">Přehled sítě</p>
      <h1 id="home-title">Autobusy po kampusu</h1>
      <p class="lede">
        <span class="tabular">{stopCount}</span> zastávek{#if networkReady()},
          <span class="tabular">{LINE_IDS.length}</span>
          {lineWord} a <span class="tabular">{transfers.length}</span>
          {transferWord}{/if}. U každé zastávky uvidíte, jestli má nástup bez schodů, střechu a
        automat na jízdenky.
      </p>
      <section class="hero-planner" aria-labelledby="hero-plan-title">
        <h2 id="hero-plan-title" class="hero-plan-title">Vyhledat spojení</h2>
        <PlannerForm compact headingId="hero-plan-title" />
      </section>

      <div class="actions">
        <Button href={resolve('/stops')} size="lg" variant="outline">
          Otevřít mapu <ArrowRight data-icon="inline-end" aria-hidden="true" />
        </Button>
        <a href={withHash(resolve('/'), 'lines')} class="text-link" onclick={() => highlightLines++}
          >Seznam linek</a
        >
      </div>
    </div>

    <div class="preview">
      <div class="preview-map" inert aria-hidden="true">
        <NetworkView interactive={false} stops={stopsList() ?? []} />
      </div>
      <a href={resolve('/stops')} class="preview-link" aria-label="Otevřít mapu zastávek"></a>
    </div>
  </section>

  <section id="lines" class="lines" aria-labelledby="lines-title">
    <div {@attach reveal()}>
      <h2 id="lines-title" class="section-title">Linky</h2>
    </div>
    <LineCards highlight={highlightLines} />
  </section>
</div>

<style>
  .landing {
    max-width: 85rem;
    margin-inline: auto;
    padding: 3rem 2rem 0;
  }
  .intro {
    display: grid;
    grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
    align-items: center;
    gap: 2.5rem 4rem;
    padding-bottom: 3rem;
    border-bottom: 1px solid var(--border);
  }
  .intro-text {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 1.25rem;
    min-width: 0;
  }
  h1 {
    font-size: var(--fs-h1);
    font-weight: var(--font-display-weight);
    line-height: var(--lh-tight);
    letter-spacing: var(--tracking-display);
  }
  .lede {
    max-width: 34rem;
    font-size: var(--fs-base);
    line-height: 1.55;
    color: var(--muted-foreground);
  }
  .actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.75rem 1.5rem;
  }
  .hero-planner {
    display: grid;
    gap: 0.5rem;
    width: 100%;
    max-width: 34rem;
  }
  .hero-plan-title {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  .text-link {
    display: inline-flex;
    align-items: center;
    min-height: 2.75rem;
    font-weight: var(--weight-strong);
    text-decoration: underline;
    text-underline-offset: 0.25em;
    text-decoration-color: color-mix(in oklch, currentColor 40%, transparent);
  }
  .text-link:hover {
    text-decoration-color: currentColor;
  }
  .preview {
    position: relative;
    min-width: 0;
    aspect-ratio: 4 / 3;
    overflow: hidden;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    background: var(--map);
  }
  .preview-map {
    position: absolute;
    inset: 0;
  }
  .preview-map :global(.network-map) {
    height: 100%;
    aspect-ratio: auto;
    border: 0;
    border-radius: 0;
  }
  .preview-link {
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
  }
  .preview-link:focus-visible {
    outline-offset: -3px;
  }
  .lines {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
    padding-top: 3rem;
  }
  .lines h2 {
    font-size: var(--fs-xl);
    font-weight: var(--font-display-weight);
    letter-spacing: var(--tracking-display);
    margin-bottom: 0.5rem;
  }
  @media (max-width: 979px) {
    .landing {
      padding: 2rem 1.25rem 0;
    }
    .intro {
      grid-template-columns: minmax(0, 1fr);
    }
  }
  .kind-label {
    margin: 0 0 0.5rem;
    font-size: var(--fs-xs);
    font-weight: var(--weight-semibold);
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--muted-foreground);
  }
</style>
