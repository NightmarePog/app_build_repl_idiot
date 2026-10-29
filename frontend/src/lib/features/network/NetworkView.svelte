<script lang="ts">
  import type { Stop } from '$lib/api/generated';
  import NetworkMap from './NetworkMap.svelte';

  let {
    stops,
    selected = null,
    highlighted = null,
    dimmed = [],
    onselect,
    focus = false,
    interactive = true
  }: {
    stops: Stop[];
    selected?: number | null;
    highlighted?: number | null;
    dimmed?: number[];
    onselect?: (stop: Stop) => void;
    focus?: boolean;
    /** false = decorative live preview (no input, hidden from assistive tech, slow rotation). */
    interactive?: boolean;
    /** Kept for compatibility; the map is always the single MapLibre view. */
    allow3d?: boolean;
  } = $props();
</script>

<NetworkMap {stops} {selected} {highlighted} {dimmed} {onselect} {focus} {interactive} />
