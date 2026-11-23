<script lang="ts">
  import { withQuery } from '$lib/links';
  import { resolve } from '$app/paths';
  import { LINES, LINE_IDS } from './lines.svelte';
  import LineBadge from './LineBadge.svelte';

  /** List items for the footer's "Linky" column: badge plus the line's official name, the same
   * name the line page shows, so the footer never differs from page to page. */
  const lines = $derived(
    LINE_IDS.flatMap((id) => (LINES[id] ? [{ id, name: LINES[id].name }] : []))
  );
</script>

{#each lines as line (line.id)}
  <li>
    <a href={withQuery(resolve('/stops'), { line: line.id })} class="line-link">
      <LineBadge id={line.id} />
      <span class="route">{line.name}</span>
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
