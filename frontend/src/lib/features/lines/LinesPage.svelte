<script lang="ts">
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  import { ArrowRight, BusFront, Route as RouteIcon, TramFront } from '@lucide/svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import type { Route } from '$lib/api/generated';
  import PageState from '$lib/components/PageState.svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';
  import {
    LINES,
    LINE_IDS,
    linesOf,
    loadNetwork,
    networkFailed,
    networkReady,
    type Line
  } from '$lib/features/network/lines.svelte';
  import { loadStops, stopsFailed, stopsList } from '$lib/features/network/stops-store.svelte';
  import Isa from './Isa.svelte';
  import { isPale, typeNoun, zOrZe } from './line-style';

  onMount(() => {
    void loadNetwork();
    void loadStops();
  });

  const lines = $derived(LINE_IDS.map((id) => LINES[id]));

  function outbound(line: Line): Route | undefined {
    return line.routes.find((route) => route.bound === 'outbound') ?? line.routes[0];
  }

  // Step-free access comes from the stops API; routes only carry ids and names.
  const stepFree = $derived(
    new Map((stopsList() ?? []).map((stop) => [stop.id, stop.wheelchair_accessible]))
  );

  /** Stops served by more than one line, with those lines, for the interchange index. */
  const interchanges = $derived.by(() => {
    // Local lookup rebuilt on every change, never mutated reactively.
    // eslint-disable-next-line svelte/prefer-svelte-reactivity
    const byId = new Map<number, { id: number; name: string; lines: Line[] }>();
    for (const line of lines) {
      for (const stop of outbound(line)?.stops ?? []) {
        const entry = byId.get(stop.id) ?? { id: stop.id, name: stop.name, lines: [] };
        if (!entry.lines.includes(line)) entry.lines.push(line);
        byId.set(stop.id, entry);
      }
    }
    return [...byId.values()]
      .filter((stop) => stop.lines.length > 1)
      .sort((a, b) => b.lines.length - a.lines.length || a.name.localeCompare(b.name, 'cs'));
  });

  function others(line: Line, stopName: string) {
    return linesOf(stopName).filter((number) => number !== line.number);
  }
</script>

<svelte:head>
  <title>Linky · TdA</title>
</svelte:head>

