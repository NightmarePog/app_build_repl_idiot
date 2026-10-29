<script lang="ts">
  import { resolve } from '$app/paths';
  import { Check, Minus } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import { describeStop, linesOf } from '$lib/features/network/lines';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { rememberStop } from './selected-stop';
  import StopImage from './StopImage.svelte';
  import { AMENITIES } from './amenities';

  let {
    stop,
    eager = false,
    search = ''
  }: { stop: Stop; eager?: boolean; search?: string } = $props();

  const titleTransition = $derived(`stop-title-${String(stop.id).replace(/[^a-zA-Z0-9_-]/g, '-')}`);
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -- The route is already resolved; preserve the query verbatim after it. -->
<a
  href={`${resolve('/stops/[id]', { id: String(stop.id) })}${search}`}
  aria-label={`Detail zastávky ${stop.name}`}
  class="stop-row"
  onclick={() => rememberStop(stop)}
>
  <StopImage
    src={stop.image_url}
    name={stop.name}
    id={stop.id}
    transition
    {eager}
    class="stop-row-image [&_p]:min-w-0 [&_p]:px-1 [&_p]:text-[0.625rem] [&_p]:leading-tight"
  />
  <span class="stop-row-body">
    <h2 style:view-transition-name={titleTransition}>{stop.name}</h2>
    <span class="stop-row-sub">
      {#each linesOf(stop.name) as id (id)}<LineBadge {id} />{/each}
      <span>{describeStop(stop.name)}</span>
    </span>
  </span>
  <span class="stop-row-amenities">
    {#each AMENITIES as amenity (amenity.field)}
      {@const has = stop[amenity.field]}
      <span class="stop-row-amenity" class:no={!has} title={amenity.label}>
        <amenity.icon aria-hidden="true" size={16} strokeWidth={1.75} />
        {#if has}
          <Check aria-hidden="true" size={12} strokeWidth={2.5} class="mark" />
        {:else}
          <Minus aria-hidden="true" size={12} strokeWidth={2.5} class="mark" />
        {/if}
        <span class="sr-only">{amenity.label}: {has ? 'ano' : 'ne'}</span>
      </span>
    {/each}
  </span>
</a>

<!-- eslint-enable svelte/no-navigation-without-resolve -->

<style>
  .stop-row {
    display: grid;
    grid-template-columns: 4.5rem minmax(0, 1fr) auto;
    align-items: center;
    gap: 1rem;
    min-width: 0;
    min-height: 4rem;
    padding: 0.5rem 0.5rem;
    border-bottom: 1px solid var(--border);
    color: var(--foreground);
    transition: background-color var(--motion-fast) var(--ease-out);
  }
  .stop-row:hover,
  .stop-row:focus-visible {
    background: var(--muted);
  }
  .stop-row :global(.stop-row-image) {
    width: 4.5rem;
    border-radius: var(--radius-sm, 0.25rem);
  }
  .stop-row-body {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    min-width: 0;
  }
  h2 {
    font-size: 1rem;
    font-weight: 600;
    line-height: 1.25;
    overflow-wrap: anywhere;
  }
  .stop-row-sub {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 0.375rem;
    font-size: 0.8125rem;
    color: var(--muted-foreground);
  }
  .stop-row-amenities {
    display: flex;
    gap: 0.25rem;
  }
  .stop-row-amenity {
    display: inline-flex;
    width: 2.75rem;
    align-items: center;
    justify-content: center;
    gap: 0.125rem;
    color: var(--foreground);
  }
  .stop-row-amenity :global(.mark) {
    color: var(--status-ok, var(--primary));
  }
  .stop-row-amenity.no {
    color: var(--muted-foreground);
    opacity: 0.55;
  }
  .stop-row-amenity.no :global(.mark) {
    color: var(--muted-foreground);
  }
  @media (max-width: 639px) {
    .stop-row {
      grid-template-columns: 3.75rem minmax(0, 1fr);
      gap: 0.75rem;
    }
    .stop-row :global(.stop-row-image) {
      width: 3.75rem;
    }
    .stop-row-amenities {
      grid-column: 2;
    }
    .stop-row-amenity {
      width: auto;
      margin-right: 0.5rem;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .stop-row {
      transition: none;
    }
  }
</style>
