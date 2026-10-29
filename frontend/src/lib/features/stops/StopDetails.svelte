<script lang="ts">
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  import { Check, X } from '@lucide/svelte';
  import type { Stop } from '$lib/api/generated';
  import NetworkView from '$lib/features/network/NetworkView.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { describeStop, LINES, linesOf, POSITIONS } from '$lib/features/network/lines';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import StopImage from './StopImage.svelte';
  import { AMENITIES } from './amenities';
  import { rememberStop } from './selected-stop';

  let { stop }: { stop: Stop } = $props();

  const titleTransition = $derived(`stop-title-${String(stop.id).replace(/[^a-zA-Z0-9_-]/g, '-')}`);
  const mapStops = $derived(stopsList() ?? [stop]);
  const onMap = $derived(!!POSITIONS[stop.name]);

  /** Per line: previous stop, this stop, next stop, in route order. */
  const strips = $derived(
    linesOf(stop.name).map((id) => {
      const route = LINES[id].stops;
      const index = route.indexOf(stop.name);
      const names = [route[index - 1], stop.name, route[index + 1]].filter(
        (name): name is string => !!name
      );
      return {
        id,
        color: LINES[id].color,
        towards: route[route.length - 1],
        stops: names.map((name) => ({
          name,
          current: name === stop.name,
          stop: stopsList()?.find((candidate) => candidate.name === name) ?? null
        }))
      };
    })
  );

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

    <div class="detail-side">
      {#if onMap}
        <NetworkView stops={mapStops} selected={stop.id} focus />
      {/if}
      {#if strips.length}
        <section aria-labelledby="neighbours-title" class="neighbours">
          <h2 id="neighbours-title">Sousední zastávky</h2>
          {#each strips as strip (strip.id)}
            <div class="strip" style:--lc={strip.color}>
              <p class="strip-head">
                <LineBadge id={strip.id} />
                <span>směr {strip.towards}</span>
              </p>
              <ol>
                {#each strip.stops as item (item.name)}
                  <li class:current={item.current}>
                    {#if item.current}
                      <span aria-current="location">{item.name}</span>
                    {:else if item.stop}
                      {@const target = item.stop}
                      <a
                        href={resolve('/stops/[id]', { id: String(target.id) })}
                        onclick={() => rememberStop(target)}>{item.name}</a
                      >
                    {:else}
                      <span>{item.name}</span>
                    {/if}
                  </li>
                {/each}
              </ol>
            </div>
          {/each}
        </section>
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
  .strip + .strip {
    margin-top: 1.25rem;
  }
  .strip-head {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.25rem;
    font-size: 0.875rem;
    color: var(--muted-foreground);
  }
  ol {
    position: relative;
    margin-left: 0.6875rem;
  }
  ol::before {
    content: '';
    position: absolute;
    top: 1.375rem;
    bottom: 1.375rem;
    left: -2px;
    width: 4px;
    border-radius: 2px;
    background: var(--lc);
  }
  li {
    position: relative;
    display: flex;
    min-height: 2.75rem;
    align-items: center;
    padding-left: 1.25rem;
  }
  li::before {
    content: '';
    position: absolute;
    top: 50%;
    left: -6px;
    width: 12px;
    height: 12px;
    margin-top: -6px;
    border: 3px solid var(--lc);
    border-radius: 50%;
    background: var(--background);
  }
  li.current {
    font-weight: 650;
  }
  li.current::before {
    left: -8px;
    width: 16px;
    height: 16px;
    margin-top: -8px;
    background: var(--lc);
    box-shadow: 0 0 0 3px var(--background);
  }
  li a {
    display: flex;
    min-height: 2.75rem;
    flex: 1;
    align-items: center;
    color: var(--foreground);
    transition: color var(--motion-fast) var(--ease-out);
  }
  li a:hover {
    color: var(--primary);
  }
  @media (max-width: 1023px) {
    .detail-layout {
      grid-template-columns: minmax(0, 1fr);
      gap: 2.5rem;
    }
  }
</style>
