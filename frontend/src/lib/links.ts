import { resolve } from '$app/paths';
import type { ResolvedPathname } from '$app/types';

/**
 * Typed link builders. Every in-app href starts from resolve(); these only append a query, a
 * raw search string or a fragment, and keep the ResolvedPathname type so components need no
 * per-site lint suppressions.
 */
/** Links that carry query parameters: the typed route first, then URLSearchParams. */
export function withQuery(
  path: ResolvedPathname,
  query: Record<string, string | number | null | undefined>
): ResolvedPathname {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== null && value !== undefined && value !== '') params.set(key, String(value));
  const text = params.toString();
  // A query string does not change which route the path resolves to.
  return (text ? `${path}?${text}` : path) as ResolvedPathname;
}

/** A typed in-page anchor on a resolved path (no query), e.g. a stop row on a line page. */
export function withHash(
  path: ResolvedPathname,
  hash: string | null | undefined
): ResolvedPathname {
  return (hash ? `${path}#${hash}` : path) as ResolvedPathname;
}

/** A line page in one direction, optionally scrolled to a stop on its diagram. */
export function lineDirectionHref(
  lineId: number,
  bound: 'outbound' | 'inbound',
  stopId?: number
): ResolvedPathname {
  const path = withQuery(resolve('/lines/[id]', { id: String(lineId) }), {
    smer: bound === 'inbound' ? 'inbound' : null
  });
  return withHash(path, stopId === undefined ? null : `stop-${stopId}`);
}

/** A resolved path plus a search string copied verbatim from the current URL ('' or '?…'). */
export function withSearch(path: ResolvedPathname, search: string): ResolvedPathname {
  return `${path}${search}` as ResolvedPathname;
}

/** One label per destination, shared by the header, tab bar, footer and command palette. */
export const NAV_LABELS = {
  home: 'Úvod',
  stops: 'Zastávky',
  lines: 'Linky'
} as const;
