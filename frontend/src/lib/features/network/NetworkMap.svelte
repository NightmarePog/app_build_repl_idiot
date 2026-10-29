<script lang="ts">
  import 'maplibre-gl/dist/maplibre-gl.css';
  import { onMount, untrack } from 'svelte';
  import type {
    ExpressionSpecification,
    GeoJSONSource,
    LayerSpecification,
    Map as MapLibreMap,
    Marker
  } from 'maplibre-gl';
  import type { Stop } from '$lib/api/generated';
  import { prefersReducedMotion } from '$lib/motion';
  import {
    LINES,
    LINE_IDS,
    POSITIONS,
    isTransfer,
    linesOf,
    primaryLine,
    routeCoordinates
  } from './lines';
  import { BOUNDS, MAX_BOUNDS, STYLE_URL } from './campus';
  import LineBadge from './LineBadge.svelte';
  import { streetLabels } from './streets';

  let {
    stops,
    selected = null,
    highlighted = null,
    dimmed = [],
    onselect,
    focus = false,
    interactive = true
  }: {
    stops: Stop[];
    selected?: number | null;
    highlighted?: number | null;
    /** Stops filtered out of the current result set are drawn faded. */
    dimmed?: number[];
    onselect?: (stop: Stop) => void;
    /** Fly the camera to the selected stop. */
    focus?: boolean;
    /** false = decorative live preview: no input, hidden from AT, slow bearing drift. */
    interactive?: boolean;
  } = $props();

  type MapLib = typeof import('maplibre-gl');

  const PITCH = 45;
  const BEARING = -18;
  const PADDING = { top: 56, bottom: 48, left: 40, right: 40 };
  const DRIFT_DEG_PER_S = 2;

  /** Base-map layers that only add clutter around a transit map. */
  const HIDDEN =
    /^(natural_earth|poi_|road_one_way|highway-shield|road_shield|airport|label_|boundary_|aeroway)/;

  /** Dark repaint of the OpenFreeMap "liberty" style (it has 3D buildings; "dark" does not). */
  const DARK = {
    background: '#1b1d21',
    green: '#1f2922',
    greenLine: '#26332a',
    landuse: '#202226',
    water: '#1b2b39',
    casing: '#131518',
    minor: '#2c2f35',
    major: '#383c43',
    path: '#3a3f38',
    rail: '#3a3d44',
    building: '#26292e',
    building3d: '#2f3238',
    text: '#a3a8b0',
    halo: '#1b1d21'
  };
  const LIGHT_BUILDING = '#e4e0d9';

  function darkPaint(layer: LayerSpecification): Record<string, unknown> | null {
    const id = layer.id;
    switch (layer.type) {
      case 'background':
        return { 'background-color': DARK.background };
      case 'fill':
        if (/park|wood|grass|pitch|track|wetland/.test(id)) return { 'fill-color': DARK.green };
        if (/water/.test(id)) return { 'fill-color': DARK.water };
        if (id === 'building')
          return { 'fill-color': DARK.building, 'fill-outline-color': DARK.casing };
        return { 'fill-color': DARK.landuse };
      case 'fill-extrusion':
        return { 'fill-extrusion-color': DARK.building3d };
      case 'line':
        if (/water/.test(id)) return { 'line-color': DARK.water };
        if (/outline/.test(id)) return { 'line-color': DARK.greenLine };
        if (/casing/.test(id)) return { 'line-color': DARK.casing };
        if (/rail/.test(id)) return { 'line-color': DARK.rail };
        if (/path|pedestrian/.test(id)) return { 'line-color': DARK.path };
        if (/motorway|trunk|primary|secondary|tertiary/.test(id))
          return { 'line-color': DARK.major };
        return { 'line-color': DARK.minor };
      case 'symbol':
        return { 'text-color': DARK.text, 'text-halo-color': DARK.halo };
      default:
        return null;
    }
  }

  let container: HTMLDivElement;
  let probe: HTMLSpanElement;
  let map = $state.raw<MapLibreMap | null>(null);
  let ready = $state(false);
  let failed = $state(false);
  let lib: MapLib | null = null;

  type Entry = { stop: Stop; marker: Marker; el: HTMLButtonElement };
  let entries: Entry[] = [];
  let cursor: number | null = null;

  const isDark = () => document.documentElement.classList.contains('dark');

  /** Resolves any CSS colour (oklch, var()) to rgb() so MapLibre can use it. */
  function resolveColor(value: string) {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 1;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) return '#888888';
    probe.style.color = value;
    ctx.fillStyle = '#888888';
    ctx.fillStyle = getComputedStyle(probe).color;
    ctx.fillRect(0, 0, 1, 1);
    const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data;
    return `rgb(${r}, ${g}, ${b})`;
  }

  function routesData() {
    const current = stops.find((stop) => stop.id === selected);
    return {
      type: 'FeatureCollection' as const,
      features: LINE_IDS.map((id) => ({
        type: 'Feature' as const,
        properties: {
          id,
          color: resolveColor(LINES[id].color),
          offset: LINES[id].offset,
          opacity: current && !LINES[id].stops.includes(current.name) ? 0.25 : 1
        },
        geometry: { type: 'LineString' as const, coordinates: routeCoordinates(id) }
      }))
    };
  }

  const zoomRamp = (values: [number, number, number]): ExpressionSpecification => [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
    14,
    values[0],
    16,
    values[1],
    18,
    values[2]
  ];
  const routeOffset: ExpressionSpecification = [
    'interpolate',
    ['exponential', 2],
    ['zoom'],
    14,
    ['*', ['get', 'offset'], 2],
    16,
    ['*', ['get', 'offset'], 4],
    18,
    ['*', ['get', 'offset'], 8]
  ];

  /** Original paint values, so the light theme can be restored after a dark repaint. */
  const original: Record<string, Record<string, unknown>> = {};

  function customizeStyle(m: MapLibreMap) {
    const layers = m.getStyle().layers ?? [];
    // Reuse the base style's font stack so the fictional street names load the same glyphs.
    const font = layers
      .map((layer) => (layer.type === 'symbol' ? layer.layout?.['text-font'] : undefined))
      .find(Array.isArray) as string[] | undefined;
    for (const layer of layers) {
      // Every base label is a real place name; the campus is fictional, so none of them show.
      if (HIDDEN.test(layer.id) || layer.type === 'symbol')
        m.setLayoutProperty(layer.id, 'visibility', 'none');
      const dark = darkPaint(layer);
      if (dark) {
        original[layer.id] = Object.fromEntries(
          Object.keys(dark).map((key) => [key, m.getPaintProperty(layer.id, key)])
        );
      }
    }
    if (m.getLayer('building-3d')) {
      m.setPaintProperty('building-3d', 'fill-extrusion-opacity', 0.9);
      original['building-3d'] = { 'fill-extrusion-color': LIGHT_BUILDING };
    }

    m.addSource('routes', { type: 'geojson', data: routesData() });
    const before = m.getLayer('building-3d')
      ? 'building-3d'
      : layers.find((layer) => layer.type === 'symbol')?.id;
    m.addLayer(
      {
        id: 'route-casing',
        type: 'line',
        source: 'routes',
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: {
          'line-color': '#ffffff',
          'line-width': zoomRamp([4.5, 7.5, 12.5]),
          'line-offset': routeOffset,
          'line-opacity': ['get', 'opacity']
        }
      },
      before
    );
    m.addLayer(
      {
        id: 'route',
        type: 'line',
        source: 'routes',
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: {
          'line-color': ['get', 'color'],
          'line-width': zoomRamp([2.6, 5, 9]),
          'line-offset': routeOffset,
          'line-opacity': ['get', 'opacity']
        }
      },
      before
    );
    m.addSource('street-names', { type: 'geojson', data: streetLabels() });
    m.addLayer({
      id: 'street-names',
      type: 'symbol',
      source: 'street-names',
      minzoom: 15,
      layout: {
        'symbol-placement': 'line',
        'text-field': ['get', 'name'],
        'text-font': font ?? ['Noto Sans Regular'],
        'text-size': ['interpolate', ['linear'], ['zoom'], 15, 10, 18, 13],
        'text-letter-spacing': 0.02,
        'symbol-spacing': 320
      },
      paint: streetNamePaint(isDark())
    });
  }

  function streetNamePaint(dark: boolean) {
    return {
      'text-color': dark ? '#c9ccd2' : '#5b5e66',
      'text-halo-color': dark ? DARK.background : '#ffffff',
      'text-halo-width': 1.4
    };
  }

  function applyTheme() {
    const m = map;
    if (!m || !ready) return;
    const dark = isDark();
    for (const layer of m.getStyle().layers ?? []) {
      const values = dark ? darkPaint(layer) : original[layer.id];
      if (!values || layer.id.startsWith('route')) continue;
      for (const [name, value] of Object.entries(values)) m.setPaintProperty(layer.id, name, value);
    }
    m.setPaintProperty('route-casing', 'line-color', dark ? DARK.background : '#ffffff');
    for (const [name, value] of Object.entries(streetNamePaint(dark)))
      m.setPaintProperty('street-names', name, value);
    refreshRoutes();
  }

  function refreshRoutes() {
    (map?.getSource('routes') as GeoJSONSource | undefined)?.setData(routesData());
  }

  const BUS_GLYPH =
    '<svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true"><rect x="3" y="2" width="10" height="10.5" rx="2" fill="currentColor"/><rect x="4.5" y="3.5" width="7" height="3.5" rx="0.8" fill="var(--pin)"/><circle cx="5.6" cy="13.6" r="1.2" fill="currentColor"/><circle cx="10.4" cy="13.6" r="1.2" fill="currentColor"/></svg>';

  function createMarker(m: MapLibreMap, stop: Stop): Entry {
    const el = document.createElement('button');
    el.type = 'button';
    el.className = 'stop-pin';
    el.setAttribute('aria-label', `Vybrat zastávku ${stop.name}`);
    const line = primaryLine(stop.name);
    const transfer = isTransfer(stop.name);
    el.classList.toggle('transfer', transfer);
    el.style.setProperty('--pin', line ? LINES[line].color : 'var(--muted-foreground)');
    el.style.setProperty('--pin-ink', line ? LINES[line].ink : 'var(--background)');

    const plate = document.createElement('span');
    plate.className = 'plate';
    plate.innerHTML = BUS_GLYPH;
    if (transfer) {
      const stripes = document.createElement('span');
      stripes.className = 'stripes';
      for (const id of linesOf(stop.name)) {
        const stripe = document.createElement('i');
        stripe.style.background = LINES[id].color;
        stripes.append(stripe);
      }
      plate.append(stripes);
    }
    const callout = document.createElement('span');
    callout.className = 'callout';
    callout.setAttribute('aria-hidden', 'true');
    callout.textContent = stop.name;
    el.append(plate, callout);

    el.addEventListener('click', (event) => {
      event.stopPropagation();
      onselect?.(stop);
    });
    el.addEventListener('focus', () => (cursor = stop.id));
    el.addEventListener('keydown', (event) => onkey(event, stop));

    const marker = new lib!.Marker({ element: el, anchor: 'bottom', subpixelPositioning: true })
      .setLngLat(POSITIONS[stop.name])
      .addTo(m);
    return { stop, marker, el };
  }

  function updateMarkers() {
    const tabbable = entries.some((entry) => entry.stop.id === cursor)
      ? cursor
      : (entries.find((entry) => entry.stop.id === selected)?.stop.id ??
        entries[0]?.stop.id ??
        null);
    for (const { stop, el } of entries) {
      const active = stop.id === selected;
      el.setAttribute('aria-pressed', String(active));
      el.tabIndex = interactive && onselect && stop.id === tabbable ? 0 : -1;
      el.toggleAttribute('aria-hidden', !interactive);
      el.classList.toggle('active', active);
      el.classList.toggle('hot', stop.id === highlighted);
      el.classList.toggle('dim', dimmed.includes(stop.id) && !active);
      el.style.zIndex = active ? '3' : stop.id === highlighted ? '2' : '';
    }
  }

  const DIRECTIONS: Record<string, [number, number]> = {
    ArrowRight: [1, 0],
    ArrowLeft: [-1, 0],
    ArrowDown: [0, 1],
    ArrowUp: [0, -1]
  };

  function onkey(event: KeyboardEvent, from: Stop) {
    const direction = DIRECTIONS[event.key];
    const m = map;
    if (!direction || !m) return;
    event.preventDefault();
    const origin = m.project(POSITIONS[from.name]);
    let best: Entry | null = null;
    let score = Infinity;
    for (const entry of entries) {
      if (entry.stop.id === from.id) continue;
      const point = m.project(POSITIONS[entry.stop.name]);
      const vx = point.x - origin.x;
      const vy = point.y - origin.y;
      const along = vx * direction[0] + vy * direction[1];
      if (along <= 0) continue;
      const value = along + Math.abs(vx * direction[1] - vy * direction[0]) * 2;
      if (value < score) {
        score = value;
        best = entry;
      }
    }
    if (!best) return;
    cursor = best.stop.id;
    updateMarkers();
    best.el.focus();
  }

  onMount(() => {
    let disposed = false;
    let cleanup = () => {};
    const live = untrack(() => interactive);

    import('maplibre-gl')
      .then((module) => {
        if (disposed) return;
        lib = ('default' in module ? (module as { default: MapLib }).default : module) as MapLib;
        let m: MapLibreMap;
        try {
          m = new lib.Map({
            container,
            style: STYLE_URL,
            bounds: BOUNDS,
            fitBoundsOptions: { padding: 24 },
            maxBounds: MAX_BOUNDS,
            pitch: PITCH,
            bearing: BEARING,
            minZoom: 14,
            maxZoom: 19,
            maxPitch: 65,
            interactive: live,
            keyboard: live,
            attributionControl: { compact: false }
          });
        } catch {
          failed = true;
          return;
        }
        if (live) {
          m.addControl(new lib.NavigationControl({ visualizePitch: true }), 'bottom-right');
        }

        m.on('style.load', () => {
          customizeStyle(m);
          ready = true;
          applyTheme();
        });

        const resize = new ResizeObserver(() => m.resize());
        resize.observe(container);
        const theme = new MutationObserver(() => applyTheme());
        theme.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });

        // Decorative preview: slow bearing drift while on screen.
        let frame = 0;
        let visible = true;
        let last = 0;
        const drift = (time: number) => {
          if (last) m.setBearing(m.getBearing() + ((time - last) / 1000) * DRIFT_DEG_PER_S);
          last = time;
          frame = requestAnimationFrame(drift);
        };
        const startDrift = () => {
          if (live || frame || !visible || document.hidden || prefersReducedMotion()) return;
          last = 0;
          frame = requestAnimationFrame(drift);
        };
        const stopDrift = () => {
          cancelAnimationFrame(frame);
          frame = 0;
        };
        const onVisibility = () => (document.hidden ? stopDrift() : startDrift());
        const seen = new IntersectionObserver(([entry]) => {
          visible = entry.isIntersecting;
          if (visible) startDrift();
          else stopDrift();
        });
        if (!live) {
          seen.observe(container);
          document.addEventListener('visibilitychange', onVisibility);
          m.once('load', startDrift);
        }

        map = m;
        cleanup = () => {
          stopDrift();
          seen.disconnect();
          document.removeEventListener('visibilitychange', onVisibility);
          resize.disconnect();
          theme.disconnect();
          m.remove();
        };
      })
      .catch(() => {
        failed = true;
      });

    return () => {
      disposed = true;
      cleanup();
      entries = [];
      map = null;
      ready = false;
    };
  });

  // Stop markers.
  $effect(() => {
    const m = map;
    const list = stops;
    if (!m) return;
    const created = untrack(() =>
      list.filter((stop) => POSITIONS[stop.name]).map((stop) => createMarker(m, stop))
    );
    entries = created;
    untrack(() => {
      updateMarkers();
      refreshRoutes();
    });
    return () => created.forEach((entry) => entry.marker.remove());
  });

  // Marker state and route emphasis.
  $effect(() => {
    void [selected, highlighted, dimmed, onselect, interactive];
    updateMarkers();
    untrack(() => {
      if (ready) refreshRoutes();
    });
  });

  // Camera: fly to the selected stop, back to the whole campus when cleared.
  let lastCamera: number | null | undefined = undefined;
  $effect(() => {
    const m = map;
    const id = selected;
    if (!m || !focus) return;
    untrack(() => {
      if (id === lastCamera) return;
      const first = lastCamera === undefined;
      lastCamera = id;
      const stop = stops.find((item) => item.id === id);
      const instant = prefersReducedMotion();
      if (stop && POSITIONS[stop.name]) {
        // Open at campus scale around the stop; later selections fly in closer.
        const options = {
          center: POSITIONS[stop.name],
          zoom: first ? 15.4 : 16.2,
          pitch: 50,
          padding: PADDING
        };
        if (first || instant) m.jumpTo(options);
        else m.flyTo({ ...options, duration: 1200, curve: 1.3 });
      } else if (!first) {
        m.fitBounds(BOUNDS, {
          padding: 24,
          pitch: PITCH,
          bearing: BEARING,
          duration: instant ? 0 : 1000
        });
      }
    });
  });

  const current = $derived(stops.find((stop) => stop.id === selected));
