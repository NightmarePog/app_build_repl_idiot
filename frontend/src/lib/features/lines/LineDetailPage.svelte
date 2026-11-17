<script lang="ts">
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  import { BusFront, TramFront } from '@lucide/svelte';
  import { getLine, type LineDetail, type Route } from '$lib/api/generated';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import { LINES, linesOf } from '$lib/features/network/lines.svelte';
  import { loadStops, stopsFailed, stopsList } from '$lib/features/network/stops-store.svelte';
  import Isa from './Isa.svelte';
  import { inkFor, isPale, typeLabel, zOrZe } from './line-style';

  let { id }: { id: string } = $props();

  let line = $state<LineDetail | null>(null);
  let failure = $state<'missing' | 'unavailable' | null>(null);
  let attempt = $state(0);

  $effect(() => {
    const lineId = Number(id);
    void attempt;
    line = null;
    failure = null;
    if (!Number.isSafeInteger(lineId) || lineId < 1) {
      failure = 'missing';
      return;
    }
    const controller = new AbortController();
    (async () => {
      try {
        const { data, response } = await getLine({
          path: { id: lineId },
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)])
        });
        if (controller.signal.aborted) return;
        if (response?.status === 404) failure = 'missing';
        else if (data) line = data;
        else failure = 'unavailable';
      } catch {
        if (!controller.signal.aborted) failure = 'unavailable';
      }
    })();
    return () => controller.abort();
  });

  // Step-free access comes from the stops API; the route only carries names and ids.
  $effect(() => {
    void loadStops();
  });
  const stepFree = $derived(
    new Map((stopsList() ?? []).map((stop) => [stop.id, stop.wheelchair_accessible]))
  );

  const bound = $derived(page.url.searchParams.get('smer') === 'inbound' ? 'inbound' : 'outbound');
  const route = $derived<Route | undefined>(
    line ? (line.routes.find((r) => r.bound === bound) ?? line.routes[0]) : undefined
  );
  const accessible = $derived(
    route ? route.stops.filter((stop) => stepFree.get(stop.id)).length : 0
  );

  /** Other lines at the stop, linked straight to that stop on their own diagram. */
  function transfersAt(stopName: string) {
    return linesOf(stopName)
      .filter((number) => number !== line?.number)
      .map((number) => LINES[number]);
  }
</script>

<svelte:head>
  <title>
    {failure === 'missing'
      ? 'Linka nenalezena'
      : line
        ? `Linka ${line.number}${route ? `, směr ${route.direction}` : ''}`
        : 'Detail linky'} | Think different Academy
  </title>
</svelte:head>

