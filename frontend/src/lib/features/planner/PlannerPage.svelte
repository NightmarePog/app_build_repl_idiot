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
  import { absolute, connectionKey, parseQuery, plannerHref, shifted } from './planner';

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
  /** Day context: the search runs for today unless the time already passed (then IDOS means the next day too). */
  const todayText = $derived(
    clock.now >= 0
      ? new Date().toLocaleDateString('cs-CZ', {
          weekday: 'long',
          day: 'numeric',
          month: 'numeric'
        })
      : ''
  );
  const whenText = $derived(
    searchedTime
      ? `Dnes (${todayText}), ${query.mode === 'arrival' ? 'příjezd do' : 'odjezd od'} ${searchedTime}${query.time ? '' : ' (teď)'}`
      : null
  );
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
  <PageHeader kind="Vyhledání spojení" icon={Navigation} {title}>
    {#snippet meta()}
      {#if ready && whenText}
        <span>{whenText}</span>
        {#if search.data}
          <span aria-hidden="true">·</span>
          <span>min. {search.data.min_transfer_minutes} min na přestup</span>
        {/if}
      {:else if sameStop}
        <span>Start a cíl jsou stejná zastávka. Vyberte jinou cílovou zastávku.</span>
      {:else}
        <span>Zadejte odkud a kam jedete. Najdeme přímé spoje i cesty s přestupem.</span>
      {/if}
    {/snippet}
  </PageHeader>

  <section class="form-section" aria-labelledby="planner-form-title">
    <h2 id="planner-form-title" class="sr-only">Zadání cesty</h2>
    <PlannerForm {query} headingId="planner-form-title" />
  </section>

  {#if ready}
    <section class="results" aria-labelledby="results-title" aria-busy={search.loading}>
      <div class="results-head">
        <h2 id="results-title" class="section-title">Nalezená spojení</h2>
        {#if connections.length}
          <p class="count" aria-live="polite">{connections.length} spojení</p>
        {/if}
      </div>

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
          <Button
            variant="outline"
            onclick={() => more('earlier')}
            disabled={!!paging || noMore.earlier}
          >
            <ChevronUp aria-hidden="true" />
            {noMore.earlier
              ? 'Dřívější spojení do 24 h nejsou'
              : paging === 'earlier'
                ? 'Načítáme…'
                : 'Dřívější spojení'}
          </Button>
          {#if pageError === 'earlier'}
            <p class="page-error" role="alert">Nepodařilo se načíst, zkuste to znovu.</p>
          {/if}
        </div>
        <ol class="list" bind:this={list}>
          {#each connections as connection (connectionKey(connection))}
            {@const key = connectionKey(connection)}
            <li>
              <ConnectionCard
                {connection}
                id={`c-${key}`}
                open={opened === key}
                countdown={countdownOf(connection)}
                ontoggle={() => toggle(key)}
              />
            </li>
          {/each}
        </ol>
        <div class="pager">
          <Button
            variant="outline"
            onclick={() => more('later')}
            disabled={!!paging || noMore.later}
          >
            <ChevronDown aria-hidden="true" />
            {noMore.later
              ? 'Další spojení do 24 h nejsou'
              : paging === 'later'
                ? 'Načítáme…'
                : 'Pozdější spojení'}
          </Button>
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
    max-width: 56rem;
  }
  .planner-page :global(.page-header) {
    margin-bottom: 0;
  }
  .results {
    display: grid;
    gap: 0.75rem;
  }
  .results-head {
    display: grid;
    gap: 0.25rem;
  }
  .count {
    color: var(--muted-foreground);
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
  .page-error {
    color: var(--destructive);
    font-size: var(--fs-sm);
  }
</style>
