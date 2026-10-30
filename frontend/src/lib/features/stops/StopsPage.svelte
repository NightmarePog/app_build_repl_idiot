<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import { flip } from 'svelte/animate';
  import { fade, fly } from 'svelte/transition';
  import { motionMs, prefersReducedMotion } from '$lib/motion';
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

  // Phones: the list is a bottom sheet over a full-screen map, collapsed or expanded.
  let mobile = $state(false);
  let expanded = $state(false);
  let dragHeight = $state<number | null>(null);
  let finderHeight = $state(0);
  let panel = $state<HTMLDivElement>();
  const COLLAPSED = 0.42;
  /** The map keeps the selected stop above the sheet and the floating stop card. */
  const mapInset = $derived(mobile ? Math.round(finderHeight * COLLAPSED) + 120 : 0);

  /**
   * Sideways filter row on phones: fade whichever edge has more chips, and nudge the row once
   * per session so it is clear it scrolls.
   */
  const scrollHint = (row: HTMLElement) => {
    const update = () => {
      const max = row.scrollWidth - row.clientWidth;
      row.classList.toggle('more-left', row.scrollLeft > 4);
      row.classList.toggle('more-right', row.scrollLeft < max - 4);
    };
    update();
    row.addEventListener('scroll', update, { passive: true });
    const resize = new ResizeObserver(update);
    resize.observe(row);
    let nudge = 0;
    let seen: boolean;
    try {
      seen = sessionStorage.getItem('filters-nudged') === '1';
    } catch {
      // Without storage, nudge every visit.
      seen = false;
    }
    if (!seen && !prefersReducedMotion() && row.scrollWidth > row.clientWidth + 8) {
      nudge = window.setTimeout(() => {
        // Snap points would turn the small peek into a jump to the next chip.
        row.style.scrollSnapType = 'none';
        row.scrollTo({ left: 56, behavior: 'smooth' });
        nudge = window.setTimeout(() => {
          row.scrollTo({ left: 0, behavior: 'smooth' });
          nudge = window.setTimeout(() => (row.style.scrollSnapType = ''), 600);
        }, 650);
        try {
          sessionStorage.setItem('filters-nudged', '1');
        } catch {
          // Not remembered; harmless.
        }
      }, 900);
    }
    return () => {
      clearTimeout(nudge);
      row.removeEventListener('scroll', update);
      resize.disconnect();
    };
  };

  function startDrag(event: PointerEvent) {
    if (!mobile || !panel || event.button !== 0) return;
    const handle = event.currentTarget as HTMLElement;
    const startY = event.clientY;
    const startHeight = panel.offsetHeight;
    const min = finderHeight * 0.2;
    const max = finderHeight - 12;
    let moved = false;
    handle.setPointerCapture(event.pointerId);
    const move = (e: PointerEvent) => {
      const dy = e.clientY - startY;
      if (!moved && Math.abs(dy) < 6) return;
      moved = true;
      dragHeight = Math.min(max, Math.max(min, startHeight - dy));
    };
    const end = () => {
      handle.removeEventListener('pointermove', move);
      handle.removeEventListener('pointerup', end);
      handle.removeEventListener('pointercancel', end);
      if (moved && dragHeight !== null) {
        // Snap to whichever state is closer.
        expanded = dragHeight > finderHeight * ((COLLAPSED + 1) / 2);
      } else {
        expanded = !expanded;
      }
      dragHeight = null;
    };
    handle.addEventListener('pointermove', move);
    handle.addEventListener('pointerup', end);
    handle.addEventListener('pointercancel', end);
  }
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
  // Filtered-out stops are not on the map, so keep the selection among the visible ones.
  const selected = $derived(
    visible.find((stop) => stop.id === selectedId) ??
      visible.find((stop) => isTransfer(stop.name)) ??
      visible[0] ??
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
    // On a phone, picking from the list drops the sheet so the map and the stop card show.
    expanded = false;
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
    const phone = matchMedia('(max-width: 767px)');
    const syncPhone = () => (mobile = phone.matches);
    syncPhone();
    phone.addEventListener('change', syncPhone);
    const controller = new AbortController();
    void loadStops(controller.signal);
    return () => {
      controller.abort();
      phone.removeEventListener('change', syncPhone);
    };
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

<section
  class="finder"
  class:sheet-open={expanded}
  aria-labelledby="stops-title"
  bind:clientHeight={finderHeight}
>
  <div
    class="panel"
    class:expanded
    class:dragging={dragHeight !== null}
    style:height={dragHeight !== null ? `${dragHeight}px` : undefined}
    bind:this={panel}
  >
    <button
      type="button"
      class="sheet-handle"
      aria-expanded={expanded}
      aria-label={expanded ? 'Sbalit seznam zastávek' : 'Rozbalit seznam zastávek'}
      onpointerdown={startDrag}
      onkeydown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          expanded = !expanded;
        }
      }}
    ></button>
    <header class="panel-head">
      <h1 id="stops-title">Zastávky</h1>
      <p class="tabular">{LINE_IDS.length} linky, {networkStopCount} zastávek</p>
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
        <div class="filter-row" {@attach scrollHint}>
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
            <li
              class:active={selected?.id === stop.id}
              animate:flip={{ duration: motionMs(240) }}
              in:fade={{ duration: motionMs(180), delay: motionMs(60) }}
              out:fade={{ duration: motionMs(120) }}
            >
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
        {line}
        inset={mapInset}
        focus
      />
    </div>
    {#if selected}
      <div class="summary">
        {#key selected.id}
          <div in:fly={{ y: 10, duration: motionMs(260) }}>
            <StopSummary stop={selected} compact />
          </div>
        {/key}
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
  .sheet-handle {
    display: none;
  }

  /* Phones: full-screen map, the list in a draggable bottom sheet, the stop card floating above. */
  @media (max-width: 767px) {
    .finder {
      position: relative;
      display: block;
      height: calc(100dvh - var(--header-h) - var(--tabbar-h) - env(safe-area-inset-bottom));
      min-height: 26rem;
      overflow: hidden;
    }
    .map {
      position: absolute;
      inset: 0;
      display: block;
    }
    .map-canvas {
      height: 100%;
      min-height: 0;
      border-bottom: 0;
    }
    .panel {
      position: absolute;
      inset: auto 0 0;
      z-index: 3;
      height: 42%;
      gap: 0.875rem;
      padding: 0 1rem 1.25rem;
      overflow-y: auto;
      overscroll-behavior: contain;
      border-radius: 1.375rem 1.375rem 0 0;
      background: var(--background);
      box-shadow:
        0 -1px 0 var(--border),
        0 -12px 32px -8px rgb(0 0 0 / 0.35);
      transition: height 0.42s cubic-bezier(0.22, 1, 0.36, 1);
    }
    .panel.expanded {
      height: calc(100% - 0.75rem);
    }
    .panel.dragging {
      transition: none;
    }
    /* Grab bar: sticks to the top of the sheet while its content scrolls. */
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
      touch-action: none;
      cursor: grab;
    }
    .sheet-handle::before {
      content: '';
      width: 2.5rem;
      height: 0.3125rem;
      border-radius: 999px;
      background: color-mix(in oklch, var(--muted-foreground) 45%, transparent);
      transition: width var(--motion-fast) var(--ease-out);
    }
    .sheet-handle:active::before {
      width: 3rem;
    }
    .sheet-handle:focus-visible {
      outline: 2px solid var(--ring);
      outline-offset: -4px;
      border-radius: 0.75rem;
    }
    h1 {
      font-size: 1.5rem;
    }
    .search :global(kbd) {
      display: none;
    }
    /* Filters: one row of chips that scrolls sideways instead of wrapping. */
    .filter-row {
      flex-wrap: nowrap;
      overflow-x: auto;
      margin-inline: -1rem;
      padding: 0.125rem 1rem;
      scrollbar-width: none;
      scroll-snap-type: x proximity;
    }
    .filter-row::-webkit-scrollbar {
      display: none;
    }
    /* Faded edges show there is more to swipe to. */
    .filter-row:global(.more-right) {
      mask-image: linear-gradient(90deg, #000 calc(100% - 3rem), transparent);
    }
    .filter-row:global(.more-left) {
      mask-image: linear-gradient(90deg, transparent, #000 2rem);
    }
    .filter-row:global(.more-left.more-right) {
      mask-image: linear-gradient(
        90deg,
        transparent,
        #000 2rem,
        #000 calc(100% - 3rem),
        transparent
      );
    }
    .filter-row > :global(*) {
      flex-shrink: 0;
    }
    .filter-row :global(.amenity-filter) {
      flex-wrap: nowrap;
    }
    .filter-row :global(.amenity-toggle) {
      white-space: nowrap;
      scroll-snap-align: start;
    }
    .summary {
      position: absolute;
      inset: auto 0.75rem calc(42% + 0.75rem);
      z-index: 2;
      width: auto;
      padding: 0;
      box-shadow: var(--shadow-pop);
      transition:
        opacity var(--motion-normal) var(--ease-out),
        transform var(--motion-normal) var(--ease-out),
        visibility 0s linear;
    }
    .sheet-open .summary {
      opacity: 0;
      visibility: hidden;
      transform: translateY(12px);
      transition:
        opacity var(--motion-fast) var(--ease-out),
        transform var(--motion-fast) var(--ease-out),
        visibility 0s linear var(--motion-fast);
    }
    /* Compact place card: photo, name and lines, one button. */
    .summary :global(.stop-summary) {
      grid-template-columns: 5.5rem minmax(0, 1fr);
      gap: 0.75rem;
      padding: 0.625rem;
    }
    .summary :global(.summary-image) {
      aspect-ratio: auto;
      height: 100%;
      min-height: 5.5rem;
    }
    .summary :global(.summary-image p) {
      padding: 0.25rem;
      font-size: 0.6875rem;
    }
    .summary :global(.summary-body) {
      gap: 0.5rem;
    }
    .summary :global(dl) {
      display: none;
    }
    .summary :global(h3) {
      font-size: 1.125rem;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .panel,
    .summary,
    .sheet-handle::before {
      transition: none;
    }
    .line-chip,
    .stop-list li,
    .detail-link,
    .search {
      transition: none;
    }
  }
</style>
