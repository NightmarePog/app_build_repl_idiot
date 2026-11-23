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
  import PageHeader from '$lib/components/PageHeader.svelte';

  let { stop }: { stop: Stop } = $props();
</script>

<!-- Wikipedia-style: the board is the article, the stop facts sit in a sticky infobox. -->
<PageHeader kind="Zastávka" icon={MapPin} title={stop.name} />

<article class="stop-detail">
  <aside class="infobox" aria-label={`Zastávka ${stop.name}`}>
    <StopImage
      src={stop.image_url}
      name={stop.name}
      id={stop.id}
      transition
      eager
      class="info-photo"
    />
    <div class="info-body">
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
          <li class:no={!has} title={`${amenity.label}: ${has ? 'ano' : 'ne'}`}>
            <span class="icon" aria-hidden="true"
              ><amenity.icon size={20} strokeWidth={1.75} /></span
            >
            <span class="caption"
              ><span class="sr-only">{has ? '' : 'Není: '}</span>{amenity.label}</span
            >
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
  .lines {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .amenities {
    display: grid;
    gap: 0.375rem;
  }
  .amenities li {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .amenities .icon {
    display: grid;
    flex: none;
    place-items: center;
    width: 2.25rem;
    height: 2.25rem;
    border-radius: var(--radius);
    background: color-mix(in oklch, var(--status-ok, var(--foreground)) 14%, transparent);
    color: var(--status-ok, var(--foreground));
  }
  .amenities li.no {
    color: var(--muted-foreground);
  }
  .amenities li.no .caption {
    text-decoration: line-through;
  }
  .amenities li.no .icon {
    background: var(--muted);
    color: var(--muted-foreground);
  }
  .amenities li.no .icon :global(svg) {
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
    /* Phones and tablets: a compact strip so the departures start high on the screen. */
    .infobox {
      position: static;
      display: grid;
      grid-template-columns: 6.5rem minmax(0, 1fr);
    }
    .infobox :global(.info-photo) {
      height: 100%;
      aspect-ratio: auto;
    }
    .info-body {
      gap: 0.625rem;
      padding: 0.75rem;
    }
    .amenities {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
    }
    .amenities .icon {
      width: 1.75rem;
      height: 1.75rem;
    }
  }
</style>
