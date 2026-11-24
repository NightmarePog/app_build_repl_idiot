<script lang="ts">
  import '../app.css';
  import { resolve } from '$app/paths';
  import { Navigation } from '@lucide/svelte';
  import AppShell from '$lib/components/AppShell.svelte';
  import CommandPalette, {
    fuzzyScore,
    type PaletteItem
  } from '$lib/components/CommandPalette.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { onMount } from 'svelte';
  import { onNavigate } from '$app/navigation';
  import {
    describeStop,
    LINES,
    LINE_IDS,
    linesOf,
    loadNetwork,
    type LineId
  } from '$lib/features/network/lines.svelte';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import { listLineTrips, type TripSummary } from '$lib/api/generated';
  import { plannerHref } from '$lib/features/planner/planner';
  import { rememberStop } from '$lib/features/stops/selected-stop';
  import { isMapView } from '$lib/links';
  import { mapSearch } from '$lib/map-search.svelte';
  let { children } = $props();

  let paletteOpen = $state(false);

  // The line network feeds the menus, footer, map and stop pages on every route.
  onMount(() => void loadNetwork());

  // Same-document view transitions: the line plate morphs from the line sheet into the trip
  // ticket. Skipped where unsupported and for reduced motion.
  onNavigate((navigation) => {
    if (
      !document.startViewTransition ||
      matchMedia('(prefers-reduced-motion: reduce)').matches ||
      navigation.from?.url.pathname === navigation.to?.url.pathname ||
      (isMapView(navigation.from?.url.pathname) && isMapView(navigation.to?.url.pathname))
    )
      return;
    return new Promise((done) => {
      document.startViewTransition(async () => {
        done();
        await navigation.complete;
      });
    });
  });

  /** A palette result: a stop, a line, a trip, or a connection search. */
  type Item = PaletteItem & { lines: LineId[]; route?: boolean };

  /** The palette's current text, for reading a "from → to" search out of it. */
  let paletteText = $state('');

  const lineItems = $derived<Item[]>(
    LINE_IDS.map((id) => {
      const stops = LINES[id].stops;
      return {
        key: `line-${id}`,
        label: `Linka ${id}`,
        sub: stops.length
          ? `${stops[0]} → ${stops[stops.length - 1]}, ${stops.length} zastávek`
          : LINES[id].name,
        href: resolve('/(map)/lines/[id]', { id: String(LINES[id].apiId) }),
        lines: [id],
        suggested: true
      };
    })
  );

  /** Every trip (spoj) of every line, loaded the first time the palette opens. */
  let trips = $state<TripSummary[]>([]);
  let tripsLoading = false;
  async function loadTrips() {
    if (tripsLoading || trips.length) return;
    tripsLoading = true;
    await loadNetwork();
    const lists = await Promise.all(
      LINE_IDS.map((id) =>
        listLineTrips({ path: { lineId: LINES[id].apiId } })
          .then(({ data }) => data ?? [])
          .catch(() => [])
      )
    );
    trips = lists.flat();
    tripsLoading = false;
  }
  const tripItems = $derived<Item[]>(
    trips.map((trip) => ({
      key: `trip-${trip.id}`,
      label: `Spoj ${trip.line.number} · ${trip.origin.name} ${trip.departure_time}`,
      sub: `→ ${trip.destination.name}`,
      href: resolve('/(map)/trips/[id]', { id: String(trip.id) }),
      lines: [trip.line.number]
    }))
  );

  const stops = $derived(stopsList() ?? []);

  /** The best-matching stop for one side of a "from → to" search. */
  function bestStop(text: string) {
    let best: { stop: (typeof stops)[number]; score: number } | null = null;
    for (const stop of stops) {
      const score = fuzzyScore(stop.name, text);
      if (score !== null && (!best || score > best.score)) best = { stop, score };
    }
    return best?.stop ?? null;
  }
  /** "ada > campus", "ada → campus", "ada - campus" or "z ada do campus". */
  const routeItem = $derived.by<Item>(() => {
    const text = paletteText.trim();
    const parts =
      text.match(/^z\s+(.+?)\s+do\s+(.+)$/i)?.slice(1) ??
      text.split(/\s*(?:->|→|>|–|—|\s-\s)\s*/).filter(Boolean);
    const from = parts.length === 2 ? bestStop(parts[0]) : null;
    const to = parts.length === 2 ? bestStop(parts[1]) : null;
    if (from && to && from.id !== to.id)
      return {
        key: 'route-found',
        label: `${from.name} → ${to.name}`,
        sub: 'Vyhledat spojení',
        href: plannerHref({ from: from.id, to: to.id }),
        lines: [],
        route: true,
        pinned: true
      };
    return {
      key: 'route',
      label: 'Spojení odkud → kam',
      sub: 'Napište třeba „ada > campus“',
      href: resolve('/connections'),
      lines: [],
      route: true,
      pinned: !text
    };
  });

  const paletteItems = $derived<Item[]>([
    routeItem,
    ...stops.map((stop, index) => ({
      key: `stop-${stop.id}`,
      label: stop.name,
      sub: describeStop(stop.name),
      href: resolve('/(map)/stops/[id]', { id: String(stop.id) }),
      lines: linesOf(stop.name),
      suggested: index < 6
    })),
    ...lineItems,
    ...tripItems
  ]);

  function choose(item: PaletteItem) {
    const stop = stops.find((s) => `stop-${s.id}` === item.key);
    if (stop) rememberStop(stop);
  }
</script>

{#snippet paletteIcon(item: PaletteItem)}
  {@const entry = paletteItems.find((candidate) => candidate.key === item.key)}
  {#if entry?.route}
    <Navigation size={16} />
  {:else if entry?.lines.length}
    <span class="flex gap-0.5">
      {#each entry.lines as id (id)}<LineBadge {id} />{/each}
    </span>
  {:else}
    <span class="ml-1.5 size-2.5 rounded-full border-2 border-muted-foreground"></span>
  {/if}
{/snippet}

<AppShell bind:paletteOpen>{@render children()}</AppShell>

<!-- The palette is the app's menu: views, the map's filters, and search across stops, lines and pages. -->
<CommandPalette
  bind:open={paletteOpen}
  items={paletteItems}
  onopen={() => {
    void loadStops();
    void loadTrips();
  }}
  onchoose={choose}
  icon={paletteIcon}
  tools={mapSearch.active ? mapSearch.tools : undefined}
  initialQuery={mapSearch.active ? mapSearch.query : ''}
  onquery={(text) => {
    paletteText = text;
    // A "from → to" search is not a filter for the map's lists.
    if (mapSearch.active && routeItem.key !== 'route-found') mapSearch.query = text;
  }}
/>
