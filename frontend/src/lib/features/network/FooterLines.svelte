<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight } from '@lucide/svelte';
  import { LINES, LINE_IDS } from './lines.svelte';

  /** List items for the footer's "Linky" column: badge plus first and last stop. */
  const lines = $derived(
    LINE_IDS.map((id) => {
      const { stops, color, ink } = LINES[id];
      return { id, color, ink, from: stops[0], to: stops[stops.length - 1] };
    })
  );
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -- The route is resolved; only a query is appended. -->
{#each lines as line (line.id)}
  <li>
    <a href={`${resolve('/stops')}?line=${line.id}`} class="line-link">
      <span class="badge" style:background={line.color} style:color={line.ink} aria-hidden="true"
        >{line.id}</span
      >
      <span class="sr-only">Linka {line.id}:</span>
      <span class="route"
        >{line.from}
        <ArrowRight size={13} aria-hidden="true" />
        <span class="sr-only">do</span>
        {line.to}</span
      >
    </a>
  </li>
{/each}

<!-- eslint-enable svelte/no-navigation-without-resolve -->

<style>
  .line-link {
    gap: 0.5rem;
  }
  .badge {
    display: inline-grid;
    place-items: center;
    width: 1.25rem;
    height: 1.25rem;
    flex-shrink: 0;
    border-radius: 0.25rem;
    font-size: 0.6875rem;
    font-weight: 800;
    line-height: 1;
  }
  .route {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem;
  }
</style>
