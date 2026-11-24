<script lang="ts">
  import { ArrowLeftRight, ArrowRight, History, MapPin, X } from '@lucide/svelte';
  import { stopsList } from '$lib/features/network/stops-store.svelte';
  import { plannerHref } from './planner';
  import { forgetSearch, recentSearches } from './recent.svelte';

  const names = $derived(new Map((stopsList() ?? []).map((s) => [s.id, s.name])));
  /** Only searches whose stops still exist; stops not loaded yet hide the list briefly. */
  const recents = $derived(recentSearches().filter((r) => names.has(r.from) && names.has(r.to)));
</script>

{#if recents.length}
  <section class="recent" aria-labelledby="recent-title">
    <h2 id="recent-title" class="label"><History aria-hidden="true" /> Nedávná hledání</h2>
    <ul>
      {#each recents as r (`${r.from}-${r.to}`)}
        <li>
          <a class="go" href={plannerHref({ from: r.from, to: r.to })}>
            <span class="stops">
              <span>{names.get(r.from)}</span>
              <ArrowRight aria-label="do" class="arrow" />
              <span>{names.get(r.to)}</span>
            </span>
          </a>
          <a
            class="icon"
            href={plannerHref({ from: r.to, to: r.from })}
            title="Zpáteční cesta"
            aria-label={`Zpět: ${names.get(r.to)} do ${names.get(r.from)}`}
            ><ArrowLeftRight aria-hidden="true" /></a
          >
          <button
            type="button"
            class="icon"
            title="Odebrat"
            aria-label={`Odebrat ${names.get(r.from)} – ${names.get(r.to)}`}
            onclick={() => forgetSearch(r.from, r.to)}><X aria-hidden="true" /></button
          >
        </li>
      {/each}
    </ul>
  </section>
{/if}

<div class="hint">
  <MapPin aria-hidden="true" />
  <p>
    {recents.length
      ? 'Nebo zadejte novou cestu výše.'
      : 'Zadejte, odkud a kam jedete. Najdeme přímé spoje i cesty s přestupem.'}
  </p>
</div>

<style>
  .recent {
    display: grid;
    gap: 0.5rem;
  }
  .label {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
  .label :global(svg) {
    width: 1rem;
    height: 1rem;
  }
  ul {
    display: grid;
    gap: 0.375rem;
    margin: 0;
    padding: 0;
    list-style: none;
  }
  li {
    display: flex;
    align-items: stretch;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
  }
  .go {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    min-height: 2.75rem;
    padding: 0.5rem 0.75rem;
    font-weight: var(--weight-strong);
  }
  .stops {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.5rem;
  }
  .stops :global(.arrow) {
    width: 1rem;
    height: 1rem;
    color: var(--muted-foreground);
  }
  .icon {
    display: grid;
    place-items: center;
    width: 2.75rem;
    border-left: 1px solid var(--border);
    color: var(--muted-foreground);
    background: none;
    cursor: pointer;
  }
  .icon :global(svg) {
    width: 1rem;
    height: 1rem;
  }
  .go:hover,
  .icon:hover {
    background: var(--muted);
    color: var(--foreground);
  }
  .hint {
    display: flex;
    gap: 0.75rem;
    align-items: flex-start;
    padding: 1rem;
    border: 1px dashed var(--border);
    border-radius: var(--radius);
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
  }
  .hint :global(svg) {
    flex: none;
    width: 1.25rem;
    height: 1.25rem;
  }
</style>
