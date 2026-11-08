<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight, ArrowLeftRight, ChevronDown } from '@lucide/svelte';
  import { getLine, getLines, type LineDetail } from '$lib/api/generated';
  import PageState from '$lib/components/PageState.svelte';
  import { Skeleton } from '$lib/ui/skeleton';
  import { inkFor, typeLabel } from './line-style';

  let lines = $state<LineDetail[] | null>(null);
  let failed = $state(false);
  let attempt = $state(0);
  let open = $state<number | null>(null);

  $effect(() => {
    void attempt;
    failed = false;
    lines = null;
    const controller = new AbortController();
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]);
    (async () => {
      try {
        const { data } = await getLines({ throwOnError: true, signal });
        // The list has no routes; details are small, so fetch them together.
        lines = await Promise.all(
          data.map(async (line) => {
            const { data: detail } = await getLine({
              path: { id: line.id },
              throwOnError: true,
              signal
            });
            return detail;
          })
        );
      } catch {
        if (!controller.signal.aborted) failed = true;
      }
    })();
    return () => controller.abort();
  });

  /** Lines that share at least one stop with the given line, by number. */
  function transfers(line: LineDetail) {
    const stops = new Set(line.routes.flatMap((route) => route.stops.map((stop) => stop.id)));
    return (lines ?? []).filter(
      (other) =>
        other.id !== line.id &&
        other.routes.some((route) => route.stops.some((stop) => stops.has(stop.id)))
    );
  }

  function outbound(line: LineDetail) {
    return line.routes.find((route) => route.bound === 'outbound') ?? line.routes[0];
  }
</script>

<svelte:head>
  <title>Linky | Think different Academy</title>
</svelte:head>

