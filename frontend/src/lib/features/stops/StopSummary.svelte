<script lang="ts">
  import { resolve } from '$app/paths';
  import { ArrowRight, Check, X } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import { Button } from '$lib/ui/button';
  import { describeStop, linesOf, neighboursOf } from '$lib/features/network/lines';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import StopImage from './StopImage.svelte';
  import { rememberStop } from './selected-stop';
  import { AMENITIES } from './amenities';

  let {
    stop,
    compact = false
  }: { stop: Stop; /** Place-card use: hides the neighbours line. */ compact?: boolean } = $props();
  const neighbours = $derived(neighboursOf(stop.name));
  const titleTransition = $derived(`stop-title-${String(stop.id).replace(/[^a-zA-Z0-9_-]/g, '-')}`);
</script>

{#key stop.id}
  <article class="stop-summary" aria-label={`Vybraná zastávka ${stop.name}`}>
    <StopImage src={stop.image_url} name={stop.name} id={stop.id} eager class="summary-image" />
    <div class="summary-body">
      <div>
        <h3 style:view-transition-name={titleTransition}>{stop.name}</h3>
        <p class="sub">
          {#each linesOf(stop.name) as id (id)}<LineBadge {id} />{/each}
          <span>{describeStop(stop.name)}</span>
        </p>
      </div>
      <dl>
        {#each AMENITIES as amenity (amenity.field)}
          {@const has = stop[amenity.field]}
          <div>
            <dt><amenity.icon size={16} strokeWidth={1.75} aria-hidden="true" />{amenity.label}</dt>
            <dd class:no={!has}>
              {#if has}
                <Check aria-hidden="true" size={14} strokeWidth={2.5} /> ano
              {:else}
                <X aria-hidden="true" size={14} strokeWidth={2.5} /> ne
              {/if}
            </dd>
          </div>
        {/each}
      </dl>
      {#if neighbours.length && !compact}
        <p class="sub">Sousední: {neighbours.join(', ')}</p>
      {/if}
      <Button
        href={resolve('/stops/[id]', { id: String(stop.id) })}
        onclick={() => rememberStop(stop)}
        class="self-start"
      >
        Podrobnosti zastávky <ArrowRight data-icon="inline-end" aria-hidden="true" />
      </Button>
    </div>
  </article>
{/key}

<style>
  .stop-summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 17rem), 1fr));
    gap: 1.25rem;
    animation: swap var(--motion-normal) var(--ease-out) both;
  }
  @keyframes swap {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
  .stop-summary :global(.summary-image) {
    aspect-ratio: auto;
    min-height: 12rem;
    height: 100%;
    border-radius: var(--radius-lg);
  }
  .summary-body {
    display: flex;
    flex-direction: column;
    gap: 0.875rem;
    min-width: 0;
  }
  h3 {
    font-size: 1.5rem;
    font-weight: var(--font-display-weight, 650);
    letter-spacing: var(--tracking-display, -0.02em);
    line-height: 1.1;
    overflow-wrap: anywhere;
  }
  .sub {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.375rem;
    margin-top: 0.375rem;
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  dl > div {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.75rem;
    min-height: 2.5rem;
    border-top: 1px solid var(--border);
  }
  dl > div:last-child {
    border-bottom: 1px solid var(--border);
  }
  dt {
    display: flex;
    align-items: center;
    gap: 0.625rem;
  }
  dt :global(svg) {
    flex-shrink: 0;
    color: var(--muted-foreground);
  }
  dd {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    font-weight: 600;
    color: var(--status-ok, var(--foreground));
  }
  dd.no {
    color: var(--status-bad, var(--muted-foreground));
  }
  @media (prefers-reduced-motion: reduce) {
    .stop-summary {
      animation: none;
    }
  }
</style>
