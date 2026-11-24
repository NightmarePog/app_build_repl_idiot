import type { Snippet } from 'svelte';

/**
 * The map page's search and filters live in the app's search palette ("Hledat", Ctrl K), while
 * the lists they narrow live in the map panel. This shared state connects the two: while the map
 * is shown, the palette starts from and writes to `query` and shows the map's `tools`.
 */
export const mapSearch = $state<{
  active: boolean;
  query: string;
  tools: Snippet | undefined;
}>({
  active: false,
  query: '',
  tools: undefined
});
