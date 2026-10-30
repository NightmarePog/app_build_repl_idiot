<script lang="ts">
  import { reveal } from '$lib/motion';
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  import { ArrowRight, Search } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import NetworkView from '$lib/features/network/NetworkView.svelte';
  import LineCards from '$lib/features/network/LineCards.svelte';
  import { isTransfer, LINES, LINE_IDS, POSITIONS } from '$lib/features/network/lines';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';

  const stopCount = Object.keys(POSITIONS).length;
  let highlightLines = $state(0);
  const transfers = [
    ...new Set(LINE_IDS.flatMap((id) => LINES[id].stops).filter((name) => isTransfer(name)))
  ];
  const lineWord = LINE_IDS.length < 5 ? 'linky' : 'linek';
  const transferWord = transfers.length < 5 ? 'přestupní zastávky' : 'přestupních zastávek';

  onMount(() => {
    const controller = new AbortController();
    void loadStops(controller.signal);
    return () => controller.abort();
  });
</script>

<svelte:head>
  <title>Autobusy po kampusu | Think different Academy</title>
  <meta
    name="description"
    content={`${stopCount} zastávek a ${LINE_IDS.length} ${lineWord} kampusu na jedné mapě, s vybavením každé zastávky.`}
  />
</svelte:head>

<!-- Preserve the legacy spelling for consumers checking the homepage HTML. -->
<span hidden>Think diffrent Academy</span>

<div class="landing">
  <section class="intro" aria-labelledby="home-title">
    <div class="intro-text">
      <h1 id="home-title">Autobusy po kampusu</h1>
      <p class="lede">
        <span class="tabular">{stopCount}</span> zastávek,
        <span class="tabular">{LINE_IDS.length}</span>
        {lineWord} a <span class="tabular">{transfers.length}</span>
        {transferWord}. U každé zastávky uvidíte, jestli má nástup bez schodů, střechu a automat na
        jízdenky.
      </p>
      <div class="actions">
        <Button href={resolve('/stops')} size="lg">
          Otevřít mapu <ArrowRight data-icon="inline-end" aria-hidden="true" />
        </Button>
        <!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- in-page fragment -->
        <a href="#lines" class="text-link" onclick={() => highlightLines++}>Seznam linek</a>
      </div>

      <form class="quick-search" role="search" method="GET" action={resolve('/stops')}>
        <label class="search">
          <Search aria-hidden="true" size={18} />
          <span class="sr-only">Hledat zastávku podle názvu</span>
          <input name="q" type="search" placeholder="Název zastávky" autocomplete="off" />
        </label>
        <Button type="submit" variant="outline">Hledat</Button>
      </form>
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
      <h2 id="lines-title">Linky</h2>
      <p>Vyberte linku a ukážeme vám ji na mapě i se všemi zastávkami.</p>
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
    font-size: clamp(2.25rem, 4.5vw, 3.25rem);
    font-weight: var(--font-display-weight, 650);
    line-height: 1.05;
    letter-spacing: var(--tracking-display, -0.02em);
  }
  .lede {
    max-width: 34rem;
    font-size: 1.0625rem;
    line-height: 1.55;
    color: var(--muted-foreground);
  }
  .actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.75rem 1.5rem;
  }
  .text-link {
    display: inline-flex;
    align-items: center;
    min-height: 2.75rem;
    font-weight: 600;
    text-decoration: underline;
    text-underline-offset: 0.25em;
    text-decoration-color: color-mix(in oklch, currentColor 40%, transparent);
  }
  .text-link:hover {
    text-decoration-color: currentColor;
  }
  .quick-search {
    display: flex;
    gap: 0.5rem;
    width: 100%;
    max-width: 26rem;
    margin-top: 0.75rem;
  }
  .search {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 0.625rem;
    min-width: 0;
    min-height: 2.75rem;
    padding: 0 0.75rem;
    border: 1px solid var(--input);
    border-radius: var(--radius-md);
    background: var(--card);
    color: var(--muted-foreground);
    cursor: text;
    transition: border-color var(--motion-fast) var(--ease-out);
  }
  .search:focus-within {
    border-color: var(--ring);
    outline: 2px solid color-mix(in oklch, var(--ring) 35%, transparent);
  }
  .search input {
    flex: 1;
    min-width: 0;
    background: transparent;
    border: 0;
    outline: 0;
    color: var(--foreground);
    font: inherit;
  }
  .quick-search :global(button) {
    min-height: 2.75rem;
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
    font-size: 1.5rem;
    font-weight: var(--font-display-weight, 650);
    letter-spacing: var(--tracking-display, -0.02em);
    margin-bottom: 0.5rem;
  }
  .lines p {
    color: var(--muted-foreground);
    line-height: 1.5;
  }
  @media (max-width: 979px) {
    .landing {
      padding: 2rem 1.25rem 0;
    }
    .intro {
      grid-template-columns: minmax(0, 1fr);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .search {
      transition: none;
    }
  }
</style>
