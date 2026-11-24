<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight } from '@lucide/svelte';
  import { Button } from '$lib/ui/button';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import type { Line } from '$lib/features/network/lines.svelte';
  import { clock } from '$lib/features/timetable/clock.svelte';
  import { createLineTimetable } from '$lib/features/timetable/line-timetable.svelte';
  import { minutesOf } from '$lib/features/timetable/time';
  import { typeNoun } from './line-style';

  /** Place card for a line on the map: the same frame as the stop card on /stops. */
  let { line }: { line: Line } = $props();

  const timetable = createLineTimetable(() => line.apiId);
  $effect(() => clock.subscribe());

  /** The next three departures from either terminus. */
  const next = $derived(
    (timetable.trips ?? [])
      .map((trip) => ({ trip, at: minutesOf(trip.departure_time, trip.departure_day_offset) }))
      .filter(({ at }) => at >= clock.now)
      .sort((a, b) => a.at - b.at)
      .slice(0, 3)
  );
</script>

<article class="line-summary" aria-label={`Vybraná linka ${line.number}`}>
  <div class="head">
    <LineBadge id={line.number} size="lg" />
    <div>
      <h3>{line.name}</h3>
      <p class="facts">{typeNoun(line.type)} · {line.stops.length} zastávek</p>
    </div>
  </div>
  {#if next.length}
    <ul class="next" aria-label="Nejbližší odjezdy">
      {#each next as { trip } (trip.id)}
        <li>
          <a href={resolve('/(map)/trips/[id]', { id: String(trip.id) })}>
            <span class="time tabular">{trip.departure_time}</span>
            <ArrowRight size={14} aria-hidden="true" />
            <span class="dest">{trip.destination.name}</span>
          </a>
        </li>
      {/each}
    </ul>
  {/if}
  <Button href={resolve('/(map)/lines/[id]', { id: String(line.apiId) })} class="self-start">
    Detail linky <ArrowRight data-icon="inline-end" aria-hidden="true" />
  </Button>
</article>

<style>
  .line-summary {
    display: grid;
    gap: 0.75rem;
    padding: 0.875rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--card);
  }
  .head {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }
  h3 {
    font-size: var(--fs-lg);
    font-weight: var(--font-display-weight);
    line-height: 1.2;
  }
  .facts {
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
  }
  .next {
    display: grid;
    gap: 0.25rem;
  }
  .next a {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-height: 2.25rem;
    padding: 0 0.5rem;
    border-radius: var(--radius);
    background: var(--muted);
  }
  .next a:hover {
    background: var(--accent);
  }
  .time {
    font-weight: var(--font-display-weight);
  }
  .dest {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
