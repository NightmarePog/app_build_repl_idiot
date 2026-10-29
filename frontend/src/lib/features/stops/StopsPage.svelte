<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import { afterNavigate, replaceState } from '$app/navigation';
  import { page } from '$app/state';
  import { resolve } from '$app/paths';
  import { ChevronRight, Search, SearchX, X } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import {
    describeStop,
    isTransfer,
    linesOf,
    LINE_IDS,
    LINES,
    POSITIONS,
    type LineId
  } from '$lib/features/network/lines';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import NetworkView from '$lib/features/network/NetworkView.svelte';
  import { loadStops, stopsFailed, stopsList } from '$lib/features/network/stops-store.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { Button } from '$lib/ui/button';
  import AmenityFilter from './AmenityFilter.svelte';
  import StopSummary from './StopSummary.svelte';
  import { AMENITIES, type AmenityValue } from './amenities';
  import { filterStops, parseLine } from './filter';
  import { rememberStop } from './selected-stop';

  const networkStopCount = Object.keys(POSITIONS).length;

  let list = $state<HTMLUListElement>();
  let search = $state<HTMLInputElement>();
  let highlighted = $state<number | null>(null);

  function readFilters(url: URL): AmenityValue[] {
    const requested = (url.searchParams.get('filter') ?? '').split(',');
    return AMENITIES.filter(({ value }) => requested.includes(value)).map(({ value }) => value);
  }
  function readStop(url: URL): number | null {
    const id = Number(url.searchParams.get('stop'));
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  // Shallow replaceState changes the address bar, not page.url. Keep controls and
  // outgoing links reactive locally, then restore them on navigation/history.
  const initialUrl = untrack(() => page.url);
  let query = $state(initialUrl.searchParams.get('q') ?? '');
  let filters = $state(readFilters(initialUrl));
  let line = $state<LineId | null>(parseLine(initialUrl.searchParams.get('line')));
  let selectedId = $state<number | null>(readStop(initialUrl));
  let directorySearch = $state(initialUrl.search);
  const hasFilters = $derived(query.length > 0 || filters.length > 0 || line !== null);

  afterNavigate(() => {
    const url = new URL(window.location.href);
    query = url.searchParams.get('q') ?? '';
    filters = readFilters(url);
    line = parseLine(url.searchParams.get('line'));
    selectedId = readStop(url);
    directorySearch = url.search;
  });

  const stops = $derived(stopsList() ?? []);
  const visible = $derived(filterStops(stops, query, filters, line));
  const dimmed = $derived(stops.filter((s) => !visible.includes(s)).map((s) => s.id));
  const selected = $derived(
    stops.find((stop) => stop.id === selectedId) ??
      stops.find((stop) => isTransfer(stop.name)) ??
      stops[0] ??
      null
  );

  function writeUrl(edit: (params: URLSearchParams) => void) {
    const url = new URL(window.location.href);
    edit(url.searchParams);
    directorySearch = url.search;
    // The browser URL already includes the configured base; do not resolve it twice.
    // eslint-disable-next-line svelte/no-navigation-without-resolve
    replaceState(url, page.state);
  }

  function updateFilters(
    nextQuery: string,
    nextFilters: readonly string[],
    nextLine: LineId | null = line
  ) {
    const chosen = AMENITIES.filter(({ value }) => nextFilters.includes(value)).map(
      ({ value }) => value
    );
    query = nextQuery;
    filters = chosen;
    line = nextLine;
    writeUrl((params) => {
      if (nextQuery) params.set('q', nextQuery);
      else params.delete('q');
      if (chosen.length) params.set('filter', chosen.join(','));
      else params.delete('filter');
      if (nextLine) params.set('line', nextLine);
      else params.delete('line');
    });
  }

  function select(stop: Stop) {
    selectedId = stop.id;
    writeUrl((params) => params.set('stop', String(stop.id)));
  }

  function resetFilters() {
    updateFilters('', [], null);
    // The reset control disappears; keep keyboard focus in the search workflow.
    search?.focus();
  }

  /** J/K (anywhere outside fields) and arrow keys (inside the list) walk and select rows. */
  function onKeydown(event: KeyboardEvent) {
    if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey || !list) return;
    const target = event.target as HTMLElement | null;
    if (target?.closest('input, textarea, select, [contenteditable="true"]')) return;
    const key = event.key.toLowerCase();
    const inList = !!target && list.contains(target);
    const step =
      key === 'j' || (inList && key === 'arrowdown')
        ? 1
        : key === 'k' || (inList && key === 'arrowup')
          ? -1
          : 0;
    if (!step) return;
    const rows = [...list.querySelectorAll<HTMLButtonElement>('button.row')];
    if (!rows.length) return;
    const current = rows.findIndex((row) => row.parentElement?.contains(document.activeElement));
    const next =
      current < 0
        ? step > 0
          ? 0
          : rows.length - 1
        : Math.min(rows.length - 1, Math.max(0, current + step));
    event.preventDefault();
    rows[next].focus();
    rows[next].scrollIntoView({ block: 'nearest' });
    select(visible[next]);
  }

  /** "/" jumps to the stop search here instead of opening the global palette. */
  function focusSearch(event: KeyboardEvent) {
    const target = event.target as HTMLElement | null;
    if (
      event.key !== '/' ||
      event.defaultPrevented ||
      event.metaKey ||
      event.ctrlKey ||
      event.altKey ||
      target?.isContentEditable ||
      /^(INPUT|TEXTAREA|SELECT)$/.test(target?.tagName ?? '') ||
      !search
    )
      return;
    event.preventDefault();
    search.focus();
  }

  onMount(() => {
    const controller = new AbortController();
    void loadStops(controller.signal);
    return () => controller.abort();
  });
