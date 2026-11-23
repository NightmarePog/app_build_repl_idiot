<script lang="ts">
  import { resolve } from '$app/paths';
  import { MapPin, Route } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import { plannerHref } from '$lib/features/planner/planner';
  import type { Stop } from '$lib/api/generated';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { LINES, linesOf } from '$lib/features/network/lines.svelte';
  import StopImage from './StopImage.svelte';
  import DepartureBoard from '$lib/features/timetable/DepartureBoard.svelte';
  import { AMENITIES } from './amenities';

  let { stop }: { stop: Stop } = $props();
  const titleTransition = $derived(`stop-title-${String(stop.id).replace(/[^a-zA-Z0-9_-]/g, '-')}`);
</script>

<!-- Wikipedia-style: the board is the article, the stop facts sit in a sticky infobox. -->
<article class="stop-detail">
  <aside class="infobox" aria-labelledby="stop-title">
    <StopImage
      src={stop.image_url}
      name={stop.name}
      id={stop.id}
      transition
      eager
      class="info-photo"
    />
    <div class="info-body">
      <p class="kind"><MapPin aria-hidden="true" size={18} /> Zastávka</p>
      <h1 id="stop-title" style:view-transition-name={titleTransition}>{stop.name}</h1>
      <ul class="lines" aria-label="Linky">
        {#each linesOf(stop.name) as id (id)}
          <li>
            {#if LINES[id]}
              <a href={resolve('/lines/[id]', { id: String(LINES[id].apiId) })}
                ><LineBadge {id} size="lg" /></a
              >
            {:else}<LineBadge {id} size="lg" />{/if}
          </li>
        {/each}
      </ul>
      <ul class="amenities" aria-label="Vybavení">
        {#each AMENITIES as amenity (amenity.field)}
          {@const has = stop[amenity.field]}
          <li
            class:no={!has}
            title={`${amenity.label}: ${has ? 'ano' : 'ne'}`}
            aria-label={`${amenity.label}: ${has ? 'ano' : 'ne'}`}
          >
            <amenity.icon aria-hidden="true" size={22} strokeWidth={1.75} />
          </li>
        {/each}
      </ul>
      <Button href={plannerHref({ to: stop.id })} size="lg" class="w-full">
        <Route aria-hidden="true" /> Trasa
      </Button>
    </div>
  </aside>

  <div class="main">
    <DepartureBoard stopId={stop.id} stopName={stop.name} />
  </div>
</article>

<style>
  .stop-detail {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 20rem;
    grid-template-areas: 'main info';
    align-items: start;
    gap: 2rem;
    margin-top: 1rem;
    padding-bottom: calc(var(--tabbar-h, 0px) + env(safe-area-inset-bottom) + 1rem);
  }
  .main {
    grid-area: main;
    min-width: 0;
  }
  .infobox {
    grid-area: info;
    position: sticky;
    top: calc(var(--header-h) + 1rem);
    overflow: hidden;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
  }
  .infobox :global(.info-photo) {
    width: 100%;
    aspect-ratio: 4 / 3;
    border-radius: 0;
  }
  .info-body {
    display: grid;
    gap: 1rem;
    padding: 1rem;
  }
  .kind {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-weight: var(--weight-strong);
  }
  h1 {
    font-size: var(--fs-h2, 1.75rem);
    font-weight: var(--font-display-weight);
    line-height: 1.1;
    overflow-wrap: anywhere;
  }
  .lines,
  .amenities {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .amenities li {
    display: grid;
    place-items: center;
    width: 2.75rem;
    height: 2.75rem;
    border-radius: var(--radius);
    background: color-mix(in oklch, var(--status-ok, var(--foreground)) 14%, transparent);
    color: var(--status-ok, var(--foreground));
  }
  .amenities li.no {
    background: var(--muted);
    color: var(--muted-foreground);
    opacity: 0.55;
  }
  .amenities li.no :global(svg) {
    /* A struck-through icon: the amenity is missing. */
    background: linear-gradient(
      to top right,
      transparent 46%,
      currentColor 46%,
      currentColor 54%,
      transparent 54%
    );
  }
  @media (max-width: 1023px) {
    .stop-detail {
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas: 'info' 'main';
      gap: 1.5rem;
    }
    .infobox {
      position: static;
      display: grid;
      grid-template-columns: 9rem minmax(0, 1fr);
    }
    .infobox :global(.info-photo) {
      height: 100%;
      aspect-ratio: auto;
    }
  }
  @media (max-width: 479px) {
    .infobox {
      grid-template-columns: minmax(0, 1fr);
    }
    .infobox :global(.info-photo) {
      aspect-ratio: 16 / 7;
    }
  }
</style>
