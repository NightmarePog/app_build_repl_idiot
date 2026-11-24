<script lang="ts">
  import { ArrowRight, Route } from '@lucide/svelte';
  import { listStopDepartures, type Stop } from '$lib/api/generated';
  import { createResource, unwrap } from '$lib/api/resource.svelte';
  import type { ResolvedPathname } from '$app/types';
  import { Button } from '$lib/ui/button';
  import { plannerHref } from '$lib/features/planner/planner';
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import { clock } from '$lib/features/timetable/clock.svelte';
  import { minutesOf, minutesUntil, relative } from '$lib/features/timetable/time';
  import { AMENITIES } from './amenities';
  import StopImage from './StopImage.svelte';

  /**
   * The short version of a stop's page, opened in place in the map's stop list: the next few
   * departures, its amenities, and the way on to a route search or the full detail.
   */
  let { stop, detailHref }: { stop: Stop; detailHref: ResolvedPathname } = $props();

  const SHOWN = 3;
  const board = createResource(
    (signal) => unwrap(listStopDepartures({ path: { stopId: stop.id }, signal })),
    () => stop.id
  );
  $effect(() => clock.subscribe());

  /** The next departures from now; after the last one of the day, the first ones tomorrow. */
  const next = $derived.by(() => {
    const rows = (board.data ?? []).map((row) => ({
      row,
      until: minutesUntil(minutesOf(row.departure_time, row.departure_day_offset), clock.now)
    }));
    return rows
      .filter((entry) => entry.until !== null)
      .sort((a, b) => (a.until ?? 0) - (b.until ?? 0))
      .slice(0, SHOWN);
  });
</script>

<article class="peek" aria-label={`Vybraná zastávka ${stop.name}`}>
  <StopImage src={stop.image_url} name={stop.name} class="peek-photo" />
  <section aria-label={`Nejbližší odjezdy ze zastávky ${stop.name}`}>
    {#if board.error}
      <p class="note">Odjezdy se nepodařilo načíst.</p>
    {:else if !board.data}
      <p class="note">Načítáme odjezdy…</p>
    {:else if next.length === 0}
      <p class="note">Odsud teď nic nejede.</p>
    {:else}
      <div class="departures" role="group" aria-label="Nejbližší odjezdy">
        {#each next as { row, until } (row.trip_id)}
          <p class="departure">
            <LinePlate number={row.line.number} color={row.line.color} size="xs" label />
            <span class="where">→ {row.destination.name}</span>
            <span class="when">
              <strong class="tabular">{row.departure_time}</strong>
              {#if until !== null && until <= 60}<small>{relative(until)}</small>{/if}
            </span>
          </p>
        {/each}
      </div>
    {/if}
  </section>

  <div class="amenities" role="group" aria-label="Vybavení">
    {#each AMENITIES as amenity (amenity.value)}
      {@const has = stop[amenity.field]}
      <span class="amenity" class:missing={!has} title={`${amenity.label}: ${has ? 'ano' : 'ne'}`}>
        <amenity.icon size={16} aria-hidden="true" />
        <span class="sr-only">{amenity.label}: {has ? 'ano' : 'ne'}</span>
      </span>
    {/each}
  </div>

  <div class="actions">
    <Button href={plannerHref({ to: stop.id })} size="sm" title="Spojení sem">
      <Route aria-hidden="true" /> Trasa
    </Button>
    <Button href={plannerHref({ from: stop.id })} variant="outline" size="sm" title="Spojení odsud">
      Odsud
    </Button>
    <Button href={detailHref} variant="outline" size="sm">
      Více <ArrowRight aria-hidden="true" />
    </Button>
  </div>
</article>

<style>
  .peek {
    display: grid;
    gap: 0.625rem;
    padding: 0.25rem 0.75rem 0.875rem 4.75rem;
  }
  .peek :global(.peek-photo) {
    max-height: 9rem;
    border-radius: var(--radius);
  }
  .note {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .departures {
    display: grid;
    gap: 0.375rem;
  }
  .departure {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.5rem;
    font-size: var(--fs-sm);
  }
  .where {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .when {
    display: flex;
    align-items: baseline;
    gap: 0.375rem;
  }
  .when small {
    color: var(--muted-foreground);
    font-size: var(--fs-xs);
  }
  .amenities {
    display: flex;
    gap: 0.375rem;
  }
  .amenity {
    display: grid;
    place-items: center;
    width: 1.75rem;
    height: 1.75rem;
    border-radius: var(--radius-sm);
    background: color-mix(in oklch, var(--success, oklch(0.6 0.15 150)) 18%, transparent);
    color: var(--foreground);
  }
  .amenity.missing {
    background: var(--muted);
    color: var(--muted-foreground);
    opacity: 0.55;
    text-decoration: line-through;
  }
  .actions {
    display: flex;
    gap: 0.5rem;
  }
</style>