</script>

<figure
  class="network-map"
  class:preview={!interactive}
  aria-hidden={interactive ? undefined : 'true'}
  aria-label={current ? `Mapa kampusu, vybraná zastávka ${current.name}` : 'Mapa kampusu'}
>
  <div class="canvas" bind:this={container}></div>
  <span class="probe" bind:this={probe} hidden></span>
  {#if failed && interactive}
    <ul class="fallback" aria-label="Zastávky">
      {#each stops.filter((stop) => POSITIONS[stop.name]) as stop (stop.id)}
        <li>
          <button type="button" aria-pressed={stop.id === selected} onclick={() => onselect?.(stop)}
            >Vybrat zastávku {stop.name}</button
          >
        </li>
      {/each}
    </ul>
  {/if}
  <figcaption class="legend">
    {#each LINE_IDS as id (id)}
      <LineBadge {id} />
    {/each}
  </figcaption>
</figure>

<style>
  .network-map {
    position: relative;
    margin: 0;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    background: #f4f2ee;
    overflow: hidden;
    aspect-ratio: 600 / 440;
    isolation: isolate;
  }
  :global(.dark) .network-map {
    background: #1b1d21;
  }
  .preview :global(.stop-pin) {
    pointer-events: none;
  }
  .network-map :global(.maplibregl-ctrl-attrib) {
    font-size: 0.625rem;
    background: color-mix(in oklch, var(--background) 80%, transparent);
    color: var(--muted-foreground);
  }
  .network-map :global(.maplibregl-ctrl-attrib a) {
    color: inherit;
  }
  .canvas {
    position: absolute;
    inset: 0;
  }
  .legend {
    position: absolute;
    top: 0.75rem;
    left: 0.875rem;
    display: flex;
    gap: 0.375rem;
    pointer-events: none;
  }
  .fallback {
    position: absolute;
    inset: 3rem 1rem 1rem;
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    gap: 0.375rem;
  }
  .fallback button {
    min-height: 2.75rem;
    padding: 0 0.75rem;
    border: 1px solid var(--border);
    border-radius: calc(var(--radius-lg) - 4px);
    background: var(--background);
    font-size: 0.8125rem;
  }
  .fallback button[aria-pressed='true'] {
    background: var(--foreground);
    color: var(--background);
  }

  /* Stop pins (created imperatively as MapLibre markers). */
  .network-map :global(.stop-pin) {
    display: grid;
    place-items: center;
    width: 2rem;
    height: 2.25rem;
    padding: 0;
    border: 0;
    background: none;
    cursor: pointer;
    transition: opacity var(--motion-fast) var(--ease-out);
  }
  .network-map :global(.stop-pin .plate) {
    position: relative;
    display: grid;
    place-items: center;
    width: 1.375rem;
    height: 1.375rem;
    border-radius: 0.4rem;
    background: var(--pin);
    color: var(--pin-ink);
    box-shadow:
      0 0 0 1.5px var(--background),
      0 1px 2px rgb(0 0 0 / 0.25);
    transform-origin: 50% 100%;
    transition: transform var(--motion-fast) var(--ease-out);
  }
  .network-map :global(.stop-pin .plate::after) {
    content: '';
    position: absolute;
    left: 50%;
    bottom: -4px;
    width: 6px;
    height: 6px;
    margin-left: -3px;
    background: inherit;
    transform: rotate(45deg);
    z-index: -1;
  }
  .network-map :global(.stop-pin.transfer .plate),
  .network-map :global(.stop-pin.active .plate) {
    background: var(--foreground);
    color: var(--background);
  }
  .network-map :global(.stop-pin.transfer .plate rect + rect),
  .network-map :global(.stop-pin.active .plate rect + rect) {
    fill: var(--foreground);
  }
  .network-map :global(.stop-pin .stripes) {
    position: absolute;
    left: 3px;
    right: 3px;
    bottom: 2px;
    display: flex;
    height: 2px;
  }
  .network-map :global(.stop-pin .stripes i) {
    flex: 1;
  }
  .network-map :global(.stop-pin.hot .plate) {
    transform: scale(1.15);
  }
  .network-map :global(.stop-pin.active .plate) {
    transform: scale(1.3);
  }
  .network-map :global(.stop-pin.dim) {
    opacity: 0.3;
  }
  .network-map :global(.stop-pin:focus-visible) {
    outline: none;
  }
  .network-map :global(.stop-pin:focus-visible .plate) {
    box-shadow:
      0 0 0 2px var(--background),
      0 0 0 4px var(--ring);
  }
  .network-map :global(.stop-pin .callout) {
    position: absolute;
    bottom: calc(100% + 0.5rem);
    left: 50%;
    padding: 0.25rem 0.5rem;
    border: 1px solid var(--border);
    border-radius: 0.375rem;
    background: var(--background);
    color: var(--foreground);
    font-size: 0.8125rem;
    font-weight: 600;
    white-space: nowrap;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.12);
    opacity: 0;
    transform: translate(-50%, 4px);
    pointer-events: none;
    transition:
      opacity var(--motion-fast) var(--ease-out),
      transform var(--motion-fast) var(--ease-out);
  }
  .network-map :global(.stop-pin.active .callout),
  .network-map :global(.stop-pin.hot .callout),
  .network-map :global(.stop-pin:focus-visible .callout) {
    opacity: 1;
    transform: translate(-50%, 0);
  }

  /* Navigation control in the app's look. */
  .network-map :global(.maplibregl-ctrl-group) {
    border-radius: calc(var(--radius-lg) - 4px);
    background: var(--background);
    box-shadow: 0 0 0 1px var(--border);
  }
  .network-map :global(.maplibregl-ctrl-group button + button) {
    border-top-color: var(--border);
  }
  :global(.dark) .network-map :global(.maplibregl-ctrl-icon) {
    filter: invert(1);
  }
  @media (pointer: coarse) {
    .network-map :global(.stop-pin) {
      width: 2.75rem;
      height: 2.75rem;
    }
    .network-map :global(.maplibregl-ctrl-group button) {
      width: 2.75rem;
      height: 2.75rem;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .network-map :global(.stop-pin),
    .network-map :global(.stop-pin .plate),
    .network-map :global(.stop-pin .callout) {
      transition: none;
    }
  }
</style>
