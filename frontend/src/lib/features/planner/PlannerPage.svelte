<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/state';
  import { ChevronDown, ChevronUp, Moon, Navigation } from '@lucide/svelte';
  import { searchConnections, type Connection, type ConnectionSearch } from '$lib/api/generated';
  import { createResource, ResourceError } from '$lib/api/resource.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { Button } from '$lib/ui/button';
  import { stopsList } from '$lib/features/network/stops-store.svelte';
  import { busAt, LINES, linePath, linesOf, POSITIONS } from '$lib/features/network/lines.svelte';
  import { tripPosition } from '$lib/features/timetable/trip-position';
  import { focusMap } from '$lib/map-focus.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { resolve } from '$app/paths';
  import { clock } from '$lib/features/timetable/clock.svelte';
  import { clock as clockText, countdown, minutesUntil } from '$lib/features/timetable/time';
  import ConnectionCard from './ConnectionCard.svelte';
  import PlannerForm from './PlannerForm.svelte';
  import PlannerEmpty from './PlannerEmpty.svelte';
  import { rememberSearch } from './recent.svelte';
  import {
    absolute,
    connectionKey,
    dayNote,
    fastestDuration,
    LONG_WAIT,
    parseQuery,
    plannerHref,
    shifted
  } from './planner';

  const PAGE = 5;

  // Opened without a search (route button, Alt+4): start typing the departure stop right away.
  onMount(() => {
    if (untrack(() => query.from) === null)
      requestAnimationFrame(() => document.getElementById('hero-plan-from')?.focus());
  });

  const query = $derived(parseQuery(page.url.searchParams));
  const ready = $derived(query.from !== null && query.to !== null && query.from !== query.to);
  const sameStop = $derived(query.from !== null && query.from === query.to);
  /** Everything that changes the search; the open detail does not. */
  const searchKey = $derived(`${query.from}|${query.to}|${query.time}|${query.mode}`);

  type Failure = 'missing' | 'unavailable';
  /** A failed "earlier"/"later" page: shown next to its button, the list stays. */
  let pageError = $state<'earlier' | 'later' | null>(null);

  async function fetchPage(
    from: number,
    to: number,
    time: string | undefined,
    mode: 'departure' | 'arrival',
    signal?: AbortSignal
  ) {
    const { data, response } = await searchConnections({
      query: { from, to, time, mode, limit: PAGE },
      signal
    });
    if (response?.status === 404) throw new ResourceError('missing');
    if (data === undefined || !response?.ok) throw new ResourceError('unavailable');
    return data;
  }

  const search = createResource<ConnectionSearch | null>(
    (signal) => {
      const { from, to, time, mode } = query;
      if (from === null || to === null || from === to) return Promise.resolve(null);
      return fetchPage(from, to, time ?? undefined, mode, signal);
    },
    () => searchKey
  );

  /** Pages added with "earlier" and "later", already shifted to the first search's day. */
  let extra = $state<Connection[]>([]);
  let paging = $state<'earlier' | 'later' | null>(null);
  let pageNote = $state('');
  let noMore = $state<{ earlier: boolean; later: boolean }>({ earlier: false, later: false });
  let opened = $state<string | null>(untrack(() => page.url.searchParams.get('detail')));

  $effect(() => {
    void searchKey;
    untrack(() => {
      extra = [];
      noMore = { earlier: false, later: false };
      pageError = null;
      opened = page.url.searchParams.get('detail');
    });
  });

  const connections = $derived(
    [...(search.data?.connections ?? []), ...extra]
      .filter(
        (c, index, all) =>
          all.findIndex((other) => connectionKey(other) === connectionKey(c)) === index
      )
      .sort(
        (a, b) =>
          absolute(a.departure_time, a.departure_day_offset) -
            absolute(b.departure_time, b.departure_day_offset) ||
          absolute(a.arrival_time, a.arrival_day_offset) -
            absolute(b.arrival_time, b.arrival_day_offset)
      )
  );

  /** The in-flight "earlier"/"later" request; aborted when the search changes. */
  let pageAbort: AbortController | null = null;
  $effect(() => {
    void searchKey;
    return () => {
      pageAbort?.abort();
      pageAbort = null;
      paging = null;
    };
  });

  async function more(direction: 'earlier' | 'later') {
    const list = connections;
    const { from, to } = query;
    if (!list.length || paging || from === null || to === null) return;
    const edge =
      direction === 'later'
        ? absolute(list.at(-1)!.departure_time, list.at(-1)!.departure_day_offset) + 1
        : absolute(list[0].arrival_time, list[0].arrival_day_offset) - 1;
    const shift = Math.floor(edge / 1440);
    const key = searchKey;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15_000);
    pageAbort = controller;
    paging = direction;
    pageError = null;
    try {
      const data = await fetchPage(
        from,
        to,
        clockText(edge),
        direction === 'later' ? 'departure' : 'arrival',
        controller.signal
      );
      // The query changed while this page was loading: it belongs to the old search.
      if (key !== searchKey) return;
      const before = connections.length;
      extra = [...extra, ...data.connections.map((c) => shifted(c, shift))];
      if (connections.length === before) noMore = { ...noMore, [direction]: true };
      else
        pageNote = `Načteno ${connections.length - before} ${direction === 'later' ? 'pozdějších' : 'dřívějších'} spojení`;
    } catch {
      if (key === searchKey) pageError = direction;
    } finally {
      clearTimeout(timeout);
      if (pageAbort === controller) {
        pageAbort = null;
        paging = null;
      }
    }
  }

  // Arrive-by: the connection closest to the target time is the last one; bring it into view.
  let list = $state<HTMLOListElement>();
  $effect(() => {
    if (query.mode !== 'arrival' || !search.data?.connections.length || !list) return;
    untrack(() => {
      if (!opened) list?.lastElementChild?.scrollIntoView({ block: 'nearest' });
    });
  });

  /** Deep link (?detail=): bring the opened card into view and focus it, once. */
  let deepLinked = false;
  $effect(() => {
    if (deepLinked || !opened || !connections.length) return;
    deepLinked = true;
    const id = `c-${opened}-summary`;
    requestAnimationFrame(() => {
      const el = document.getElementById(id);
      el?.scrollIntoView({ block: 'start' });
      el?.focus({ preventScroll: true });
    });
  });

  /** Fewer than a page came back: the day's service is over, the next one runs tomorrow. */
  const endOfDay = $derived(
    query.mode === 'departure' &&
      !!search.data &&
      search.data.connections.length < PAGE &&
      connections.every((c) => c.departure_day_offset <= 0)
  );

  /**
   * Nothing more today: roll over to the next service day (IDOS "další spojení") instead of
   * claiming that nothing runs. `rollover` is 'none' only when tomorrow's whole day is empty too.
   */
  let rollover = $state<'idle' | 'loading' | 'found' | 'none' | 'failed'>('idle');
  $effect(() => {
    void searchKey;
    const data = search.data;
    const { from, to } = query;
    untrack(() => (rollover = 'idle'));
    if (!data || data.connections.length || from === null || to === null) return;
    if (query.mode !== 'departure') return;
    const key = searchKey;
    const controller = new AbortController();
    untrack(() => (rollover = 'loading'));
    fetchPage(from, to, '00:00', 'departure', controller.signal)
      .then((next) => {
        if (key !== searchKey) return;
        extra = next.connections.map((c) => shifted(c, 1));
        rollover = extra.length ? 'found' : 'none';
      })
      .catch(() => {
        if (key === searchKey) rollover = 'failed';
      });
    return () => controller.abort();
  });

  const fastestMinutes = $derived(fastestDuration(connections));

  /**
   * Probe once per search whether anything runs before the first result; when nothing does the
   * "earlier" button would lead nowhere, so it is not shown at all.
   */
  $effect(() => {
    const first = search.data?.connections[0];
    const { from, to } = query;
    if (!first || from === null || to === null) return;
    const key = searchKey;
    const controller = new AbortController();
    const edge = absolute(first.arrival_time, first.arrival_day_offset) - 1;
    fetchPage(from, to, clockText(edge), 'arrival', controller.signal)
      .then((data) => {
        if (key !== searchKey) return;
        const shift = Math.floor(edge / 1440);
        const known = new Set(connections.map(connectionKey));
        const fresh = data.connections.filter((c) => !known.has(connectionKey(shifted(c, shift))));
        if (fresh.length === 0) noMore = { ...noMore, earlier: true };
      })
      .catch(() => {});
    return () => controller.abort();
  });

  /**
   * The first result leaves long after the searched time: say so in the heading ("nejbližší
   * v 15:30" or "nejbližší zítra 07:00"), as IDOS does, so one late result is not read as "now".
   */
  const gapNote = $derived.by(() => {
    const first = connections[0];
    const at = searchedTime;
    if (query.mode !== 'departure' || !first || !at) return null;
    const start = absolute(first.departure_time, first.departure_day_offset);
    if (start - absolute(at, 0) < LONG_WAIT) return null;
    const day = dayNote(first.departure_day_offset);
    return `nejbližší ${day ? `${day} ` : 'v '}${first.departure_time}`;
  });

  /**
   * "zítra · úterý 24. 11." for a day divider, the same wording as the stop board. The day is
   * today's Prague calendar date moved by `offset` (clock.now is minutes since midnight, not a date).
   */
  const pragueDay = new Intl.DateTimeFormat('cs-CZ', {
    timeZone: 'Europe/Prague',
    weekday: 'long',
    day: 'numeric',
    month: 'numeric'
  });
  function dayLabel(offset: number) {
    return `${dayNote(offset)} · ${pragueDay.format(new Date(Date.now() + offset * 86_400_000))}`;
  }

  function toggle(key: string) {
    opened = opened === key ? null : key;
    void goto(plannerHref(query, opened), {
      replaceState: true,
      noScroll: true,
      keepFocus: true
    });
  }

  $effect(() => clock.subscribe());
  /** Countdowns only make sense when the search is for "now". */
  function countdownOf(connection: Connection) {
    if (query.time || query.mode === 'arrival') return null;
    return countdown(
      minutesUntil(absolute(connection.departure_time, connection.departure_day_offset), clock.now)
    );
  }

  const stopNames = $derived(new Map((stopsList() ?? []).map((s) => [s.id, s.name])));
  const nameOf = (id: number | null) => (id === null ? null : (stopNames.get(id) ?? null));
  const fromName = $derived(search.data?.from.name ?? nameOf(query.from));
  const toName = $derived(search.data?.to.name ?? nameOf(query.to));
  const title = $derived(ready && fromName && toName ? `${fromName} → ${toName}` : 'Kam pojedete?');
  // A search that found its stops goes to the recents list.
  $effect(() => {
    const { from, to } = query;
    if (search.data && from !== null && to !== null) untrack(() => rememberSearch(from, to));
  });
  /** The stops as picked in the form, live; the URL's once a search runs. */
  let picked = $state<{ from: number | null; to: number | null }>({ from: null, to: null });

  /**
   * The map shows what the panel shows: the picked start and destination, and once results are
   * in, the opened connection (else the first) drawn leg by leg in its lines' colours.
   */
  const shown = $derived(connections.find((c) => connectionKey(c) === opened) ?? connections[0]);
  $effect(() => {
    const all = stopsList() ?? [];
    const placeOf = (id: number | null) => {
      const name = id === null ? undefined : all.find((s) => s.id === id)?.name;
      return name ? POSITIONS[name] : undefined;
    };
    if (ready && shown) {
      const paths = shown.legs.flatMap((leg) => {
        const id = leg.line.number;
        if (!LINES[id]) return [];
        return [
          {
            coords: linePath(
              id,
              leg.stops.map((s) => s.stop.name)
            ),
            color: leg.line.color
          }
        ];
      });
      const marks: Record<number, 'board' | 'alight'> = {};
      for (const leg of shown.legs) {
        marks[leg.from.id] ??= 'board';
        marks[leg.to.id] = 'alight';
      }
      marks[shown.legs[0].from.id] = 'board';
      // The bus of the leg under way now; only for a search from now, where times are today's.
      let bus = null;
      if (!query.time && query.mode === 'departure')
        for (const leg of shown.legs) {
          if (!LINES[leg.line.number]) continue;
          const calls = leg.stops.map((call) => ({
            arr: absolute(call.arrival_time, call.arrival_day_offset),
            dep: absolute(call.departure_time, call.departure_day_offset)
          }));
          const names = leg.stops.map((call) => call.stop.name);
          const at = busAt(leg.line.number, names, tripPosition(calls, clock.now));
          if (at) {
            bus = { at, color: leg.line.color, label: `Spoj linky ${leg.line.number}` };
            break;
          }
        }
      return focusMap({
        bus,
        onselect: (stop) => goto(resolve('/(map)/stops/[id]', { id: String(stop.id) })),
        stops: all,
        paths,
        marks,
        focusLines: [...new Set(shown.legs.map((leg) => leg.line.number))],
        focusStops: shown.legs.flatMap((leg) => leg.stops.map((s) => s.stop.id)),
        fit: paths.flatMap((path) => path.coords)
      });
    }
    const ends = [picked.from, picked.to].filter((id): id is number => id !== null);
    const marks: Record<number, 'board' | 'alight'> = {};
    if (picked.from !== null) marks[picked.from] = 'board';
    if (picked.to !== null) marks[picked.to] = 'alight';
    const fit = ends.map(placeOf).filter((p) => p !== undefined);
    return focusMap({
      stops: all,
      marks,
      focusStops: ends.length ? ends : null,
      selected: ends.length === 1 ? ends[0] : null,
      fit: fit.length ? fit : null,
      // A stop clicked on the map fills Odkud first, then Kam (which runs the search).
      onselect: (stop) => {
        const next =
          picked.from === null
            ? { from: stop.id, to: picked.to }
            : picked.to === null && stop.id !== picked.from
              ? { from: picked.from, to: stop.id }
              : { from: stop.id, to: null };
        void goto(plannerHref({ ...query, ...next }), { keepFocus: true, noScroll: true });
      }
    });
  });

  const searchedTime = $derived(search.data?.time ?? query.time);
  const errorKind = $derived<Failure | null>(
    search.error === 'missing' ? 'missing' : search.error ? 'unavailable' : null
  );
