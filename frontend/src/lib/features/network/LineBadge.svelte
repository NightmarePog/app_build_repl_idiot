<script lang="ts">
  import LinePlate from '$lib/features/timetable/LinePlate.svelte';
  import { LINES, type LineId } from './lines.svelte';
  // Thin wrapper over LinePlate for places that only know the line id.
  let { id, size = 'sm' }: { id: LineId; size?: 'sm' | 'md' | 'lg' } = $props();
  const plateSize = { sm: 'xs', md: 'sm', lg: 'md' } as const;
</script>

<LinePlate
  number={id}
  color={LINES[id]?.color ?? 'var(--foreground)'}
  size={plateSize[size]}
  label
/>