</script>

<svelte:window onkeydown={onKeydown} />
<svelte:document onkeydown={focusSearch} />

<svelte:head>
  <title>Zastávky | Think different Academy</title>
  <meta
    name="description"
    content={`${networkStopCount} zastávek kampusu na ${LINE_IDS.length} linkách. U každé fotografie a informace o nástupu bez schodů, střeše a prodeji jízdenek.`}
  />
</svelte:head>

<section class="finder" aria-labelledby="stops-title">
  <div class="panel">
    <header class="panel-head">
      <h1 id="stops-title">Zastávky</h1>
      <p class="tabular">{LINE_IDS.length} linky · {networkStopCount} zastávek</p>
    </header>

    {#if stopsFailed()}
      <PageState title="Zastávky se nepodařilo načíst" error onretry={() => void loadStops()} />
    {:else if stopsList() === null}
      <LoadingState label="Načítáme zastávky…" />
    {:else if stops.length === 0}
      <PageState title="Zatím tu nejsou žádné zastávky" />
    {:else}
      <form role="search" class="controls" onsubmit={(event) => event.preventDefault()}>
        <label class="search">
          <Search aria-hidden="true" size={18} />
          <span class="sr-only">Hledat zastávku</span>
          <input
            id="stop-search"
            bind:this={search}
            type="search"
            placeholder="Název zastávky"
            autocomplete="off"
            aria-keyshortcuts="/"
            value={query}
            oninput={(event) => updateFilters(event.currentTarget.value, filters)}
          />
          {#if !query}<kbd aria-hidden="true">/</kbd>{/if}
        </label>
        <div class="filter-row">
          <div class="line-chips" role="group" aria-label="Linka">
            {#each LINE_IDS as id (id)}
              <button
                type="button"
                class="line-chip"
                aria-pressed={line === id}
                aria-label={`Linka ${id}`}
                style:--lc={LINES[id].color}
                onclick={() => updateFilters(query, filters, line === id ? null : id)}
              >
                <LineBadge {id} size="md" />
              </button>
            {/each}
          </div>
          <AmenityFilter value={filters} onchange={(value) => updateFilters(query, value)} />
        </div>
      </form>

      <div class="results-heading">
        <p role="status" aria-live="polite" aria-atomic="true">
          <span class="result-number">{visible.length}</span>
          <span
            >{hasFilters
              ? `z ${stops.length} zastávek`
              : stops.length === 1
                ? 'zastávka'
                : stops.length < 5
                  ? 'zastávky'
                  : 'zastávek'}</span
          >
        </p>
        {#if hasFilters}
          <Button variant="ghost" size="sm" onclick={resetFilters}
            ><X aria-hidden="true" data-icon="inline-start" /> Zrušit filtry</Button
          >
        {/if}
      </div>

      {#if visible.length === 0}
        <div class="list-empty">
          <SearchX aria-hidden="true" size={20} />
          <h2>Takovou zastávku tu nemáme</h2>
          <p>Zkuste jiný název nebo zrušte filtry.</p>
          <Button variant="outline" size="sm" onclick={resetFilters}
            >Zobrazit všechny zastávky</Button
          >
        </div>
      {:else}
        <ul class="stop-list" aria-label="Seznam zastávek" bind:this={list}>
          {#each visible as stop (stop.id)}
            {@const lines = linesOf(stop.name)}
            <li class:active={selected?.id === stop.id}>
              <button
                type="button"
                class="row"
                aria-pressed={selected?.id === stop.id}
                onclick={() => select(stop)}
                onmouseenter={() => (highlighted = stop.id)}
                onmouseleave={() => (highlighted = null)}
                onfocus={() => (highlighted = stop.id)}
                onblur={() => (highlighted = null)}
              >
                <span
                  class="dot"
                  class:transfer={isTransfer(stop.name)}
                  style:--lc={lines[0] ? LINES[lines[0]].color : 'var(--muted-foreground)'}
                  aria-hidden="true"
                ></span>
                <span class="row-text">
                  <span class="row-name">{stop.name}</span>
                  <span class="row-sub">{describeStop(stop.name)}</span>
                </span>
                <span class="row-lines">
                  {#each lines as id (id)}<LineBadge {id} />{/each}
                </span>
              </button>
              <!-- eslint-disable svelte/no-navigation-without-resolve -- The route is already resolved; preserve the query verbatim after it. -->
              <a
                class="detail-link"
                href={`${resolve('/stops/[id]', { id: String(stop.id) })}${directorySearch}`}
                aria-label={`Detail zastávky ${stop.name}`}
                title="Detail zastávky"
                onclick={() => rememberStop(stop)}
              >
                <ChevronRight aria-hidden="true" size={18} />
              </a>
              <!-- eslint-enable svelte/no-navigation-without-resolve -->
            </li>
          {/each}
        </ul>
      {/if}
    {/if}
  </div>

  <div class="map">
    <div class="map-canvas">
      <NetworkView
        {stops}
        selected={selected?.id ?? null}
        {highlighted}
        {dimmed}
        onselect={select}
        focus
      />
    </div>
    {#if selected}
      <div class="summary">
        <StopSummary stop={selected} compact />
      </div>
    {/if}
  </div>
</section>

<style>
  .finder {
    display: grid;
    grid-template-columns: 26rem minmax(0, 1fr);
    height: calc(100dvh - var(--header-h));
    min-height: 32rem;
    border-bottom: 1px solid var(--border);
  }
  .panel {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    min-width: 0;
    min-height: 0;
    overflow-y: auto;
    padding: 1.5rem 1.25rem 1.5rem;
    border-right: 1px solid var(--border);
  }
  .panel-head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 0.25rem 1rem;
  }
  h1 {
    font-size: 1.75rem;
    font-weight: var(--font-display-weight, 650);
    line-height: 1.1;
    letter-spacing: var(--tracking-display, -0.02em);
  }
  .panel-head p {
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  .controls {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }
  .search {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    min-height: 2.875rem;
    padding: 0 0.5rem 0 0.75rem;
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
    font-size: 1.0625rem;
    font-weight: 600;
  }
  .filter-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem;
  }
  .filter-row :global(.amenity-toggle) {
    min-height: 2.75rem;
    padding: 0 0.625rem;
    font-size: 0.8125rem;
  }
  .line-chips {
    display: flex;
    gap: 0.375rem;
    padding-right: 0.25rem;
    margin-right: 0.25rem;
    border-right: 1px solid var(--border);
  }
  .line-chip {
    display: grid;
    width: 2.75rem;
    height: 2.75rem;
    place-items: center;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    background: var(--background);
    cursor: pointer;
    opacity: 0.85;
    transition:
      background-color var(--motion-fast) var(--ease-out),
      border-color var(--motion-fast) var(--ease-out),
      opacity var(--motion-fast) var(--ease-out);
  }
  .line-chip:hover {
    background: var(--muted);
    opacity: 1;
  }
  .line-chip[aria-pressed='true'] {
    border-color: var(--lc);
    box-shadow: inset 0 0 0 1px var(--lc);
    opacity: 1;
  }
  .results-heading {
    display: flex;
    min-height: 2.75rem;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
  }
  .results-heading > p {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  .result-number {
    color: var(--foreground);
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }
  .stop-list {
    margin-inline: -0.5rem;
    border-top: 1px solid var(--border);
  }
  .stop-list li {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    border-bottom: 1px solid var(--border);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .stop-list li:hover {
    background: var(--muted);
  }
  .stop-list li.active {
    background: var(--accent);
    box-shadow: inset 2px 0 0 var(--primary);
  }
  .row {
    display: grid;
    grid-template-columns: 1.25rem minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.625rem;
    min-width: 0;
    min-height: 3.25rem;
    padding: 0.5rem;
    border: 0;
    background: transparent;
    color: var(--foreground);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }
  .row:focus-visible,
  .detail-link:focus-visible {
    outline-offset: -3px;
  }
  .dot {
    justify-self: center;
    width: 0.75rem;
    height: 0.75rem;
    border-radius: 50%;
    background: var(--background);
    border: 3px solid var(--lc);
  }
  .dot.transfer {
    border-color: var(--foreground);
  }
  .row-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .row-name {
    font-weight: 600;
    line-height: 1.2;
    overflow-wrap: anywhere;
  }
  .row-sub {
    font-size: 0.8125rem;
    color: var(--muted-foreground);
  }
  .row-lines {
    display: flex;
    gap: 0.25rem;
  }
  .detail-link {
    display: grid;
    place-items: center;
    width: 2.75rem;
    min-height: 2.75rem;
    color: var(--muted-foreground);
    transition: color var(--motion-fast) var(--ease-out);
  }
  .detail-link:hover {
    color: var(--foreground);
  }
  .list-empty {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.375rem;
    padding: 1rem 0;
    border-top: 1px solid var(--border);
  }
  .list-empty h2 {
    font-size: 1rem;
    font-weight: 600;
  }
  .list-empty p {
    color: var(--muted-foreground);
  }
  .map {
    position: relative;
    display: grid;
    min-width: 0;
    min-height: 0;
    background: var(--map);
  }
  .map-canvas {
    position: relative;
    min-width: 0;
    min-height: 0;
  }
  .map-canvas :global(.network-map) {
    height: 100%;
    aspect-ratio: auto;
    border: 0;
    border-radius: 0;
  }
  .summary {
    position: absolute;
    left: 1rem;
    bottom: 1rem;
    z-index: 2;
    width: min(26rem, calc(100% - 5rem));
    box-shadow: var(--shadow-pop);
    border-radius: var(--radius-lg);
  }
  .summary :global(.stop-summary) {
    grid-template-columns: 8.5rem minmax(0, 1fr);
    gap: 1rem;
    padding: 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    background: var(--card);
  }
  .summary :global(.summary-image) {
    min-height: 0;
    border-radius: var(--radius-md);
  }
  @media (max-width: 979px) {
    .finder {
      grid-template-columns: minmax(0, 1fr);
      height: auto;
      min-height: 0;
      border-bottom: 0;
    }
    /* Map on top, then search and list, then the selected stop's card. */
    .map {
      display: contents;
    }
    .map-canvas {
      order: -1;
      height: 50vh;
      min-height: 18rem;
      background: var(--map);
      border-bottom: 1px solid var(--border);
    }
    .summary {
      order: 1;
      position: static;
      width: auto;
      padding: 0 1.25rem 1.5rem;
      box-shadow: none;
    }
    .summary :global(.stop-summary) {
      grid-template-columns: minmax(0, 1fr);
    }
    .summary :global(.summary-image) {
      aspect-ratio: 16 / 9;
    }
    .panel {
      overflow: visible;
      border-right: 0;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .line-chip,
    .stop-list li,
    .detail-link,
    .search {
      transition: none;
    }
  }
</style>
