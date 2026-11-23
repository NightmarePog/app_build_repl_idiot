import { getTrip, type TripDetail } from '$lib/api/generated';
import { unwrap } from '$lib/api/resource.svelte';

/**
 * Trip details by id, fetched once per page load: the timetable is static, and the line sheet
 * and the "kam jedete" filter both read times at many stops of the same few trips.
 */
const cache = new Map<number, Promise<TripDetail>>();

/**
 * Pass the caller's signal so leaving the page cancels the request. A failed or aborted fetch
 * leaves the cache, so the next caller tries again.
 */
export function tripDetail(tripId: number, signal?: AbortSignal) {
  let pending = cache.get(tripId);
  if (!pending) {
    const timeout = AbortSignal.timeout(10_000);
    const request = unwrap(
      getTrip({ path: { tripId }, signal: signal ? AbortSignal.any([signal, timeout]) : timeout })
    );
    pending = request;
    request.catch(() => {
      if (cache.get(tripId) === request) cache.delete(tripId);
    });
    cache.set(tripId, request);
  }
  return pending;
}

export { lineDirectionHref, withHash, withQuery } from '$lib/links';
