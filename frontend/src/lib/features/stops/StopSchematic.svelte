<script lang="ts">
  import { LINES, loadNetwork, networkReady } from '$lib/features/network/lines.svelte';
  import { isPale } from '$lib/features/lines/line-style';
  import { lineDirectionHref } from '$lib/features/timetable/trips';
  import { ChevronRight } from '@lucide/svelte';
  import LoadingState from '$lib/components/LoadingState.svelte';

  /**
   * The lines through one stop as a schematic: each direction drawn as a short strip in the
   * line colour (previous stop, this stop, next stop). No map tiles, so it never renders empty.
   */
  let { stopId }: { stopId: number } = $props();

  $effect(() => {
    void loadNetwork();
  });

  type Strip = {
    route: (typeof LINES)[string]['routes'][number];
    previous: { id: number; name: string } | null;
    here: { id: number; name: string };
    next: { id: number; name: string } | null;
  };

  // One card per line (like a PID stop panel), with a row per direction that calls here.
  const groups = $derived(
    Object.values(LINES)
      .map((line) => ({
        line,
        strips: line.routes
          .flatMap((route): Strip[] => {
            const index = route.stops.findIndex((stop) => stop.id === stopId);
            if (index < 0) return [];
            return [
              {
                route,
                previous: route.stops[index - 1] ?? null,
                here: route.stops[index],
                next: route.stops[index + 1] ?? null
              }
            ];
          })
          // Boardable directions first; a direction that ends here is only for getting off.
          .sort((a, b) => Number(!a.next) - Number(!b.next))
      }))
      .filter((group) => group.strips.length > 0)
  );
</script>

<section class="schematic" aria-labelledby="schematic-heading">
  <h2 id="schematic-heading" class="section-title">Linky na zastávce</h2>
  {#if !networkReady()}
    <LoadingState label="Načítáme linky…" />
  {:else if groups.length === 0}
    <p class="note">Tudy zatím žádná linka nejezdí.</p>
  {:else}
    <ul>
      {#each groups as { line, strips } (line.number)}
        <li
          class:line-cased={isPale(line.color)}
          style:--line={line.color}
          style:--line-ink={line.ink}
        >
          <span class="plate tabular" aria-hidden="true">{line.number}</span>
          <div class="what">
            <span class="sr-only">Linka {line.number}</span>
            <ul class="dirs">
              {#each strips as { route, previous, here, next } (route.id)}
                <li class:ends={!next}>
                  <a class="row-link" href={lineDirectionHref(line.apiId, route.bound, stopId)}>
                    <span class="dir">
                      {#if next}
                        <span class="dir-name"
                          ><span aria-hidden="true">→ </span><strong>{route.direction}</strong
                          ></span
                        >
                        <span class="hops">
                          {#if previous}{previous.name} ·{:else}začíná tady ·{/if}
                          <strong class="here">{here.name}</strong>
                          · {next.name} …
                        </span>
                      {:else}
                        <span class="dir-name"
                          ><span aria-hidden="true">⏹ </span>Tady končí (z {route.stops[0]
                            ?.name})</span
                        >
                        <span class="hops">jen výstup</span>
                      {/if}
                    </span>
                    <span class="sr-only">, jízdní řád</span>
                    <ChevronRight class="chev" aria-hidden="true" size={20} />
                  </a>
                </li>
              {/each}
            </ul>
          </div>
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
    display: flex;
    gap: 0.875rem;
    align-items: flex-start;
    padding: 0.75rem 1rem;
    border-left: 6px solid var(--line);
    border-radius: var(--radius-md);
    background: color-mix(in oklch, var(--line) 10%, var(--background));
  }
  .dirs {
    gap: 0;
    margin-top: 0.25rem;
  }
  .dirs li {
    display: block;
    padding: 0;
    border: 0;
    border-top: 1px solid color-mix(in oklch, var(--line) 25%, var(--border));
    border-radius: 0;
    background: none;
  }
  .row-link {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.5rem 0;
  }
  .row-link:hover .dir-name {
    text-decoration: underline;
  }
  .dir {
    display: grid;
    gap: 0.125rem;
    min-width: 0;
  }
  .ends .dir-name {
    color: var(--muted-foreground);
  }
  .row-link :global(.chev) {
    margin-left: auto;
    flex-shrink: 0;
    color: var(--muted-foreground);
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .row-link:hover :global(.chev) {
    color: var(--foreground);
    transform: translateX(2px);
  }
  .here {
    color: var(--foreground);
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
    flex: 1;
    min-width: 0;
  }
  .hops {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-regular);
  }
  .note {
    color: var(--muted-foreground);
  }
</style>
