<script lang="ts">
  import { tick, type ComponentProps } from 'svelte';
  import { Maximize2, Minimize2 } from '@lucide/svelte';
  import type NetworkMapType from './NetworkMap.svelte';

  type Props = ComponentProps<typeof NetworkMapType> & {
    /** Embedded on a detail page: short, scroll passes to the page, with an expand button. */
    compact?: boolean;
    /** Accessible name of the expand button's dialog. */
    label?: string;
  };

  let { compact = false, label = 'Mapa', ...rest }: Props = $props();

  // MapLibre and its stylesheet load with the map, so pages without one stay light.
  const loading = import('./NetworkMap.svelte');

  let expanded = $state(false);
  let inner = $state<{ reframe: () => void } | null>(null);
  let toggle = $state<HTMLButtonElement | null>(null);

  async function setExpanded(value: boolean) {
    expanded = value;
    await tick();
    requestAnimationFrame(() => inner?.reframe());
    toggle?.focus();
  }

  function onkeydown(event: KeyboardEvent) {
    if (expanded && event.key === 'Escape') void setExpanded(false);
  }
</script>

<svelte:window {onkeydown} />

<div
  class="transit-map"
  class:compact
  class:expanded
  role={expanded ? 'dialog' : undefined}
  aria-modal={expanded ? 'true' : undefined}
  aria-label={expanded ? label : undefined}
>
  {#await loading}
    <div class="placeholder" aria-hidden="true"></div>
  {:then module}
    <module.default
      bind:this={inner}
      {...rest}
      cooperative={compact && !expanded}
      interactive={rest.interactive ?? true}
    />
  {/await}
  {#if compact}
    <button
      type="button"
      class="expand"
      bind:this={toggle}
      aria-label={expanded ? 'Zmenšit mapu' : 'Zvětšit mapu'}
      aria-pressed={expanded}
      onclick={() => setExpanded(!expanded)}
    >
      {#if expanded}<Minimize2 size={18} aria-hidden="true" />{:else}<Maximize2
          size={18}
          aria-hidden="true"
        />{/if}
    </button>
  {/if}
</div>

<style>
  .transit-map {
    position: relative;
    height: 100%;
  }
  .placeholder {
    aspect-ratio: var(--map-ratio, 600 / 440);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--muted);
  }
  .compact :global(.network-map),
  .compact .placeholder {
    aspect-ratio: var(--map-ratio, 4 / 3);
  }
  .expand {
    position: absolute;
    top: 0.625rem;
    right: 0.625rem;
    z-index: 3;
    display: grid;
    place-items: center;
    width: 2.75rem;
    height: 2.75rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--background);
    color: var(--foreground);
    box-shadow: var(--shadow-pop);
  }
  .expand:hover {
    background: var(--muted);
  }
  .expand:focus-visible {
    outline: 2px solid var(--ring);
    outline-offset: 2px;
  }
  .expanded {
    position: fixed;
    inset: 0;
    z-index: 60;
    padding: 0.75rem;
    padding-bottom: calc(0.75rem + env(safe-area-inset-bottom));
    background: color-mix(in oklch, var(--background) 92%, transparent);
  }
  .expanded :global(.network-map) {
    height: 100%;
    aspect-ratio: auto;
  }
  .expanded .expand {
    top: 1.375rem;
    right: 1.375rem;
  }
</style>
