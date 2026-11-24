import { redirect } from '@sveltejs/kit';
import { resolve } from '$app/paths';

/** The planner moved to /connections; old /spojeni links keep their search. */
export function load({ url }) {
  redirect(308, `${resolve('/connections')}${url.search}`);
}
