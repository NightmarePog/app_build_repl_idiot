import { browser } from '$app/environment';
import type { Stop } from '$lib/api/generated';
import { stopsList } from '$lib/features/network/stops-store.svelte';

// A browser-only preview (the last opened stop, else the stop list) shows the detail at once.
// The detail request still runs and its response always takes precedence.
let selected: Stop | null = null;

export function rememberStop(stop: Stop) {
  if (browser) selected = stop;
}

/** The stop as already known in the browser: the last one opened, or the loaded stop list. */
export function selectedStop(id: number): Stop | null {
  if (!browser) return null;
  if (selected?.id === id) return selected;
  return stopsList()?.find((stop) => stop.id === id) ?? null;
}

export function forgetStop(id: number) {
  if (selected?.id === id) selected = null;
}