{#if failure}
  <div class="state">
    <PageState
      title={failure === 'missing' ? 'Linka nebyla nalezena' : 'Linku se nepodařilo načíst'}
      headingLevel={1}
      error={failure === 'unavailable'}
      onretry={failure === 'unavailable' ? () => attempt++ : undefined}
      href={failure === 'missing' ? resolve('/lines') : undefined}
      linkLabel="Přehled linek"
    />
  </div>
{:else if !line}
  <LoadingState label="Načítáme detail linky…" detail />
{:else}
  <article
    class="line"
    class:line-cased={isPale(line.color)}
    style:--line={line.color}
    style:--line-ink={inkFor(line.color)}
  >
    <header class="head">
      <h1>
        <span class="plate tabular" aria-hidden="true">{line.number}</span>
        <span><span class="sr-only">Linka {line.number}: </span>{line.name}</span>
      </h1>
      <p class="meta">
        {#if line.type.toLowerCase() === 'tram'}
          <TramFront class="size-4" aria-hidden="true" />
        {:else}
          <BusFront class="size-4" aria-hidden="true" />
        {/if}
        {typeLabel(line.type)} linka
      </p>
    </header>

    {#if !route || route.stops.length === 0}
      <PageState title="Trasa linky zatím není zadaná" />
    {:else}
      {#if line.routes.length > 1}
        <!-- Headsigns as tabs, like a stop timetable: pick where you are going. -->
        <nav class="directions" aria-label="Směr jízdy">
          {#each line.routes as option (option.id)}
            <a
              href={option.bound === 'inbound'
                ? resolve('/lines/[id]?smer=inbound', { id })
                : resolve('/lines/[id]', { id })}
              aria-current={option.id === route.id ? 'page' : undefined}
              data-sveltekit-replacestate
              data-sveltekit-noscroll
            >
              <span class="dir-label">směr</span>
              <span class="dir-name">{option.direction}</span>
            </a>
          {/each}
        </nav>
      {:else}
        <p class="single-direction">směr <strong>{route.direction}</strong></p>
      {/if}
      <p class="sr-only" aria-live="polite">Směr {route.direction}</p>

      <p class="summary">
        {#if stopsList()}
          <Isa size={18} />
          <span class="tabular">
            Bezbariérové zastávky: {accessible}
            {zOrZe(route.stops.length)}
            {route.stops.length}
          </span>
        {:else if stopsFailed()}
          Bezbariérovost zastávek se nepodařilo zjistit.
        {/if}
      </p>

      <ol class="diagram" aria-label={`Zastávky směr ${route.direction}`}>
        {#each route.stops as stop, index (stop.id)}
          {@const others = transfersAt(stop.name)}
          {@const first = index === 0}
          {@const last = index === route.stops.length - 1}
          <li
            id={`stop-${stop.id}`}
            class:terminus={first || last}
            class:interchange={others.length > 0}
          >
            <span class="dot" aria-hidden="true"></span>
            <div class="stop">
              <a class="name" href={resolve('/stops/[id]', { id: String(stop.id) })}>{stop.name}</a>
              {#if stepFree.get(stop.id)}
                <Isa size={18} label="bezbariérová zastávka" />
              {/if}
              {#if others.length}
                <span class="transfer">
                  <span class="transfer-label">přestup</span>
                  {#each others as other (other.number)}
                    <a
                      class="badge tabular"
                      href={resolve(`/lines/[id]#stop-${stop.id}`, { id: String(other.apiId) })}
                      style:background={other.color}
                      style:color={other.ink}
                      ><span aria-hidden="true">{other.number}</span><span class="sr-only"
                        >linka {other.number}</span
                      ></a
                    >
                  {/each}
                </span>
              {/if}
              {#if last}<span class="note">konečná</span>{/if}
            </div>
          </li>
        {/each}
      </ol>
    {/if}
  </article>
{/if}

<style>
  .state {
    margin-top: 1rem;
  }

  .head {
    margin-top: 1rem;
  }
  h1 {
    display: flex;
    align-items: center;
    gap: 1rem;
    font-size: clamp(1.625rem, 1.2rem + 1.4vw, 2.125rem);
    font-weight: var(--font-display-weight);
    letter-spacing: var(--tracking-display);
    line-height: 1.15;
  }
  .plate {
    display: grid;
    place-items: center;
    flex-shrink: 0;
    min-width: 4rem;
    height: 4rem;
    padding-inline: 0.625rem;
    border-radius: 0.375rem;
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: 2.375rem;
    font-weight: 800;
    letter-spacing: 0;
    line-height: 1;
  }
  .meta {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    margin: 0.5rem 0 0 5rem;
    font-size: 0.9375rem;
    font-weight: 500;
    color: var(--muted-foreground);
  }

  .directions {
    display: grid;
    grid-auto-columns: minmax(0, 1fr);
    grid-auto-flow: column;
    max-width: 36rem;
    margin-top: 1.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
  }
  .directions a {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-height: 3.5rem;
    padding: 0.5rem 1rem;
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .directions a + a {
    border-left: 1px solid var(--border);
  }
  .directions a:hover {
    background: var(--accent);
  }
  .directions a[aria-current='page'] {
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
  }
  .dir-label {
    font-size: 0.8125rem;
    font-weight: 500;
    opacity: 0.8;
  }
  .dir-name {
    font-size: 1.0625rem;
    font-weight: 700;
    line-height: 1.2;
  }
  .single-direction {
    margin-top: 1.75rem;
    font-size: 1.125rem;
  }
  .directions a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: -4px;
  }

  .summary {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 1.5rem;
    margin-top: 1rem;
    font-weight: 500;
    color: var(--muted-foreground);
  }

  /*
   * Vertical line diagram, as on a PID line timetable. Columns: rail with the stop symbol,
   * then the stop. The rail runs through every row and stops at the termini.
   */
  .diagram {
    max-width: 44rem;
    margin-top: 1.25rem;
  }
  .diagram li {
    position: relative;
    display: grid;
    grid-template-columns: 2rem minmax(0, 1fr);
    align-items: center;
    column-gap: 0.75rem;
    min-height: 3.5rem;
    scroll-margin-top: calc(var(--header-h) + 2rem);
  }
  .diagram li::before {
    content: '';
    position: absolute;
    left: calc(1rem - 0.1875rem);
    top: 0;
    bottom: 0;
    width: 0.375rem;
    background: var(--line);
    box-shadow: 0 0 0 1.5px var(--line-stroke, var(--line));
  }
  .diagram li:first-child::before {
    top: 50%;
  }
  .diagram li:last-child::before {
    bottom: 50%;
  }
  /* Arriving from a transfer link: show which stop was meant. */
  .diagram li:target {
    background: linear-gradient(to right, transparent 2.5rem, var(--accent) 2.5rem);
  }
  .dot {
    position: relative;
    z-index: 1;
    justify-self: center;
    width: 1rem;
    height: 1rem;
    border-radius: 999px;
    border: 3px solid var(--line-stroke, var(--line));
    background: var(--background);
  }
  .interchange .dot {
    width: 1.375rem;
    height: 1.375rem;
    border-color: var(--foreground);
  }
  .terminus .dot {
    width: 1.375rem;
    height: 1.375rem;
    background: var(--line);
  }
  .stop {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.625rem;
    padding-block: 0.375rem;
  }
  .name {
    padding-block: 0.5rem;
    font-size: 1.125rem;
    font-weight: 500;
    text-underline-offset: 3px;
  }
  .terminus .name,
  .interchange .name {
    font-weight: 700;
  }
  .name:hover {
    text-decoration: underline;
  }
  .transfer {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
  }
  .transfer-label {
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--muted-foreground);
  }
  .badge {
    position: relative;
    display: inline-grid;
    place-items: center;
    min-width: 1.75rem;
    height: 1.75rem;
    padding-inline: 0.375rem;
    border-radius: 0.3125rem;
    font-size: 0.875rem;
    font-weight: 800;
    line-height: 1;
  }
  /* 44px hit area around the small badge. */
  .badge::after {
    content: '';
    position: absolute;
    inset: -0.5rem;
  }
  .note {
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--muted-foreground);
  }
  .diagram a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: 4px;
  }

  @media (max-width: 639px) {
    .meta {
      margin-left: 0;
    }
    .plate {
      min-width: 3.25rem;
      height: 3.25rem;
      font-size: 1.875rem;
    }
    .directions {
      max-width: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .directions a {
      transition: none;
    }
  }
</style>
