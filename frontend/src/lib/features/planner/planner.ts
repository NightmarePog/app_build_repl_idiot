import { resolve } from '$app/paths';
import type { ResolvedPathname } from '$app/types';
import { withQuery } from '$lib/links';
import type { Query } from './planner-core';

export * from './planner-core';

/** The planner URL; a null time means "now". */
export function plannerHref(query: Partial<Query>, detail?: string | null): ResolvedPathname {
  return withQuery(resolve('/connections'), {
    z: query.from,
    do: query.to,
    cas: query.time,
    podle: query.mode === 'arrival' ? 'prijezd' : null,
    detail
  });
}
