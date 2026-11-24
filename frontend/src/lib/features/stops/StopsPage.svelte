<script lang="ts">
  import PageHeader from '$lib/components/PageHeader.svelte';
  import { czechCount } from '$lib/plural';
  import { isDetailView, withSearch } from '$lib/links';
  import type { Snippet } from 'svelte';
  import { mapFocus } from '$lib/map-focus.svelte';
  import ProjectCredits from '$lib/features/about/ProjectCredits.svelte';
  import { onMount, untrack } from 'svelte';
  import { flip } from 'svelte/animate';
  import { fade, fly } from 'svelte/transition';
  import { motionMs, prefersReducedMotion } from '$lib/motion';
  import { afterNavigate, goto, replaceState } from '$app/navigation';
  import { page } from '$app/state';
  import { resolve } from '$app/paths';
  import {
    ArrowLeftRight,
    ChevronRight,
    Map as MapIcon,
    MapPin,
    Navigation,
    Search,
    SearchX,
    X
  } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import {
    isTransfer,
    linePath,
    linesOf,
    LINE_IDS,
    LINES,
    loadNetwork,
    networkFailed,
    networkReady,
    POSITIONS,
    type LineId
  } from '$lib/features/network/lines.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import TransitMap from '$lib/features/network/TransitMap.svelte';
  import { loadStops, stopsFailed, stopsList } from '$lib/features/network/stops-store.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { Button } from '$lib/ui/button';
  import AmenityFilter from './AmenityFilter.svelte';
  import StopSummary from './StopSummary.svelte';
  import StopPeek from './StopPeek.svelte';
  import { AMENITIES, type AmenityValue } from './amenities';
  import { filterStops, normalize, parseLine } from './filter';
  import { rememberStop } from './selected-stop';
  import { mapSearch } from '$lib/map-search.svelte';
  import LineSummary from '$lib/features/lines/LineSummary.svelte';
  import PlannerPage from '$lib/features/planner/PlannerPage.svelte';
  import { typeNoun } from '$lib/features/lines/line-style';

  /** A stop, line or trip page, rendered by the route into the panel in place of the lists. */
  let { children }: { children?: Snippet } = $props();

  const networkStopCount = Object.keys(POSITIONS).length;

  let list = $state<HTMLUListElement>();

  // Phones: the panel is a bottom sheet over a full-screen map with three snap heights.
  type Snap = 'peek' | 'half' | 'full';
  let mobile = $state(false);
  let snap = $state<Snap>('half');
  let dragHeight = $state<number | null>(null);
  let finderHeight = $state(0);
  let panel = $state<HTMLDivElement>();
  /** Room the floating header card takes at the top of the map. */
  const TOP_GAP = 84;
  const PEEK = 128;
  function heightOf(value: Snap) {
    if (value === 'peek') return PEEK;
    if (value === 'half') return Math.round(finderHeight * 0.52);
    return finderHeight - TOP_GAP;
  }
  const expanded = $derived(snap === 'full');
  /** The sheet's height in pixels: the map keeps what is selected in the part still visible. */
  const sheetHeight = $derived(dragHeight ?? heightOf(snap));
  const mapInset = $derived(mobile ? Math.min(sheetHeight, heightOf('half')) + 16 : 0);

  function startDrag(event: PointerEvent) {
    if (!mobile || !panel || event.button !== 0) return;
    const handle = event.currentTarget as HTMLElement;
    const startY = event.clientY;
    const startHeight = panel.offsetHeight;
    let moved = false;
    handle.setPointerCapture(event.pointerId);
    const move = (e: PointerEvent) => {
      const dy = e.clientY - startY;
      if (!moved && Math.abs(dy) < 6) return;
      moved = true;
      dragHeight = Math.min(heightOf('full'), Math.max(PEEK * 0.75, startHeight - dy));
    };
    const end = () => {
      handle.removeEventListener('pointermove', move);
      handle.removeEventListener('pointerup', end);
      handle.removeEventListener('pointercancel', end);
      if (moved && dragHeight !== null) {
        // Snap to the nearest height.
        const at = dragHeight;
        snap = (['peek', 'half', 'full'] as const).reduce((best, next) =>
          Math.abs(heightOf(next) - at) < Math.abs(heightOf(best) - at) ? next : best
        );
      } else {
        snap = snap === 'full' ? 'half' : snap === 'half' ? 'full' : 'half';
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
  /** Which list the panel shows; the map stays the same for both. */
  type View = 'all' | 'stops' | 'lines' | 'planner';
  function readView(url: URL): View {
    if (url.pathname.startsWith('/lines')) return 'lines';
    if (url.pathname.startsWith('/connections')) return 'planner';
    if (url.pathname.startsWith('/stops')) return 'stops';
    return 'all';
  }
  const VIEW_PATH = {
    all: '/',
    stops: '/stops',
    lines: '/lines',
    planner: '/connections'
  } as const;
  const VIEWS: [View, string][] = [
    ['all', 'Vše'],
    ['stops', 'Zastávky'],
    ['lines', 'Linky'],
    ['planner', 'Spojení']
  ];
  function readStop(url: URL): number | null {
    const id = Number(url.searchParams.get('stop'));
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  // Shallow replaceState changes the address bar, not page.url. Keep controls and
  // outgoing links reactive locally, then restore them on navigation/history.
  const initialUrl = untrack(() => page.url);
  let query = $state(initialUrl.searchParams.get('q') ?? '');
  let filters = $state(readFilters(initialUrl));
  // Keep the raw `?line=` value: it only resolves to a line once the network has loaded.
  let lineParam = $state<string | null>(initialUrl.searchParams.get('line'));
  const line = $derived<LineId | null>(parseLine(lineParam));
  /** A line was asked for in the URL but the network is still on its way. */
  const lineResolving = $derived(!!lineParam && !networkReady() && !networkFailed());
  let selectedId = $state<number | null>(readStop(initialUrl));
  let view = $state<View>(readView(initialUrl));
  let detail = $state(isDetailView(initialUrl.pathname));
  /** In the line view the floating card describes the chosen line instead of a stop. */
  const showLine = $derived(view === 'lines' && line !== null && !!LINES[line]);
  let directorySearch = $state(initialUrl.search);
  const hasFilters = $derived(query.length > 0 || filters.length > 0 || line !== null);

  afterNavigate(() => {
    const url = new URL(window.location.href);
    query = url.searchParams.get('q') ?? '';
    filters = readFilters(url);
    lineParam = url.searchParams.get('line');
    selectedId = readStop(url);
    view = readView(url);
    detail = isDetailView(url.pathname);
    directorySearch = url.search;
  });

  const stops = $derived(stopsList() ?? []);
  const visible = $derived(filterStops(stops, query, filters, line));
  /** The header search filters the line list too, by number or name, ignoring accents. */
  const shownLines = $derived(
    LINE_IDS.filter((id) => {
      const needle = normalize(query);
      return !needle || normalize(`${id} ${LINES[id].name}`).includes(needle);
    })
  );

  // Search and filters live in the app's search palette ("Hledat", Ctrl K): it shows this
  // page's tools and its text filters the lists here.
  $effect(() => {
    // A stop, line or trip detail has nothing to filter: the palette is a plain search there.
    if (detail) return;
    mapSearch.active = true;
    mapSearch.tools = mapTools;
    return () => {
      mapSearch.active = false;
      mapSearch.tools = undefined;
    };
  });
  $effect(() => {
    const current = query;
    untrack(() => {
      if (mapSearch.query !== current) mapSearch.query = current;
    });
  });
  $effect(() => {
    const typed = mapSearch.query;
    untrack(() => {
      // The planner has its own fields; the search applies once a list view is open.
      if (typed !== query && view !== 'planner') updateFilters(typed, filters);
    });
  });
  /**
   * The list reads like a network map: transfer stops first, then each line's own stops under
   * its badge. Every stop appears once; `ordered` is that order, for J/K and arrow keys.
   */
  const groups = $derived.by(() => {
    const transfer = visible.filter((stop) => isTransfer(stop.name));
    const result: { key: string; title: string; line?: LineId; stops: Stop[] }[] = [];
    if (transfer.length)
      result.push({ key: 'transfer', title: 'Přestupní zastávky', stops: transfer });
    for (const id of LINE_IDS) {
      const own = visible.filter((stop) => !isTransfer(stop.name) && linesOf(stop.name)[0] === id);
      if (own.length)
        result.push({ key: `line-${id}`, title: LINES[id].name, line: id, stops: own });
    }
    const other = visible.filter((stop) => !isTransfer(stop.name) && !linesOf(stop.name).length);
    if (other.length) result.push({ key: 'other', title: 'Ostatní zastávky', stops: other });
    return result;
  });
  const ordered = $derived(groups.flatMap((group) => group.stops));
  /** Equal bands of each serving line's colour, top to bottom. */
  function stripeOf(lines: LineId[]) {
    const colors = lines.map((id) => LINES[id]?.color).filter(Boolean);
    if (colors.length < 2) return colors[0] ?? 'var(--foreground)';
    const band = 100 / colors.length;
    return `linear-gradient(${colors
      .map((color, i) => `${color} ${i * band}% ${(i + 1) * band}%`)
      .join(', ')})`;
  }
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

  /**
   * The list views drive the map from the selection and filters; a detail page hands over its
   * own focus (a stop's neighbourhood, a line's route, a trip). Keys left out fall back to the
   * map's defaults, so nothing from the other mode lingers.
   */
  const mapProps = $derived(
    (detail || view === 'planner') && mapFocus.props
      ? { stops: stopsList() ?? [], ...mapFocus.props }
      : {
          stops,
          selected: selected?.id ?? null,
          highlighted,
          dimmed,
          onselect: select,
          onhover: (stop: Stop | null) => (highlighted = stop?.id ?? null),
          line,
          fit: showLine && line ? linePath(line, LINES[line].stops) : null
        }
  );

  /** A new detail or view starts at the top of the panel, like a new page would. */
  const panelKey = $derived(`${detail}|${view}|${page.url.pathname}`);
  $effect(() => {
    void panelKey;
    untrack(() => panel?.scrollTo({ top: 0 }));
  });

  // On a phone, a new view or detail opens the sheet to half: content and map both visible.
  $effect(() => {
    void panelKey;
    untrack(() => {
      if (mobile && snap === 'peek') snap = 'half';
    });
  });

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
    lineParam = nextLine;
    writeUrl((params) => {
      if (nextQuery) params.set('q', nextQuery);
      else params.delete('q');
      if (chosen.length) params.set('filter', chosen.join(','));
      else params.delete('filter');
      if (nextLine) params.set('line', nextLine);
      else params.delete('line');
    });
  }

  /** Both views are real pages (/stops, /lines) that share this component through a layout. */
  function switchView(next: View) {
    if (next === view && !detail) return;
    const from = view;
    view = next;
    // Stop filters carry over between the stop and line views; the planner has its own query.
    const search = next === 'planner' || from === 'planner' || detail ? '' : window.location.search;
    void goto(withSearch(resolve(VIEW_PATH[next]), search), {
      keepFocus: true,
      noScroll: true
    });
  }

  /** The list view to come back to from the planner. */
  let lastList = $state<View>('all');
  $effect(() => {
    if (view !== 'planner') lastList = view;
  });

  function pickLine(id: LineId) {
    updateFilters(query, filters, line === id ? null : id);
    snap = 'half';
  }

  function select(stop: Stop) {
    selectedId = stop.id;
    writeUrl((params) => params.set('stop', String(stop.id)));
    if (!mobile) return;
    // On a phone: half a sheet, with the stop's card scrolled into view under the map.
    snap = 'half';
    requestAnimationFrame(() =>
      document
        .querySelector(`[data-stop-row="${stop.id}"]`)
        ?.scrollIntoView({ block: 'start', behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
    );
  }

  function resetFilters() {
    updateFilters('', [], null);
  }

  /** Alt+1–4 switch the map between all, stops, lines and connections, also from the palette. */
  function viewShortcut(event: KeyboardEvent) {
    if (!event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return false;
    const index = ['Digit1', 'Digit2', 'Digit3', 'Digit4'].indexOf(event.code);
    if (index < 0) return false;
    event.preventDefault();
    switchView(VIEWS[index][0]);
    return true;
  }

  /** J/K (anywhere outside fields) and arrow keys (inside the list) walk and select rows. */
  function onKeydown(event: KeyboardEvent) {
    if (viewShortcut(event)) return;
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
    select(ordered[next]);
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

{#snippet mapTools()}
  <!-- Rendered inside the search palette: what the map panel shows and how it is filtered. -->
  <div class="map-tools">
    <p class="pop-label">Zobrazit</p>
    <div class="view-switch" role="group" aria-label="Zobrazit">
      {#each VIEWS as [value, label] (value)}
        <button type="button" aria-pressed={view === value} onclick={() => switchView(value)}
          >{label}</button
        >
      {/each}
    </div>
    {#if view === 'stops' || view === 'all'}
      <p class="pop-label">Linka</p>
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
      <p class="pop-label">Vybavení zastávky</p>
      <AmenityFilter value={filters} onchange={(value) => updateFilters(query, value)} />
    {/if}
    {#if filters.length || line}
      <Button variant="ghost" size="sm" onclick={() => updateFilters(query, [], null)}
        >Vymazat filtry</Button
      >
    {/if}
  </div>
{/snippet}

<svelte:window onkeydown={onKeydown} />

<svelte:head>
  <!-- The planner view sets its own title. -->
  {#if view !== 'planner' && !detail}
    <title
      >{view === 'lines'
        ? 'Linky · TdA'
        : view === 'stops'
          ? 'Zastávky · TdA'
          : 'Think different Academy · Autobusy po kampusu'}</title
    >
  {/if}
  <meta
    name="description"
    content={`${networkStopCount} zastávek kampusu${networkReady() ? ` na ${LINE_IDS.length} linkách` : ''}. U každé fotografie a informace o nástupu bez schodů, střeše a prodeji jízdenek.`}
  />
</svelte:head>

<section
  class="finder"
  class:sheet-open={expanded}
  aria-labelledby="stops-title"
  bind:clientHeight={finderHeight}
  style:--sheet-h={mobile ? `${sheetHeight}px` : undefined}
>
  <div class="panel" class:expanded class:dragging={dragHeight !== null} bind:this={panel}>
    <button
      type="button"
      class="sheet-handle"
      aria-expanded={expanded}
      aria-label={expanded ? 'Sbalit panel' : 'Rozbalit panel'}
      onpointerdown={startDrag}
      onkeydown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          snap = snap === 'full' ? 'half' : 'full';
        }
      }}
    ></button>
    <!-- Under the search field: is the panel listing stops and lines, or planning a journey? -->
    <div class="mode-switch" role="group" aria-label="Režim panelu">
      <!-- One pill slides under the active mode instead of each button repainting. -->
      <span class="mode-pill" class:planner={view === 'planner'} aria-hidden="true"></span>
      <button type="button" aria-pressed={view !== 'planner'} onclick={() => switchView(lastList)}>
        <Search size={16} aria-hidden="true" /> Hledat
      </button>
      <button
        type="button"
        aria-pressed={view === 'planner'}
        aria-keyshortcuts="Alt+4"
        onclick={() => switchView('planner')}
      >
        <Navigation size={16} aria-hidden="true" /> Spojení
      </button>
    </div>
    {#if detail}
      <!-- No fade: the page is ready at once, and a fade from blank reads as a flicker. -->
      <div class="detail">{@render children?.()}</div>
    {:else if view === 'planner'}
      <div class="mode-pane" in:fly={{ x: 16, duration: motionMs(220), opacity: 0 }}>
        <PlannerPage />
      </div>
    {:else}
      <div class="mode-pane" in:fly={{ x: -16, duration: motionMs(220), opacity: 0 }}>
        <!-- The view switch names the view; the heading stays for screen readers only. -->
        <div class="panel-head sr-only" id="stops-title">
          <PageHeader
            kind={view === 'lines' ? 'Linky' : view === 'stops' ? 'Zastávky' : 'Mapa'}
            icon={MapIcon}
            title={view === 'lines'
              ? 'Všechny linky'
              : view === 'stops'
                ? 'Všechny zastávky'
                : 'Autobusy po kampusu'}
          >
            {#snippet meta()}
              <span class="tabular">
                {networkReady()
                  ? `${czechCount(LINE_IDS.length, 'linka', 'linky', 'linek')}, `
                  : ''}{czechCount(networkStopCount, 'zastávka', 'zastávky', 'zastávek')}
              </span>
            {/snippet}
          </PageHeader>
        </div>

        <!-- In "Vše" a search that matches no line just leaves the line section out. -->
        {#if view === 'lines' || (view === 'all' && shownLines.length > 0)}
          {#if view === 'all'}<h2 class="group-title">Linky</h2>{/if}
          {#if networkFailed()}
            <PageState title="Linky se nepodařilo načíst" error onretry={() => loadNetwork(true)} />
          {:else if !networkReady()}
            <LoadingState label="Načítáme linky…" />
          {:else if LINE_IDS.length === 0}
            <PageState title="Zatím nejsou evidované žádné linky" />
          {:else}
            {#if shownLines.length === 0}
              <PageState
                title="Takovou linku tu nemáme"
                description="Zkuste jiné číslo nebo název linky."
              />
            {/if}
            <ul class="line-list" aria-label="Seznam linek">
              {#each shownLines as id (id)}
                {@const item = LINES[id]}
                <li class:active={line === id}>
                  <button
                    type="button"
                    class="line-pick"
                    aria-pressed={line === id}
                    onclick={() => pickLine(id)}
                  >
                    <LineBadge {id} size="lg" />
                    <span class="line-name">
                      <strong>{item.name}</strong>
                      <span
                        >{typeNoun(item.type)} · {czechCount(
                          item.stops.length,
                          'zastávka',
                          'zastávky',
                          'zastávek'
                        )}</span
                      >
                    </span>
                  </button>
                  <a
                    class="detail-link"
                    href={resolve('/(map)/lines/[id]', { id: String(item.apiId) })}
                    aria-label={`Linka ${item.number}: ${item.name}`}
                    title="Jízdní řád linky"
                  >
                    <ChevronRight aria-hidden="true" size={20} />
                  </a>
                </li>
              {/each}
            </ul>
            {#if line && view === 'lines'}
              <Button variant="outline" size="sm" onclick={() => switchView('stops')}
                >Zastávky linky {line}</Button
              >
            {/if}
          {/if}
        {/if}
        {#if view !== 'lines'}
          {#if view === 'all'}<h2 class="group-title">Zastávky</h2>{/if}
          {#if stopsFailed()}
            <PageState
              title="Zastávky se nepodařilo načíst"
              error
              onretry={() => void loadStops()}
            />
          {:else if stopsList() === null || lineResolving}
            <LoadingState label="Načítáme zastávky…" />
          {:else if stops.length === 0}
            <PageState title="Zatím tu nejsou žádné zastávky" />
          {:else}
            <!-- Result count for screen readers; on screen the active filters say what is shown. -->
            <p class="sr-only" role="status" aria-live="polite" aria-atomic="true">
              {hasFilters
                ? `${visible.length} z ${stops.length} zastávek`
                : czechCount(stops.length, 'zastávka', 'zastávky', 'zastávek')}
            </p>
            {#if hasFilters}
              <div class="active-filters" role="group" aria-label="Zapnuté filtry">
                {#if query}
                  <button
                    type="button"
                    class="active-chip"
                    aria-label={`Zrušit hledání „${query}“`}
                    onclick={() => updateFilters('', filters)}
                    ><span>„{query}“</span><X size={14} aria-hidden="true" /></button
                  >
                {/if}
                {#if line && LINES[line]}
                  <button
                    type="button"
                    class="active-chip"
                    aria-label={`Zrušit filtr linka ${line}`}
                    onclick={() => updateFilters(query, filters, null)}
                    ><LineBadge id={line} /><X size={14} aria-hidden="true" /></button
                  >
                {/if}
                {#each AMENITIES.filter( ({ value }) => filters.includes(value) ) as amenity (amenity.value)}
                  <button
                    type="button"
                    class="active-chip"
                    aria-label={`Zrušit filtr ${amenity.label}`}
                    onclick={() =>
                      updateFilters(
                        query,
                        filters.filter((value) => value !== amenity.value)
                      )}
                    ><amenity.icon size={14} aria-hidden="true" /><span>{amenity.short}</span><X
                      size={14}
                      aria-hidden="true"
                    /></button
                  >
                {/each}
                <Button variant="ghost" size="sm" onclick={resetFilters}>Zrušit filtry</Button>
              </div>
            {/if}

            {#if visible.length === 0}
              <div class="list-empty">
                <SearchX aria-hidden="true" size={20} />
                <PageState
                  title="Takovou zastávku tu nemáme"
                  description="Zkuste jiný název nebo zrušte filtry."
                />
                <Button variant="outline" size="sm" onclick={resetFilters}
                  >Zobrazit všechny zastávky</Button
                >
              </div>
            {:else}
              <ul class="stop-list" aria-label="Seznam zastávek" bind:this={list}>
                {#each groups as group (group.key)}
                  <!-- Section header; role=presentation keeps it out of the list's item count. -->
                  <li
                    role="presentation"
                    class="group-head"
                    class:transfer-head={group.key === 'transfer'}
                    style:--lc={group.line ? LINES[group.line].color : undefined}
                  >
                    {#if group.line}<LineBadge id={group.line} />{:else}<ArrowLeftRight
                        size={16}
                        aria-hidden="true"
                      />{/if}
                    <span>{group.title}</span>
                  </li>
                  {#each group.stops as stop (stop.id)}
                    {@const lines = linesOf(stop.name)}
                    <li
                      class:transfer={group.key === 'transfer'}
                      style:--stripe={group.key === 'transfer' ? stripeOf(lines) : undefined}
                      style:--lc={group.line ? LINES[group.line].color : undefined}
                      data-stop-row={stop.id}
                      class:active={selected?.id === stop.id}
                      class:hot={highlighted === stop.id}
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
                        {#if stop.image_url}
                          <img
                            class="thumb"
                            src={stop.image_url}
                            alt=""
                            width="96"
                            height="64"
                            loading="lazy"
                            decoding="async"
                          />
                        {:else}
                          <span class="thumb thumb-empty" aria-hidden="true"
                            ><MapPin size={18} aria-hidden="true" /></span
                          >
                        {/if}
                        <span class="row-text">
                          <span class="row-name"
                            >{#if group.key === 'transfer'}<ArrowLeftRight
                                class="transfer-icon"
                                size={15}
                                aria-hidden="true"
                              /><span class="sr-only"
                                >Přestupní zastávka
                              </span>{/if}{stop.name}</span
                          >
                        </span>
                      </button>
                      <span class="row-lines">
                        {#each lines as id (id)}
                          <a
                            class="line-link"
                            href={resolve('/(map)/lines/[id]', { id: String(LINES[id].apiId) })}
                            title={`Linka ${id}: ${LINES[id].name}`}><LineBadge {id} /></a
                          >
                        {/each}
                      </span>
                      <a
                        class="detail-link"
                        href={withSearch(
                          resolve('/(map)/stops/[id]', { id: String(stop.id) }),
                          directorySearch
                        )}
                        aria-label={`Detail zastávky ${stop.name}`}
                        title="Detail zastávky"
                        onclick={() => rememberStop(stop)}
                      >
                        <ChevronRight aria-hidden="true" size={20} />
                      </a>
                      {#if selectedId === stop.id}
                        <StopPeek
                          {stop}
                          detailHref={withSearch(
                            resolve('/(map)/stops/[id]', { id: String(stop.id) }),
                            directorySearch
                          )}
                        />
                      {/if}
                    </li>
                  {/each}
                {/each}
              </ul>
            {/if}
          {/if}
        {/if}
      </div>
    {/if}
    <footer class="panel-foot" aria-label="O projektu">
      <ProjectCredits />
      <nav aria-label="Odkazy projektu">
        <a href={resolve('/contact')}>Kontakt</a>
        <a href={resolve('/terms')}>Podmínky používání</a>
        <a href="https://github.com/NightmarePog/2idiotz" rel="noopener noreferrer">GitHub</a>
      </nav>
    </footer>
  </div>

  <div class="map">
    <div class="map-canvas">
      <!-- One map for every view: it only gets new props, so it never reloads. -->
      <TransitMap {...mapProps} legend={false} inset={mapInset} focus />
    </div>
    {#if view === 'planner' || detail}
      <!-- The planner shows its results in the panel; no place card over the map. -->
    {:else if showLine && line}
      <div class="summary">
        {#key line}
          <div in:fly={{ y: 10, duration: motionMs(260) }}>
            <LineSummary line={LINES[line]} />
          </div>
        {/key}
      </div>
    {:else if selected && view === 'lines'}
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
  .detail {
    display: grid;
    gap: 1.25rem;
    min-width: 0;
  }
  /* Scrolled to on a phone: land below the sheet's sticky grab bar, not under it. */
  li[data-stop-row] {
    scroll-margin-top: 2.5rem;
  }
  .panel-foot {
    display: grid;
    gap: 0.75rem;
    margin-top: auto;
    padding-top: 1.25rem;
    border-top: 1px solid var(--border);
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .panel-foot nav {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem 1rem;
  }
  .panel-foot a:hover {
    color: var(--foreground);
  }
  .finder {
    display: grid;
    grid-template-columns: var(--panel-w, 30rem) minmax(0, 1fr);
    height: calc(100dvh - var(--header-h));
    min-height: 32rem;
    border-bottom: 1px solid var(--border);
  }
  .panel {
    /* Detail pages lay out by the panel's width, not the window's. */
    container-type: inline-size;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    min-width: 0;
    min-height: 0;
    overflow-y: auto;
    /* Room for the logo row the app header draws over the top of the panel. */
    padding: calc(1rem + var(--panel-top, 0px)) 1.25rem 1.5rem;
    border-right: 1px solid var(--border);
  }
  .panel-head :global(.page-header) {
    margin-bottom: 0;
  }
  .pop-label {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .map-tools {
    display: grid;
    justify-items: start;
    gap: 0.5rem;
  }
  .map-tools .view-switch {
    justify-self: stretch;
  }
  .map-tools :global(.amenity-filter) {
    flex-wrap: wrap;
  }
  .group-title {
    margin-top: 0.25rem;
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  .view-switch {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0.25rem;
    padding: 0.25rem;
    border-radius: var(--radius);
    background: var(--muted);
  }
  .view-switch button {
    min-height: 2.5rem;
    border-radius: var(--radius-sm);
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
    transition:
      background-color var(--motion-fast) var(--ease-out),
      color var(--motion-fast) var(--ease-out);
  }
  .view-switch button[aria-pressed='true'] {
    background: var(--background);
    color: var(--foreground);
    box-shadow: var(--shadow-sm, 0 1px 2px oklch(0 0 0 / 0.12));
  }
  .view-switch button:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 1px;
  }
  .line-list {
    display: grid;
    gap: 0.375rem;
  }
  .line-list li {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .line-list li:hover {
    background: var(--muted);
  }
  .line-list li.active {
    background: var(--accent);
    border-color: var(--foreground);
  }
  .line-pick {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    min-height: 3.75rem;
    padding: 0.5rem 0.75rem;
    text-align: left;
  }
  .line-pick:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -2px;
    border-radius: var(--radius);
  }
  .line-name {
    display: grid;
    min-width: 0;
  }
  .line-name strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .line-name span {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .line-chips {
    display: flex;
    gap: 0.375rem;
  }
  .line-chip {
    display: grid;
    width: 2.75rem;
    height: 2.75rem;
    place-items: center;
    border: 1px solid var(--border);
    border-radius: var(--radius);
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
  .active-filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.375rem;
  }
  .active-chip {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.25rem;
    padding: 0 0.5rem 0 0.625rem;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: var(--muted);
    color: var(--foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .active-chip:hover {
    border-color: var(--foreground);
  }
  .active-chip:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .stop-list {
    margin-inline: -0.5rem;
    border-top: 1px solid var(--border);
  }
  .stop-list li {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: center;
    border-bottom: 1px solid var(--border);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .stop-list li:hover,
  .stop-list li.hot {
    background: var(--muted);
  }
  .stop-list li.active {
    background: var(--accent);
  }
  .row {
    display: grid;
    grid-template-columns: 3.5rem minmax(0, 1fr);
    align-items: center;
    gap: 0.75rem;
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
  .row-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .row-name {
    font-weight: var(--weight-strong);
    line-height: 1.2;
    overflow-wrap: anywhere;
  }
  .stop-list .group-head {
    display: flex;
    box-shadow: none;
    align-items: center;
    gap: 0.5rem;
    padding: 1rem 0.5rem 0.375rem;
    border-bottom: 2px solid var(--lc, var(--foreground));
    color: var(--foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .stop-list .group-head:first-child {
    padding-top: 0.25rem;
  }
  .transfer-head {
    text-transform: uppercase;
    letter-spacing: var(--tracking-eyebrow);
    font-size: var(--fs-xs);
  }
  /* A stripe in the line colour ties each row to its section. */
  .stop-list li[style*='--lc']:not(.group-head) {
    box-shadow: inset 3px 0 0 var(--lc);
  }
  /* Transfer stops: a stripe split into the colours of every line that calls there. */
  .stop-list li.transfer {
    position: relative;
    background: color-mix(in oklch, var(--foreground) 4%, transparent);
  }
  .stop-list li.transfer::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 4px;
    background: var(--stripe);
  }
  .stop-list li.transfer :global(.transfer-icon) {
    display: inline;
    margin-right: 0.375rem;
    vertical-align: -0.125em;
    color: var(--muted-foreground);
  }
  .stop-list li.transfer .row-name {
    font-size: var(--fs-base);
  }

  /* The selected stop opens in place: its peek spans the whole row under the name. */
  .stop-list li > :global(.peek) {
    grid-column: 1 / -1;
  }

  .mode-switch {
    position: relative;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0.25rem;
    padding: 0.25rem;
    border-radius: var(--radius);
    background: var(--muted);
  }
  .mode-switch button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.4rem;
    min-height: 2.5rem;
    border-radius: var(--radius-sm);
    color: var(--muted-foreground);
    font-weight: var(--weight-strong);
    cursor: pointer;
  }
  .mode-switch button {
    position: relative;
    transition: color var(--motion-fast) var(--ease-out);
  }
  .mode-switch button[aria-pressed='true'] {
    color: var(--foreground);
  }
  .mode-pill {
    position: absolute;
    inset: 0.25rem auto 0.25rem 0.25rem;
    width: calc(50% - 0.375rem);
    border-radius: var(--radius-sm);
    background: var(--background);
    box-shadow: 0 1px 2px oklch(0 0 0 / 0.15);
    transition: translate var(--motion-normal) var(--ease-out);
  }
  .mode-pill.planner {
    translate: calc(100% + 0.25rem) 0;
  }
  .mode-pane {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    min-width: 0;
  }
  @media (prefers-reduced-motion: reduce) {
    .mode-pill {
      transition: none;
    }
  }
  .mode-switch button:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 1px;
  }
  .thumb {
    width: 3.5rem;
    height: 2.375rem;
    border-radius: var(--radius-sm);
    background: var(--muted);
    object-fit: cover;
  }
  .thumb-empty {
    display: grid;
    place-items: center;
    color: var(--muted-foreground);
  }
  .row-lines {
    display: flex;
    gap: 0.125rem;
  }
  .line-link {
    display: grid;
    place-items: center;
    min-width: 2rem;
    min-height: 2.75rem;
    border-radius: var(--radius-sm);
  }
  .line-link:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -2px;
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
    border-radius: var(--radius);
  }
  .summary :global(.stop-summary) {
    grid-template-columns: 8.5rem minmax(0, 1fr);
    gap: 1rem;
    padding: 0.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
  }
  .summary :global(.summary-image) {
    min-height: 0;
    border-radius: var(--radius);
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
      height: calc(100dvh - var(--header-h) - env(safe-area-inset-bottom));
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
      height: var(--sheet-h, 52%);
      gap: 0.875rem;
      padding: 0 1rem 1.25rem;
      overflow-y: auto;
      overscroll-behavior: contain;
      border-radius: var(--radius) var(--radius) 0 0;
      background: var(--background);
      box-shadow: var(--shadow-pop);
      transition: height var(--motion-slow) var(--ease-out);
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
      border-radius: var(--radius);
      background: color-mix(in oklch, var(--muted-foreground) 45%, transparent);
      transition: width var(--motion-fast) var(--ease-out);
    }
    .sheet-handle:active::before {
      width: 3rem;
    }
    .sheet-handle:focus-visible {
      outline: 2px solid var(--ring);
      outline-offset: -4px;
      border-radius: var(--radius);
    }
    .summary {
      position: absolute;
      inset: auto 0.75rem calc(var(--sheet-h, 52%) + 0.75rem);
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
      font-size: var(--fs-2xs);
    }
    .summary :global(.summary-body) {
      gap: 0.5rem;
    }
    .summary :global(dl) {
      display: none;
    }
    .summary :global(h3) {
      font-size: var(--fs-lg);
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
    .detail-link {
      transition: none;
    }
  }
</style>
