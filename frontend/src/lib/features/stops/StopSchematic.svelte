<script lang="ts">
  import { LINES, loadNetwork, networkReady } from '$lib/features/network/lines.svelte';
  import { isPale } from '$lib/features/lines/line-style';
  import { lineDirectionHref } from '$lib/features/timetable/trips';

  /**
   * The lines through one stop as a schematic: each direction drawn as a short strip in the
   * line colour (previous stop, this stop, next stop). No map tiles, so it never renders empty.
   */
  let { stopId }: { stopId: number } = $props();

  $effect(() => {
    void loadNetwork();
  });

  const strips = $derived(
    Object.values(LINES)
      .flatMap((line) =>
        line.routes.flatMap((route) => {
          const index = route.stops.findIndex((stop) => stop.id === stopId);
          if (index < 0) return [];
          return [
            {
              line,
              route,
              previous: route.stops[index - 1] ?? null,
              next: route.stops[index + 1] ?? null
            }
          ];
        })
      )
      // Boardable directions first; a direction that ends here is only for getting off.
      .sort((a, b) => Number(!a.next) - Number(!b.next))
  );
</script>

<section class="schematic" aria-labelledby="schematic-heading">
  <h2 id="schematic-heading" class="section-title">Linky na zastávce</h2>
  {#if !networkReady()}
    <p class="note" role="status">Načítáme linky…</p>
  {:else if strips.length === 0}
    <p class="note">Tudy zatím žádná linka nejezdí.</p>
  {:else}
    <ul>
      {#each strips as { line, route, previous, next } (route.id)}
        <li
          class:line-cased={isPale(line.color)}
          class:ends={!next}
          style:--line={line.color}
          style:--line-ink={line.ink}
        >
          <a class="row-link" href={lineDirectionHref(line.apiId, route.bound, stopId)}>
            <span class="plate tabular" aria-hidden="true">{line.number}</span>
            <span class="what">
              <span class="title">Linka {line.number} → <strong>{route.direction}</strong></span>
              {#if next}
                <span class="hops">
                  <span>další zastávka <strong>{next.name}</strong></span>
                  {#if !previous}<span>výchozí zastávka linky</span>{/if}
                </span>
              {:else}
                <span class="hops">
                  <span class="end-badge">Konečná – nenastupujte</span>
                  spoje sem přijíždějí jen k výstupu
                </span>
              {/if}
            </span>
          </a>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  ul {
    display: grid;
    gap: 0.75rem;
  }
  li {
    border-left: 6px solid var(--line);
    border-radius: var(--radius-md);
    background: color-mix(in oklch, var(--line) 10%, var(--background));
  }
  li.ends {
    border-left-style: dashed;
    background: transparent;
    box-shadow: inset 0 0 0 1px var(--border);
  }
  .row-link {
    display: flex;
    align-items: center;
    gap: 0.875rem;
    padding: 0.75rem 1rem;
  }
  .row-link:hover .title {
    text-decoration: underline;
  }
  .plate {
    display: grid;
    flex-shrink: 0;
    place-items: center;
    min-width: 2.75rem;
    height: 2.75rem;
    border-radius: var(--radius-md);
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
  }
  .what {
    display: grid;
    gap: 0.25rem;
    min-width: 0;
  }
  .title {
    font-size: var(--fs-lg);
  }
  .hops {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem 0.75rem;
    align-items: center;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-regular);
  }
  .hops strong {
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .end-badge {
    padding: 0 0.5rem;
    border: 1.5px solid currentColor;
    border-radius: 999px;
    color: var(--foreground);
    font-weight: var(--weight-strong);
  }
  .note {
    color: var(--muted-foreground);
  }
</style>