<header class="intro">
  <h1>Linky</h1>
  {#if lines}
    <p class="tabular">
      {lines.length}
      {lines.length === 1 ? 'linka' : lines.length < 5 ? 'linky' : 'linek'}
    </p>
  {/if}
</header>

{#if failed}
  <PageState title="Linky se nepodařilo načíst" error onretry={() => attempt++} />
{:else if !lines}
  <p role="status" class="sr-only">Načítáme linky…</p>
  <ul class="grid" aria-hidden="true">
    {#each [0, 1, 2] as item (item)}
      <li><Skeleton class="h-44 w-full rounded-lg" /></li>
    {/each}
  </ul>
{:else if lines.length === 0}
  <PageState title="Zatím nejsou evidované žádné linky" />
{:else}
  <ul class="grid">
    {#each lines as line (line.id)}
      {@const route = outbound(line)}
      {@const others = transfers(line)}
      {@const ink = inkFor(line.color)}
      <li class="card" style:--line={line.color}>
        <div class="head">
          <span class="badge tabular" style:background={line.color} style:color={ink}
            >{line.number}</span
          >
          <div class="title">
            <h2>{line.name}</h2>
            <p class="type">{typeLabel(line.type)} linka</p>
          </div>
        </div>

        {#if route && route.stops.length}
          <p class="ends">
            <span>{route.stops[0].name}</span>
            {#if line.routes.length > 1}
              <ArrowLeftRight class="size-4 shrink-0" aria-label="oběma směry" />
            {:else}
              <ArrowRight class="size-4 shrink-0" aria-label="do" />
            {/if}
            <span>{route.direction}</span>
          </p>
          <dl class="facts">
            <div>
              <dt>Zastávek</dt>
              <dd class="tabular">{route.stops.length}</dd>
            </div>
            <div>
              <dt>Směry</dt>
              <dd class="tabular">{line.routes.length}</dd>
            </div>
            <div>
              <dt>Přestup</dt>
              <dd>
                {#if others.length}
                  <span class="mini-badges">
                    {#each others as other (other.id)}
                      <span
                        class="mini tabular"
                        style:background={other.color}
                        style:color={inkFor(other.color)}
                        aria-label={`Linka ${other.number}`}>{other.number}</span
                      >
                    {/each}
                  </span>
                {:else}
                  —
                {/if}
              </dd>
            </div>
          </dl>

          <button
            type="button"
            class="toggle"
            aria-expanded={open === line.id}
            aria-controls={`stops-${line.id}`}
            onclick={() => (open = open === line.id ? null : line.id)}
          >
            Zastávky na trase
            <ChevronDown class="size-4 chevron" aria-hidden="true" />
          </button>
          {#if open === line.id}
            <ol class="route" id={`stops-${line.id}`}>
              {#each route.stops as stop (stop.position)}
                <li>
                  <a href={resolve('/stops/[id]', { id: String(stop.id) })}>{stop.name}</a>
                </li>
              {/each}
            </ol>
          {/if}
        {:else}
          <p class="ends muted">Trasa zatím není zadaná</p>
        {/if}
      </li>
    {/each}
  </ul>
{/if}

<style>
  .intro {
    display: flex;
    align-items: baseline;
    gap: 0.75rem;
    margin-block: 0.5rem 1.5rem;
  }
  h1 {
    font-size: 2rem;
    font-weight: var(--font-display-weight);
    letter-spacing: var(--tracking-display);
  }
  .intro p {
    color: var(--muted-foreground);
  }
  .grid {
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr));
    align-items: start;
  }
  .card {
    position: relative;
    overflow: hidden;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
    padding: 1.25rem 1.25rem 1rem;
    box-shadow: var(--shadow-pop);
  }
  /* The line colour runs along the top edge like a route stripe. */
  .card::before {
    content: '';
    position: absolute;
    inset: 0 0 auto;
    height: 0.375rem;
    background: var(--line);
  }
  .head {
    display: flex;
    align-items: center;
    gap: 0.875rem;
  }
  .badge {
    display: inline-grid;
    place-items: center;
    min-width: 3rem;
    height: 3rem;
    padding-inline: 0.5rem;
    border-radius: 0.375rem;
    font-size: 1.5rem;
    font-weight: 800;
    line-height: 1;
  }
  .title {
    min-width: 0;
  }
  h2 {
    font-size: 1.125rem;
    font-weight: 650;
    line-height: 1.25;
  }
  .type {
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  .ends {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin-top: 1rem;
    font-weight: 600;
  }
  .muted {
    color: var(--muted-foreground);
    font-weight: 400;
  }
  .facts {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 0.5rem;
    margin-top: 1rem;
    padding-top: 0.875rem;
    border-top: 1px solid var(--border);
  }
  dt {
    font-size: 0.75rem;
    color: var(--muted-foreground);
  }
  dd {
    font-weight: 650;
  }
  .mini-badges {
    display: flex;
    gap: 0.25rem;
  }
  .mini {
    display: inline-grid;
    place-items: center;
    min-width: 1.375rem;
    height: 1.375rem;
    padding-inline: 0.25rem;
    border-radius: 0.25rem;
    font-size: 0.75rem;
    font-weight: 800;
  }
  .toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    margin-top: 0.875rem;
    min-height: 2.75rem;
    font-weight: 600;
    color: var(--primary);
    border-radius: calc(var(--radius) - 2px);
  }
  .toggle:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .toggle :global(.chevron) {
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .toggle[aria-expanded='true'] :global(.chevron) {
    transform: rotate(180deg);
  }
  /* Stops hang off a vertical line in the line colour, like a stop sign timeline. */
  .route {
    margin: 0.25rem 0 0.25rem 0.5rem;
    border-left: 3px solid var(--line);
  }
  .route li {
    position: relative;
    padding: 0.3rem 0 0.3rem 1rem;
  }
  .route li::before {
    content: '';
    position: absolute;
    left: -0.47rem;
    top: 50%;
    width: 0.75rem;
    height: 0.75rem;
    translate: 0 -50%;
    border: 3px solid var(--line);
    border-radius: 999px;
    background: var(--card);
  }
  .route a {
    text-underline-offset: 3px;
  }
  .route a:hover {
    text-decoration: underline;
  }
  .route a:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
    border-radius: 2px;
  }
  @media (prefers-reduced-motion: reduce) {
    .toggle :global(.chevron) {
      transition: none;
    }
  }
</style>
