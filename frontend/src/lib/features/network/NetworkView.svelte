<script lang="ts">
  import type { Stop } from '$lib/api/generated';
  import type { LineId } from './lines';
  import NetworkMap from './NetworkMap.svelte';

  let {
    stops,
    selected = null,
    highlighted = null,
    dimmed = [],
    onselect,
    focus = false,
    interactive = true,
    line = null,
    inset = 0
  }: {
    stops: Stop[];
    selected?: number | null;
    highlighted?: number | null;
    dimmed?: number[];
    onselect?: (stop: Stop) => void;
    focus?: boolean;
    /** false = decorative live preview (no input, hidden from assistive tech, slow rotation). */
    interactive?: boolean;
    /** Line filter: only this line's route and stops are drawn. */
    line?: LineId | null;
    /** Pixels at the bottom covered by an overlay; the camera centres above it. */
    inset?: number;
    /** Kept for compatibility; the map is always the single MapLibre view. */
    allow3d?: boolean;
  } = $props();
</script>

<NetworkMap
  {stops}
  {selected}
  {highlighted}
  {dimmed}
  {onselect}
  {focus}
  {interactive}
  {line}
  {inset}
/>
