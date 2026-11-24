import { getTrip, type TripDetail } from '$lib/api/generated';
import { unwrap } from '$lib/api/resource.svelte';

/**
 * Trip details by id, fetched once per page load: the timetable is static, and the line sheet
 * and the "kam jedete" filter both read times at many stops of the same few trips.
 */
const cache = new Map<number, Promise<TripDetail>>();
/** The same trips once they have arrived. */
const known = new Map<number, TripDetail>();

/**
 * Pass the caller's signal so leaving the page cancels the request. A failed or aborted fetch
 * leaves the cache, so the next caller tries again.
 */
/** A trip already loaded in this browser, available synchronously so its page opens at once. */
export function knownTrip(tripId: number | null) {
  return tripId === null ? null : (known.get(tripId) ?? null);
}

export function tripDetail(tripId: number, signal?: AbortSignal) {
  let pending = cache.get(tripId);
  if (!pending) {
    const timeout = AbortSignal.timeout(10_000);
    const request = unwrap(
      getTrip({ path: { tripId }, signal: signal ? AbortSignal.any([signal, timeout]) : timeout })
    );
    pending = request;
    request.then(
      (detail) => known.set(tripId, detail),
      () => {
        if (cache.get(tripId) === request) cache.delete(tripId);
      }
    );
    cache.set(tripId, request);
  }
  return pending;
}

export { lineDirectionHref, withHash, withQuery } from '$lib/links';
