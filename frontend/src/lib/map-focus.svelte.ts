import type { ComponentProps } from 'svelte';
import type NetworkMap from '$lib/features/network/NetworkMap.svelte';

/**
 * What the map page's one big map shows while a stop, line or trip detail is open in its panel.
 * The detail page sets it and clears it when it leaves; the map page spreads it onto its map.
 */
export const mapFocus = $state<{ props: Partial<ComponentProps<typeof NetworkMap>> | null }>({
  props: null
});

/** Use in a detail page's `$effect`: shows `props` on the big map until the page goes away. */
export function focusMap(props: Partial<ComponentProps<typeof NetworkMap>>) {
  mapFocus.props = props;
  return () => {
    if (mapFocus.props === props) mapFocus.props = null;
  };
}
