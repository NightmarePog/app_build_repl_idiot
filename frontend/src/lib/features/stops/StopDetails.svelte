<script lang="ts">
  import { resolve } from '$app/paths';
  import { MapPin, Route } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import { plannerHref } from '$lib/features/planner/planner';
  import type { Stop } from '$lib/api/generated';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { goto } from '$app/navigation';
  import { LINES, POSITIONS, linesOf, neighboursOf } from '$lib/features/network/lines.svelte';
  import { focusMap } from '$lib/map-focus.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import StopImage from './StopImage.svelte';
  import DepartureBoard from '$lib/features/timetable/DepartureBoard.svelte';
  import { AMENITIES } from './amenities';
  import PageHeader from '$lib/components/PageHeader.svelte';

  let { stop }: { stop: Stop } = $props();

  $effect(() => void loadStops());
  const near = $derived([stop.name, ...neighboursOf(stop.name)]);
  const mapStops = $derived(stopsList() ?? [stop]);

  // The map page's big map shows the stop and its neighbourhood.
  $effect(() =>
    focusMap({
      stops: mapStops,
      selected: stop.id,
      focusLines: linesOf(stop.name),
      focusStops: mapStops.filter((s) => near.includes(s.name)).map((s) => s.id),
      fit: near.map((name) => POSITIONS[name]).filter(Boolean),
      onselect: (other) =>
        other.id !== stop.id && goto(resolve('/(map)/stops/[id]', { id: String(other.id) }))
    })
  );
</script>

<!-- Wikipedia-style: the board is the article, the stop facts sit in a sticky infobox. -->
<PageHeader kind="Zastávka" icon={MapPin} title={stop.name} />

<article class="stop-detail">
  <aside class="infobox" aria-label={`Zastávka ${stop.name}`}>
    <div class="photo">
      <StopImage
        src={stop.image_url}
        name={stop.name}
        id={stop.id}
        transition
        eager
        class="info-photo"
      />
    </div>
    <div class="info-body">
      <h2 class="info-heading">Linky</h2>
      <ul class="lines">
        {#each linesOf(stop.name) as id (id)}
          {@const line = LINES[id]}
          <li>
            {#if line}
              <a class="line" href={resolve('/(map)/lines/[id]', { id: String(line.apiId) })}>
                <LineBadge {id} size="lg" />
                {#if line.stops.length > 1}
                  <span class="terminals"
                    >{line.stops[0]} – {line.stops[line.stops.length - 1]}</span
                  >
                {/if}
              </a>
            {:else}<LineBadge {id} size="lg" />{/if}
          </li>
        {/each}
      </ul>
      <h2 class="info-heading">Vybavení</h2>
      <ul class="amenities">
        {#each AMENITIES as amenity (amenity.field)}
          {@const has = stop[amenity.field]}
          <li
            class:no={!has}
            title={`${amenity.label}: ${has ? 'ano' : 'ne'}`}
            aria-label={`${amenity.label}: ${has ? 'ano' : 'ne'}`}
          >
            <span class="icon" aria-hidden="true"
              ><amenity.icon size={20} strokeWidth={1.75} /></span
            >
            <span class="amenity-label" aria-hidden="true">{amenity.label}</span>
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
  .photo {
    position: relative;
  }
  .info-heading {
    font-size: var(--fs-base);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
  .info-heading + ul {
    margin-top: -0.5rem;
  }
  .lines {
    display: grid;
    gap: 0.25rem;
  }
  .line {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    min-height: 2.75rem;
    padding: 0.25rem;
    border-radius: var(--radius);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .line:hover {
    background: var(--muted);
  }
  .line:hover .terminals {
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .line:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  /* A compact icon row: tinted = present, struck grey = missing; the name is the tooltip. */
  .amenities {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
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
  .amenities li.no .icon {
    position: relative;
    background: var(--muted);
    color: var(--muted-foreground);
  }
  /* Not available: a small x badge on the muted icon, like SBB/DB station info. */
  .amenities li.no .icon::after {
    content: '\2715';
    position: absolute;
    right: -0.3rem;
    bottom: -0.3rem;
    display: grid;
    place-items: center;
    width: 1.1rem;
    height: 1.1rem;
    border-radius: 50%;
    background: var(--muted-foreground);
    color: var(--background);
    font-size: 0.65rem;
    font-weight: var(--weight-strong);
    line-height: 1;
  }
  @container (max-width: 1023px) {
    .stop-detail {
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas: 'info' 'main';
      gap: 1.5rem;
    }
    /* Phones and tablets: a compact strip so the departures start high on the screen. */
    /* Phones: a full-width photo strip on top, then lines, amenities and Trasa. */
    .infobox {
      position: static;
      display: grid;
      grid-template-columns: minmax(0, 1fr);
    }
    .photo,
    .infobox :global(.info-photo) {
      height: 8.5rem;
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