</script>

<svelte:head>
  <title
    >{ready && fromName && toName ? `Spojení ${fromName} → ${toName}` : 'Vyhledání spojení'} · TdA</title
  >
  <meta
    name="description"
    content="Vyhledejte autobusové spojení mezi zastávkami kampusu, včetně přestupů a časů na každé zastávce."
  />
</svelte:head>

<div class="planner-page">
  <!-- The map panel's view switch names the view; the heading stays for screen readers only. -->
  <div class="sr-only">
    <PageHeader kind="Spojení" icon={Navigation} {title} />
  </div>
  {#if sameStop}
    <p class="same-stop" role="alert">
      Start a cíl jsou stejná zastávka. Vyberte jinou cílovou zastávku.
    </p>
  {/if}

  <section class="form-section" aria-labelledby="planner-form-title">
    <h2 id="planner-form-title" class="sr-only">Zadání cesty</h2>
    <PlannerForm
      {query}
      headingId="planner-form-title"
      compact
      onchange={(value) => (picked = value)}
    />
  </section>

  {#if !ready && !sameStop}
    <PlannerEmpty />
  {/if}

  {#if ready}
    <section class="results" aria-labelledby="results-title" aria-busy={search.loading}>
      <h2 id="results-title" class="results-title">
        {query.mode === 'arrival' ? 'Příjezd do' : 'Odjezd po'}
        {searchedTime ?? 'teď'}{#if gapNote}<span class="gap-note">{gapNote}</span>{/if}
      </h2>
      <p class="sr-only" aria-live="polite">{pageNote || `${connections.length} spojení`}</p>

      {#if errorKind}
        <PageState
          title={errorKind === 'missing'
            ? 'Zastávka nebyla nalezena'
            : 'Spojení se nepodařilo vyhledat'}
          description={errorKind === 'missing'
            ? 'Jedna ze zadaných zastávek neexistuje. Vyberte ji znovu ze seznamu.'
            : undefined}
          headingLevel={2}
          error
          onretry={errorKind === 'unavailable' ? () => search.retry() : undefined}
        />
      {:else if !search.data && search.loading}
        <LoadingState label="Hledáme spojení…" />
      {:else if search.data && connections.length === 0 && rollover === 'loading'}
        <LoadingState label="Dnes už nic nejede, hledáme zítřejší spojení…" />
      {:else if search.data && connections.length === 0}
        <PageState
          title={query.mode === 'arrival'
            ? 'Do tohoto času žádné spojení nedojede'
            : rollover === 'failed'
              ? 'Zítřejší spojení se nepodařilo načíst'
              : 'Mezi těmito zastávkami nejede žádné spojení'}
          headingLevel={2}
          error={rollover === 'failed'}
          onretry={rollover === 'failed' ? () => search.retry() : undefined}
        />
        {#if (rollover === 'none' || query.mode === 'arrival') && fromName && toName}
          <div class="no-route">
            <p>
              Nenašli jsme žádný spoj ani s přestupem. Podívejte se, které linky zastávkami
              projíždějí, a vyberte jízdní řád:
            </p>
            <ul>
              {#each [fromName, toName] as name (name)}
                <li>
                  <strong>{name}</strong>
                  {#each linesOf(name) as id (id)}
                    <a
                      class="line-link"
                      href={resolve('/(map)/lines/[id]', { id: String(LINES[id].apiId) })}
                      ><LineBadge {id} /> <span>jízdní řád</span></a
                    >
                  {:else}
                    <span>žádná linka</span>
                  {/each}
                </li>
              {/each}
            </ul>
          </div>
        {/if}
        {#if query.mode === 'arrival'}
          <div class="pager">
            <Button variant="outline" href={plannerHref({ ...query, mode: 'departure' })}
              >Hledat podle odjezdu</Button
            >
          </div>
        {/if}
      {:else if connections.length}
        {#if !noMore.earlier}
          <div class="pager">
            <Button variant="outline" onclick={() => more('earlier')} disabled={!!paging}>
              <ChevronUp aria-hidden="true" />
              {paging === 'earlier' ? 'Načítáme…' : 'Dřívější spojení'}
            </Button>
            {#if pageError === 'earlier'}
              <p class="page-error" role="alert">Nepodařilo se načíst, zkuste to znovu.</p>
            {/if}
          </div>
        {/if}
        <ol class="list" bind:this={list}>
          {#each connections as connection, index (connectionKey(connection))}
            {@const key = connectionKey(connection)}
            {@const day = connection.departure_day_offset}
            {#if day !== 0 && (index === 0 || connections[index - 1].departure_day_offset !== day)}
              <li class="day-divider" aria-hidden="true"><span>{dayLabel(day)}</span></li>
            {/if}
            <li>
              <ConnectionCard
                {connection}
                id={`c-${key}`}
                open={opened === key}
                countdown={countdownOf(connection)}
                fastest={connection.duration_minutes === fastestMinutes}
                ontoggle={() => toggle(key)}
              />
            </li>
          {/each}
        </ol>
        <div class="pager">
          {#if noMore.later}
            <p class="pager-end" role="status">Žádná další pozdější spojení nejsou.</p>
          {:else}
            {#if endOfDay}
              <p class="day-end" role="status">
                <Moon class="size-5" aria-hidden="true" /> Dnes už žádné další spojení
              </p>
            {/if}
            <Button variant="outline" onclick={() => more('later')} disabled={!!paging}>
              <ChevronDown aria-hidden="true" />
              {paging === 'later'
                ? 'Načítáme…'
                : endOfDay
                  ? 'Zobrazit zítřejší spojení'
                  : 'Pozdější spojení'}
            </Button>
          {/if}
          {#if pageError === 'later'}
            <p class="page-error" role="alert">Nepodařilo se načíst, zkuste to znovu.</p>
          {/if}
        </div>
      {/if}
    </section>
  {/if}
</div>

<style>
  .planner-page {
    display: grid;
    gap: 1.5rem;
    width: 100%;
  }
  .planner-page :global(.page-header) {
    margin-bottom: 0;
  }
  .results {
    display: grid;
    gap: 0.75rem;
  }
  .results-title {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0.5rem;
    font-size: var(--fs-xl);
    font-variant-numeric: tabular-nums;
    font-weight: var(--weight-strong);
  }

  .gap-note {
    padding: 0.125rem 0.625rem;
    border-radius: var(--radius);
    background: var(--muted);
    font-size: var(--fs-lg);
  }
  .day-end {
    display: flex;
    flex-basis: 100%;
    justify-content: center;
    align-items: center;
    gap: 0.5rem;
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .pager-end {
    font-size: var(--fs-sm);
  }
  .list {
    display: grid;
    gap: 0.75rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }
  .pager {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    align-items: center;
    gap: 0.5rem 0.75rem;
  }
  .day-divider {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding-block: 0.25rem;
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .day-divider::before,
  .day-divider::after {
    content: '';
    flex: 1;
    height: 2px;
    background: var(--foreground);
  }
  .same-stop {
    color: var(--destructive);
    font-weight: var(--weight-strong);
  }
  @media (max-width: 767px) {
    .planner-page {
      padding-bottom: calc(5rem + env(safe-area-inset-bottom));
    }
  }
  .no-route {
    display: grid;
    gap: 0.75rem;
    max-width: 42rem;
  }
  .no-route ul {
    display: grid;
    gap: 0.5rem;
  }
  .no-route li {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem 0.75rem;
  }
  .line-link {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.75rem;
    padding: 0 0.75rem 0 0.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    font-weight: var(--weight-strong);
  }
  .line-link:hover {
    background: var(--muted);
  }
  .page-error {
    color: var(--destructive);
    font-size: var(--fs-sm);
  }
</style>
