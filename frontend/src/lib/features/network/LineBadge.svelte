<script lang="ts">
  import { LINES, type LineId } from './lines';
  let { id, size = 'sm' }: { id: LineId; size?: 'sm' | 'md' | 'lg' } = $props();
</script>

<span
  class="line-badge tabular"
  class:md={size === 'md'}
  class:lg={size === 'lg'}
  style:background={LINES[id].color}
  style:color={LINES[id].ink}
  aria-label={`Linka ${id}`}>{id}</span
>

<style>
  .line-badge {
    display: inline-grid;
    place-items: center;
    width: 1.375rem;
    min-width: 1.375rem;
    height: 1.375rem;
    border-radius: 0.25rem;
    font-size: 0.75rem;
    font-weight: 800;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }
  .line-badge.md {
    width: 1.875rem;
    min-width: 1.875rem;
    height: 1.875rem;
    font-size: 0.9375rem;
    border-radius: 0.3125rem;
  }
  .line-badge.lg {
    width: 2.75rem;
    min-width: 2.75rem;
    height: 2.75rem;
    font-size: 1.375rem;
    border-radius: 0.375rem;
  }
</style>
