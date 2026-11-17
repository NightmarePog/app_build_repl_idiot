<script lang="ts">
  import { onMount } from 'svelte';
  import { Check, X } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import NetworkView from '$lib/features/network/NetworkView.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { describeStop, linesOf, POSITIONS } from '$lib/features/network/lines.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import StopImage from './StopImage.svelte';
  import StopLines from './StopLines.svelte';
  import { AMENITIES } from './amenities';

  let { stop }: { stop: Stop } = $props();

  const titleTransition = $derived(`stop-title-${String(stop.id).replace(/[^a-zA-Z0-9_-]/g, '-')}`);
  const mapStops = $derived(stopsList() ?? [stop]);
  const onMap = $derived(!!POSITIONS[stop.name]);

  onMount(() => void loadStops());
</script>

<article class="stop-detail">
  <figure class="detail-image">
    <StopImage
      src={stop.image_url}
      name={stop.name}
      id={stop.id}
      transition
      eager
      class="detail-image-frame"
    />
  </figure>

  <header class="detail-heading">
    <h1 style:view-transition-name={titleTransition}>{stop.name}</h1>
    <p class="detail-sub">
      {#each linesOf(stop.name) as id (id)}<LineBadge {id} size="md" />{/each}
      <span>{describeStop(stop.name)}</span>
    </p>
  </header>

  <div class="detail-layout">
    <div class="detail-main">
      <section class="detail-amenities" aria-labelledby="amenities-title">
        <h2 id="amenities-title">Vybavení zastávky</h2>
        <dl>
          {#each AMENITIES as amenity (amenity.field)}
            {@const has = stop[amenity.field]}
            <div class="amenity-row">
              <dt>
                <amenity.icon aria-hidden="true" size={20} strokeWidth={1.75} />
                {amenity.label}
              </dt>
              <dd class:no={!has}>
                {#if has}
                  <Check aria-hidden="true" size={16} strokeWidth={2.5} /> ano
                {:else}
                  <X aria-hidden="true" size={16} strokeWidth={2.5} /> ne
                {/if}
              </dd>
            </div>
          {/each}
        </dl>
      </section>
      <StopLines stopId={stop.id} />
    </div>

    <div class="detail-side">
      {#if onMap}
        <!-- Decorative: the camera circles the stop; the page itself carries the information. -->
        <NetworkView stops={mapStops} selected={stop.id} focus interactive={false} />
      {/if}
    </div>
  </div>
</article>

<style>
  .detail-image {
    margin: 1rem 0 0;
  }
  .detail-image :global(.detail-image-frame) {
    aspect-ratio: 21 / 9;
    min-height: 14rem;
    border-radius: var(--radius-lg);
  }
  .detail-heading {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.5rem 1.5rem;
    margin: 1.5rem 0 2rem;
  }
  h1 {
    font-size: clamp(2rem, 4.5vw, 3.25rem);
    font-weight: var(--font-display-weight, 650);
    line-height: 1.05;
    letter-spacing: var(--tracking-display, -0.02em);
    overflow-wrap: anywhere;
  }
  .detail-sub {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--muted-foreground);
  }
  .detail-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
    gap: clamp(2rem, 4vw, 4rem);
  }
  h2 {
    font-size: 1rem;
    font-weight: 600;
    margin-bottom: 0.5rem;
  }
  .amenity-row {
    display: flex;
    min-height: 3.25rem;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    border-top: 1px solid var(--border);
  }
  .amenity-row:last-child {
    border-bottom: 1px solid var(--border);
  }
  dt {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 0.75rem;
    font-weight: 500;
  }
  dt :global(svg) {
    flex-shrink: 0;
    color: var(--muted-foreground);
  }
  dd {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-weight: 600;
    color: var(--status-ok, var(--foreground));
  }
  dd.no {
    color: var(--status-bad, var(--muted-foreground));
  }
  .detail-side {
    display: flex;
    flex-direction: column;
    gap: 2rem;
    min-width: 0;
  }
  @media (max-width: 1023px) {
    .detail-layout {
      grid-template-columns: minmax(0, 1fr);
      gap: 2.5rem;
    }
  }
  @media (max-width: 767px) {
    .detail-image {
      margin: -2rem -1.25rem 0;
    }
    .detail-image :global(.detail-image-frame) {
      aspect-ratio: 4 / 3;
      min-height: 0;
      border-radius: 0;
    }
    .detail-heading {
      margin-top: 1.25rem;
    }
  }
</style>
