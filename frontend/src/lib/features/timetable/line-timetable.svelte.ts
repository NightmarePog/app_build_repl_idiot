import { SvelteMap, SvelteSet } from 'svelte/reactivity';
import { listLineTrips, type TripDetail } from '$lib/api/generated';
import { createResource, unwrap } from '$lib/api/resource.svelte';
import { tripDetail } from './trips';

/**
 * A line's trips for the line page. The list endpoint alone is enough to render departures from
 * the origin, so it renders first; each trip's stop times then load in the background, one by
 * one. A failed detail marks only that trip, and changing the line aborts the requests in flight.
 */
export function createLineTimetable(lineId: () => number) {
  const summaries = createResource(
    (signal) => unwrap(listLineTrips({ path: { lineId: lineId() }, signal })),
    lineId
  );
  const details = new SvelteMap<number, TripDetail>();
  const failed = new SvelteSet<number>();

  $effect(() => {
    const list = summaries.data;
    details.clear();
    failed.clear();
    if (!list) return;
    const controller = new AbortController();
    for (const trip of list) {
      tripDetail(trip.id, controller.signal)
        .then((detail) => {
          if (!controller.signal.aborted) details.set(detail.id, detail);
        })
        .catch(() => {
          if (!controller.signal.aborted) failed.add(trip.id);
        });
    }
    return () => controller.abort();
  });

  return {
    get trips() {
      return summaries.data;
    },
    get error() {
      return summaries.error;
    },
    get loading() {
      return summaries.loading;
    },
    details,
    failed,
    retry() {
      failed.clear();
      summaries.retry();
    }
  };
}

export type LineTimetable = ReturnType<typeof createLineTimetable>;
