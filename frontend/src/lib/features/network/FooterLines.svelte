<script lang="ts">
  import { withQuery } from '$lib/links';
  import { resolve } from '$app/paths';
  import { ArrowRight } from '@lucide/svelte';
  import { LINES, LINE_IDS } from './lines.svelte';
  import LineBadge from './LineBadge.svelte';

  /** List items for the footer's "Linky" column: badge plus first and last stop. */
  const lines = $derived(
    LINE_IDS.map((id) => {
      const { stops, color, ink } = LINES[id];
      return { id, color, ink, from: stops[0], to: stops[stops.length - 1] };
    })
  );
</script>

{#each lines as line (line.id)}
  <li>
    <a href={withQuery(resolve('/stops'), { line: line.id })} class="line-link">
      <LineBadge id={line.id} />
      <span class="route"
        >{line.from}
        <ArrowRight size={14} aria-hidden="true" />
        <span class="sr-only">do</span>
        {line.to}</span
      >
    </a>
  </li>
{/each}

<style>
  .line-link {
    gap: 0.5rem;
  }
  .route {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem;
  }
</style>
