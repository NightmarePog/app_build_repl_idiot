<script lang="ts">
  import { Check, MapPin, X } from '@lucide/svelte';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import type { Stop } from '$lib/api/generated';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { linesOf } from '$lib/features/network/lines.svelte';
  import StopImage from './StopImage.svelte';
  import StopSchematic from './StopSchematic.svelte';
  import DepartureBoard from '$lib/features/timetable/DepartureBoard.svelte';
  import { AMENITIES } from './amenities';

  let { stop }: { stop: Stop } = $props();
</script>

<article class="stop-detail">
  <div class="detail-head">
    <PageHeader kind="Zastávka" icon={MapPin} title={stop.name}>
      {#snippet meta()}
        <span>Obsluhují linky</span>
        {#each linesOf(stop.name) as id (id)}<LineBadge {id} size="md" />{/each}
      {/snippet}
    </PageHeader>
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
  </div>

  <!-- The board answers the page's main question (when does the next bus leave), so it comes
       first; the line chips on it replace the old per-line list. -->
  <DepartureBoard stopId={stop.id} stopName={stop.name} />

  <div class="detail-layout">
    <div class="detail-main">
      <section class="detail-amenities" aria-labelledby="amenities-title">
        <h2 id="amenities-title" class="section-title">Vybavení zastávky</h2>
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
                  {#if amenity.value === 'tickets'}<span class="hint">kupte u řidiče</span>{/if}
                {/if}
              </dd>
            </div>
          {/each}
        </dl>
      </section>
    </div>

    <div class="detail-side">
      <StopSchematic stopId={stop.id} />
    </div>
  </div>
</article>

<style>
  .detail-head {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 22rem);
    align-items: center;
    gap: 1.5rem;
    margin-bottom: 1.5rem;
  }
  .detail-head :global(.page-header) {
    margin-bottom: 0;
  }
  .detail-image {
    margin: 0;
  }
  .detail-image :global(.detail-image-frame) {
    height: 13.75rem;
    width: 100%;
    border-radius: var(--radius-lg);
  }
  .detail-layout {
    margin-top: 2.5rem;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
    gap: clamp(2rem, 4vw, 4rem);
  }
  .hint {
    margin-left: 0.25rem;
    padding-left: 0.625rem;
    border-left: 1px solid var(--border);
    color: var(--muted-foreground);
    font-weight: var(--weight-regular);
  }
  /* Room for the fixed mobile tab bar, so the last section never hides under it. */
  .stop-detail {
    padding-bottom: calc(var(--tabbar-h, 0px) + env(safe-area-inset-bottom) + 1rem);
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
    font-weight: var(--weight-regular);
  }
  dt :global(svg) {
    flex-shrink: 0;
    color: var(--muted-foreground);
  }
  dd {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    font-weight: var(--weight-strong);
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
    .detail-head {
      grid-template-columns: minmax(0, 1fr);
      gap: 1rem;
    }
    .detail-image :global(.detail-image-frame) {
      height: 9rem;
    }
  }
</style>
