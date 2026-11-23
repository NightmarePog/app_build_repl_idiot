<script lang="ts">
  import { inkFor, isPale } from '$lib/features/lines/line-style';

  let {
    number,
    color,
    size = 'md',
    label = false
  }: {
    number: string;
    color: string;
    size?: 'xs' | 'sm' | 'md' | 'lg';
    label?: boolean;
  } = $props();
</script>

<span
  class="plate tabular {size}"
  class:line-cased={isPale(color)}
  style:--line={color}
  style:--line-ink={inkFor(color)}
  ><span aria-hidden="true">{number}</span>{#if label}<span class="sr-only">Linka {number}</span
    >{/if}</span
>

<style>
  .plate {
    display: inline-grid;
    place-items: center;
    flex-shrink: 0;
    border-radius: var(--radius-sm);
    background: var(--line);
    color: var(--line-ink);
    box-shadow: inset 0 0 0 1.5px var(--line-stroke, transparent);
    font-weight: var(--font-display-weight);
    line-height: 1;
  }
  .xs {
    min-width: 1.375rem;
    height: 1.375rem;
    font-size: var(--fs-xs);
  }
  .sm {
    min-width: 1.75rem;
    height: 1.75rem;
    font-size: var(--fs-base);
  }
  .md {
    min-width: 2.5rem;
    height: 2.5rem;
    font-size: var(--fs-lg);
  }
  .lg {
    min-width: 4rem;
    height: 4rem;
    font-size: var(--fs-2xl);
  }
  /* The board is always dark, so pale lines need no keyline there. */
  :global(.board-surface) .plate {
    box-shadow: none;
  }
</style>
