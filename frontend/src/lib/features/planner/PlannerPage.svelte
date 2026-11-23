<script lang="ts">
  import { untrack } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/state';
  import { ChevronDown, ChevronUp, Navigation } from '@lucide/svelte';
  import { searchConnections, type Connection, type ConnectionSearch } from '$lib/api/generated';
  import { createResource, ResourceError } from '$lib/api/resource.svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { Button } from '$lib/ui/button';
  import { stopsList } from '$lib/features/network/stops-store.svelte';
  import { clock } from '$lib/features/timetable/clock.svelte';
  import { clock as clockText, countdown, minutesUntil } from '$lib/features/timetable/time';
  import ConnectionCard from './ConnectionCard.svelte';
  import PlannerForm from './PlannerForm.svelte';
  import { absolute, connectionKey, dayNote, parseQuery, plannerHref, shifted } from './planner';

  const PAGE = 5;

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

  async function more(direction: 'earlier' | 'later') {
    const list = connections;
    const { from, to } = query;
    if (!list.length || paging || from === null || to === null) return;
    const edge =
      direction === 'later'
        ? absolute(list.at(-1)!.departure_time, list.at(-1)!.departure_day_offset) + 1
        : absolute(list[0].arrival_time, list[0].arrival_day_offset) - 1;
    const shift = Math.floor(edge / 1440);
    paging = direction;
    pageError = null;
    try {
      const data = await fetchPage(
        from,
        to,
        clockText(edge),
        direction === 'later' ? 'departure' : 'arrival'
      );
      const before = connections.length;
      extra = [...extra, ...data.connections.map((c) => shifted(c, shift))];
      if (connections.length === before) noMore = { ...noMore, [direction]: true };
    } catch (error) {
      console.error(`Planner: loading ${direction} connections failed`, error);
      pageError = direction;
    } finally {
      paging = null;
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

  const fastestMinutes = $derived(
    connections.length > 1 ? Math.min(...connections.map((c) => c.duration_minutes)) : null
  );

  /** "zítra · út 24. 11." for a day divider; the search day itself needs none. */
  function dayLabel(offset: number) {
    const date = new Date(clock.now);
    date.setDate(date.getDate() + offset);
    const day = date.toLocaleDateString('cs-CZ', {
      weekday: 'short',
      day: 'numeric',
      month: 'numeric'
    });
    return `${dayNote(offset)} · ${day}`;
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
  <PageHeader kind="Spojení" icon={Navigation} {title}>
    {#snippet meta()}
      {#if sameStop}
        <span class="same-stop" role="alert"
          >Start a cíl jsou stejná zastávka. Vyberte jinou cílovou zastávku.</span
        >
      {/if}
    {/snippet}
  </PageHeader>

  <section class="form-section" aria-labelledby="planner-form-title">
    <h2 id="planner-form-title" class="sr-only">Zadání cesty</h2>
    <PlannerForm {query} headingId="planner-form-title" />
  </section>

  {#if ready}
    <section class="results" aria-labelledby="results-title" aria-busy={search.loading}>
      <h2 id="results-title" class="results-title">
        Spojení{#if searchedTime}<span class="when-chip"
            >{query.mode === 'arrival' ? 'příjezd do' : 'odjezd od'} {searchedTime}</span
          >{/if}
      </h2>
      <p class="sr-only" aria-live="polite">{connections.length} spojení</p>

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
      {:else if search.data && connections.length === 0}
        <PageState
          title="Žádné spojení"
          description="Mezi těmito zastávkami během celého dne nic nejede. Zkuste jinou zastávku poblíž nebo jiný směr."
          headingLevel={2}
        />
      {:else if connections.length}
        <div class="pager">
          {#if noMore.earlier}
            <p class="pager-end">Dřívější spojení v tento den nejsou.</p>
          {:else}
            <Button variant="outline" onclick={() => more('earlier')} disabled={!!paging}>
              <ChevronUp aria-hidden="true" />
              {paging === 'earlier' ? 'Načítáme…' : 'Dřívější spojení'}
            </Button>
          {/if}
          {#if pageError === 'earlier'}
            <p class="page-error" role="alert">Nepodařilo se načíst, zkuste to znovu.</p>
          {/if}
        </div>
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
            <p class="pager-end">Pozdější spojení v tento den nejsou.</p>
          {:else}
            <Button variant="outline" onclick={() => more('later')} disabled={!!paging}>
              <ChevronDown aria-hidden="true" />
              {paging === 'later' ? 'Načítáme…' : 'Pozdější spojení'}
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
    max-width: 60rem;
    margin-inline: auto;
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
    gap: 0.5rem 0.75rem;
    font-size: var(--fs-xl);
    font-weight: var(--weight-strong);
  }
  .when-chip {
    padding: 0.125rem 0.5rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    font-size: var(--fs-base);
    font-variant-numeric: tabular-nums;
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
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .day-divider::before,
  .day-divider::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--border);
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
  .page-error {
    color: var(--destructive);
    font-size: var(--fs-sm);
  }
</style>