<PageHeader kind="Přehled linek" icon={RouteIcon} title="Všechny linky">
  {#snippet meta()}Trasy, zastávky a přestupy všech linek kampusu.{/snippet}
</PageHeader>
<div class="intro">
  <!-- A map key, not decoration: the diagrams below use these symbols. -->
  <p class="key-title">Vysvětlivky</p>
  <ul class="key" aria-label="Vysvětlivky">
    <li><span class="key-dot" aria-hidden="true"></span>zastávka</li>
    <li><span class="key-dot interchange" aria-hidden="true"></span>přestup</li>
    <li><span class="key-dot terminus" aria-hidden="true"></span>konečná</li>
    <li><Isa size={16} />bezbariérový nástup</li>
  </ul>
</div>

{#if networkFailed()}
  <PageState title="Linky se nepodařilo načíst" error onretry={() => loadNetwork(true)} />
{:else if !networkReady()}
  <LoadingState label="Načítáme linky…" />
{:else if lines.length === 0}
  <PageState title="Zatím nejsou evidované žádné linky" />
{:else}
  <h2 class="section-title list-title">Linky ({lines.length})</h2>
  <ul class="lines">
    {#each lines as line (line.number)}
      {@const route = outbound(line)}
      {@const stops = route?.stops ?? []}
      {@const accessible = stops.filter((stop) => stepFree.get(stop.id)).length}
      {@const via = stops.filter(
        (stop, i) => i > 0 && i < stops.length - 1 && others(line, stop.name).length
      )}
      <li
        class="line"
        class:line-cased={isPale(line.color)}
        style:--line={line.color}
        style:--line-ink={line.ink}
      >
        <div class="head">
          <span class="plate tabular" aria-hidden="true">{line.number}</span>
          <div class="naming">
            <h3><span class="sr-only">Linka {line.number}: </span>{line.name}</h3>
            <p class="meta">
              <span class="meta-item">
                {#if line.type.toLowerCase() === 'tram'}
                  <TramFront class="size-4" aria-hidden="true" />
                {:else}
                  <BusFront class="size-4" aria-hidden="true" />
                {/if}
                {typeNoun(line.type)}
              </span>
              {#if stops.length && stopsList()}
                <span class="meta-item tabular">
                  <Isa size={16} />
                  {accessible}
                  {zOrZe(stops.length)}
                  {stops.length} s bezbariérovým nástupem
                </span>
              {:else if stops.length && stopsFailed()}
                <span class="meta-item">bezbariérový nástup nezjištěn</span>
              {/if}
            </p>
          </div>
        </div>

        {#if stops.length}
          <!-- Wide screens: the outbound route as a strip diagram. -->
          <ol class="strip" style:--count={stops.length} aria-label={`Trasa linky ${line.number}`}>
            {#each stops as stop, index (stop.id)}
              {@const transfer = others(line, stop.name)}
              {@const terminus = index === 0 || index === stops.length - 1}
              <li class:terminus class:interchange={transfer.length > 0}>
                <span class="dot" aria-hidden="true"></span>
                <a href={resolve('/stops/[id]', { id: String(stop.id) })}
                  >{stop.name}{#if terminus}<span class="sr-only">, konečná</span>{/if}</a
                >
                {#if transfer.length}
                  <span class="badges" aria-hidden="true">
                    {#each transfer as other (other)}
                      <span
                        class="mini tabular"
                        style:background={LINES[other].color}
                        style:color={LINES[other].ink}>{other}</span
                      >
                    {/each}
                  </span>
                  <span class="sr-only">přestup na linky {transfer.join(', ')}</span>
                {/if}
              </li>
            {/each}
          </ol>

          <!-- Phones: no sideways scrolling; where the line changes with others, in words. -->
          <p class="via">
            {#if via.length}
              přes
              {#each via as stop, i (stop.id)}
                {i > 0 ? ' · ' : ''}<span class="via-stop"
                  >{stop.name}
                  <span class="badges" aria-hidden="true">
                    {#each others(line, stop.name) as other (other)}
                      <span
                        class="mini tabular"
                        style:background={LINES[other].color}
                        style:color={LINES[other].ink}>{other}</span
                      >
                    {/each}
                  </span><span class="sr-only">
                    (přestup na {others(line, stop.name).join(', ')})</span
                  ></span
                >
              {/each}
            {:else}
              {stops.length} zastávek, bez přestupu
            {/if}
          </p>
        {:else}
          <p class="via empty">Trasa zatím není zadaná</p>
        {/if}
        <a class="more" href={resolve('/lines/[id]', { id: String(line.apiId) })}>
          Víc informací<span class="sr-only"> o lince {line.number}</span>
          <ArrowRight class="size-4" aria-hidden="true" />
        </a>
      </li>
    {/each}
  </ul>

  {#if interchanges.length}
    <section class="interchanges" aria-labelledby="interchanges-heading">
      <h2 id="interchanges-heading" class="section-title">Přestupní zastávky</h2>
      <ul>
        {#each interchanges as stop (stop.id)}
          <li>
            <a href={resolve('/stops/[id]', { id: String(stop.id) })}>
              <span class="stop-name">{stop.name}</span>
              <span class="badges" aria-hidden="true">
                {#each stop.lines as served (served.number)}
                  <span
                    class="mini md tabular"
                    style:background={served.color}
                    style:color={served.ink}>{served.number}</span
                  >
                {/each}
              </span>
              <span class="sr-only">
                linky {stop.lines.map((served) => served.number).join(', ')}</span
              >
            </a>
          </li>
        {/each}
      </ul>
    </section>
  {/if}
{/if}

<style>
  .intro {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem 1.25rem;
    align-items: center;
    justify-content: flex-start;
    margin-block: 0 0.5rem;
  }
  .key-title {
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .key {
    display: flex;
    flex-wrap: wrap;
    gap: 0.375rem 1.125rem;
    font-size: var(--fs-sm);
    font-weight: var(--weight-regular);
    color: var(--muted-foreground);
  }
  .key li {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
  }
  .key-dot {
    width: 0.875rem;
    height: 0.875rem;
    border-radius: 999px;
    border: 3px solid var(--muted-foreground);
    background: var(--background);
  }
  .key-dot.interchange {
    width: 1.0625rem;
    height: 1.0625rem;
    border-color: var(--foreground);
  }
  .key-dot.terminus {
    background: var(--muted-foreground);
  }

  /* Rows separated by rules, no boxes: the plate and the rail carry the line colour. */
  .lines {
    border-top: 1px solid var(--border);
  }
  .line {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    column-gap: 1rem;
    align-items: start;
    padding-block: 1.5rem 1.25rem;
    border-bottom: 1px solid var(--border);
  }
  .strip,
  .via {
    grid-column: 1 / -1;
  }
  .more {
    grid-row: 1;
    grid-column: 2;
    align-self: center;
  }

  .head {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 1rem;
  }
  .plate {
    display: grid;
    place-items: center;
    min-width: 3.5rem;
    height: 3.5rem;
    padding-inline: 0.5rem;
    border-radius: var(--radius-md);
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: var(--fs-2xl);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  h2 {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
  .meta {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem 1rem;
    margin-top: 0.25rem;
    font-size: var(--fs-base);
    font-weight: var(--weight-regular);
    color: var(--muted-foreground);
  }
  .meta-item {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
  }
  .more {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.75rem;
    padding-inline: 0.25rem;
    font-weight: var(--weight-strong);
    color: var(--primary);
    white-space: nowrap;
    text-underline-offset: 4px;
  }
  .more:hover {
    text-decoration: underline;
  }
  .more :global(svg) {
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .more:hover :global(svg) {
    transform: translateX(3px);
  }

  /*
   * Strip diagram. Equal columns per stop; a single rail runs from the first stop's centre to
   * the last. Pale lines get a dark keyline (--line-stroke) on light surfaces.
   */
  .strip {
    position: relative;
    display: grid;
    grid-template-columns: repeat(var(--count), minmax(0, 1fr));
    margin-top: 1.25rem;
  }
  .strip::before {
    content: '';
    position: absolute;
    top: 0.5625rem;
    left: calc(50% / var(--count));
    right: calc(50% / var(--count));
    height: 0.375rem;
    translate: 0 -50%;
    border-radius: 999px;
    background: var(--line);
    box-shadow: 0 0 0 1.5px var(--line-stroke, var(--line));
  }
  .strip li {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding-inline: 0.25rem;
    text-align: center;
  }
  .dot {
    width: 0.875rem;
    height: 0.875rem;
    margin-top: 0.125rem;
    border-radius: 999px;
    border: 3px solid var(--line-stroke, var(--line));
    background: var(--background);
  }
  .interchange .dot {
    width: 1.125rem;
    height: 1.125rem;
    margin-top: 0;
    border-color: var(--foreground);
  }
  .terminus .dot {
    width: 1.125rem;
    height: 1.125rem;
    margin-top: 0;
    background: var(--line);
  }
  .strip a {
    /* Generous hit area around a short label. */
    padding: 0.5rem 0.375rem;
    font-size: var(--fs-base);
    font-weight: var(--weight-regular);
    line-height: 1.2;
    border-radius: var(--radius-sm);
    text-underline-offset: 3px;
  }
  .terminus a,
  .interchange a {
    font-weight: var(--weight-strong);
  }
  .strip a:hover {
    text-decoration: underline;
  }
  .badges {
    display: inline-flex;
    gap: 0.25rem;
    vertical-align: middle;
  }
  .mini {
    display: inline-grid;
    place-items: center;
    min-width: 1.25rem;
    height: 1.25rem;
    padding-inline: 0.25rem;
    border-radius: var(--radius-sm);
    font-size: var(--fs-xs);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  .mini.md {
    min-width: 1.625rem;
    height: 1.625rem;
    font-size: var(--fs-sm);
  }

  .via {
    display: none;
    margin-top: 0.875rem;
    font-size: var(--fs-base);
    line-height: 1.6;
    color: var(--muted-foreground);
  }
  .via-stop {
    font-weight: var(--weight-strong);
    color: var(--foreground);
  }
  .via.empty {
    display: block;
  }

  .interchanges {
    margin-top: 2.5rem;
  }
  .interchanges ul {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 16rem), 1fr));
    column-gap: 2rem;
  }
  .interchanges a {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    min-height: 3.25rem;
    border-bottom: 1px solid var(--border);
  }
  .stop-name {
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .interchanges a:hover .stop-name {
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: var(--radius-sm);
  }

  @media (max-width: 767px) {
    .strip {
      display: none;
    }
    .via {
      display: block;
    }
    .head {
      align-items: start;
    }
    .more {
      grid-row: auto;
      grid-column: 1 / -1;
      justify-self: start;
      margin-top: 0.25rem;
    }
    .plate {
      min-width: 3rem;
      height: 3rem;
      font-size: var(--fs-xl);
    }
    h2 {
      font-size: var(--fs-lg);
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .more :global(svg) {
      transition: none;
    }
    .more:hover :global(svg) {
      transform: none;
    }
  }
</style>
