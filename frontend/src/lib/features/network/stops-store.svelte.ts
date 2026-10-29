import { browser } from '$app/environment';
import { getStops, type Stop } from '$lib/api/generated';

/** One shared stops request for the header menus and the home page. */
let stops = $state<Stop[] | null>(null);
let failed = $state(false);
let pending: Promise<void> | null = null;

export function stopsList() {
  return stops;
}

export function stopsFailed() {
  return failed;
}

export function loadStops(signal?: AbortSignal) {
  if (!browser) return Promise.resolve();
  if (stops || pending) return pending ?? Promise.resolve();
  failed = false;
  pending = getStops({
    throwOnError: true,
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(10_000)])
      : AbortSignal.timeout(10_000)
  })
    .then(({ data }) => {
      stops = data;
    })
    .catch(() => {
      if (!signal?.aborted) failed = true;
    })
    .finally(() => {
      pending = null;
    });
  return pending;
}
