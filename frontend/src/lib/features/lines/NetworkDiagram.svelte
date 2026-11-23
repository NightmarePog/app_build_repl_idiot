<script lang="ts">
  import { resolve } from '$app/paths';
  import type { Line } from '$lib/features/network/lines.svelte';
  import { layoutNetwork, type LayoutNode } from './network-layout';

  let { lines }: { lines: Line[] } = $props();

  /** Line under the pointer or focus: the others dim so its whole route reads at once. */
  let focusLine = $state<string | null>(null);

  /** Grid units in px: one stop step along a line, one line row. */
  // Equal units, so a step of one across and one down is a true 45° diagonal.
  const X = 96;
  const Y = 96;
  const PAD = 80;
  /** Extra room on the right so the last horizontal label is never clipped. */
  const LABEL_ROOM = 150;

  const layout = $derived(
    layoutNetwork(
      lines.map((line) => ({
        id: line.number,
        stops: (line.routes.find((r) => r.bound === 'outbound') ?? line.routes[0])?.stops ?? []
      }))
    )
  );
  const color = $derived(new Map(lines.map((line) => [line.number, line])));

  const px = (x: number) => PAD + x * X;
  const py = (y: number) => PAD + 10 + y * Y;
  const width = $derived(PAD * 2 + LABEL_ROOM + layout.width * X);
  const height = $derived(py(layout.height) + PAD);

  /** Metro-map segment: straight run, then a 45° bend into the next stop's row. */
  function segment(a: { x: number; y: number }, b: { x: number; y: number }) {
    const [x1, y1, x2, y2] = [px(a.x), py(a.y), px(b.x), py(b.y)];
    const dy = Math.abs(y2 - y1);
    const dir = Math.sign(x2 - x1) || 1;
    if (dy === 0 || Math.abs(x2 - x1) < dy) return `L${x2},${y2}`;
    return `L${x2 - dir * dy},${y1} L${x2},${y2}`;
  }

  /**
   * Horizontal labels: the top row reads above its stops, the bottom row below; stops on inner
   * rows alternate above/below in left-to-right order, so neighbours never share a side.
   */
  /** Label anchor for a node: its schematic side, else the alternating rule above. */
  function labelAt(node: LayoutNode, r: number) {
    const [x, y] = [px(node.x), py(node.y)];
    switch (node.label) {
      case 'above-left':
        return { x: x - r - 4, y: y - r - 10, anchor: 'end' };
      case 'above':
        return { x, y: y - r - 10, anchor: 'middle' };
      case 'below':
        return { x, y: y + r + 20, anchor: 'middle' };
      case 'below-right':
        return { x: x + r + 4, y: y + r + 20, anchor: 'start' };
      case 'left':
        return { x: x - r - 10, y: y + 5, anchor: 'end' };
      case 'right':
        return { x: x + r + 10, y: y + 5, anchor: 'start' };
    }
    const under = below.get(node.id);
    if (under === undefined) return { x: x + r + 8, y: y - r - 4, anchor: 'start' };
    return { x, y: under ? y + r + 20 : y - r - 10, anchor: 'middle' };
  }

  const below = $derived.by(() => {
    // eslint-disable-next-line svelte/prefer-svelte-reactivity -- local scratch map, rebuilt per derive
    const side = new Map<number, boolean>();
    // eslint-disable-next-line svelte/prefer-svelte-reactivity -- local scratch map, rebuilt per derive
    const rows = new Map<number, { id: number; x: number }[]>();
    for (const node of layout.nodes.values()) {
      if (node.y <= 0) side.set(node.id, false);
      else if (node.y >= layout.height) side.set(node.id, true);
      else if (Number.isInteger(node.y)) rows.set(node.y, [...(rows.get(node.y) ?? []), node]);
    }
    for (const row of rows.values())
      row.sort((a, b) => a.x - b.x).forEach((node, i) => side.set(node.id, i % 2 === 1));
    return side;
  });

  function path(stops: { x: number; y: number }[]) {
    if (!stops.length) return '';
    return `M${px(stops[0]!.x)},${py(stops[0]!.y)} ${stops
      .slice(1)
      .map((s, i) => segment(stops[i]!, s))
      .join(' ')}`;
  }
</script>

<figure class="diagram">
  <div class="scroll">
    <svg
      viewBox={`0 0 ${width} ${height}`}
      style:min-width={`${Math.min(width, 760)}px`}
      role="group"
      aria-label="Schéma sítě linek"
    >
      {#each layout.lines as line (line.id)}
        {@const meta = color.get(line.id)}
        <path
          class="rail"
          class:dim={focusLine !== null && focusLine !== line.id}
          d={path(line.stops.map((id) => layout.nodes.get(id)!))}
          style:stroke={meta?.color}
          role="presentation"
          onpointerenter={() => (focusLine = line.id)}
          onpointerleave={() => (focusLine = null)}
        />
      {/each}
      {#each layout.lines as line (line.id)}
        {@const meta = color.get(line.id)}
        {@const first = layout.nodes.get(line.stops[0]!)}
        {#if first && meta}
          <!-- Badge on the side of the terminus away from its label, so they never overlap. -->
          {@const up = first.label !== 'above'}
          <g
            transform={`translate(${px(first.x) - 30},${py(first.y) + (up ? -32 : 32)})`}
            aria-hidden="true"
            class="line-badge"
            onpointerenter={() => (focusLine = line.id)}
            onpointerleave={() => (focusLine = null)}
          >
            <rect class="badge" x="-14" y="-14" width="28" height="28" style:fill={meta.color} />
            <text class="badge-text" y="1" style:fill={meta.ink}>{meta.number}</text>
          </g>
        {/if}
      {/each}
      {#each [...layout.nodes.values()] as node (node.id)}
        {@const r = node.lines.length > 1 ? 14 : 7}
        {@const at = labelAt(node, r)}
        <a
          href={resolve('/stops/[id]', { id: String(node.id) })}
          class="stop"
          class:dim={focusLine !== null && !node.lines.includes(focusLine)}
          aria-label={`Zastávka ${node.name}${node.lines.length > 1 ? `, přestup ${node.lines.join(', ')}` : ''}`}
        >
          <title
            >{node.name}{node.lines.length > 1 ? `, přestup ${node.lines.join(', ')}` : ''}</title
          >
          <circle
            cx={px(node.x)}
            cy={py(node.y)}
            {r}
            class:interchange={node.lines.length > 1}
            style:stroke={node.lines.length > 1 ? undefined : color.get(node.lines[0]!)?.color}
          />
          <text
            class="label"
            class:strong={node.lines.length > 1}
            x={at.x}
            y={at.y}
            text-anchor={at.anchor}
            paint-order="stroke">{node.name}</text
          >
        </a>
      {/each}
    </svg>
  </div>
</figure>

<style>
  .diagram {
    margin-top: 1rem;
    border-radius: var(--radius);
    background: var(--card);
    border: 1px solid var(--border);
  }
  .scroll {
    overflow-x: auto;
  }
  svg {
    display: block;
    width: 100%;
    height: auto;
  }
  .rail,
  .stop {
    transition: opacity var(--motion-fast) var(--ease-out);
  }
  .dim {
    opacity: 0.25;
  }
  .line-badge {
    cursor: default;
  }
  .stop {
    cursor: pointer;
  }
  .rail {
    fill: none;
    stroke-width: 10;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  circle {
    fill: var(--card);
    stroke-width: 4;
  }
  circle.interchange {
    stroke: var(--foreground);
    stroke-width: 5;
  }
  .badge {
    rx: var(--radius);
  }
  .badge-text {
    font-size: var(--fs-lg);
    font-weight: var(--weight-strong);
    text-anchor: middle;
    dominant-baseline: middle;
  }
  .label {
    fill: var(--foreground);
    stroke: var(--card);
    stroke-width: 6px;
    stroke-linejoin: round;
    font-family: var(--font-sans);
    font-size: var(--fs-base);
    font-weight: var(--weight-strong);
  }
  .label.strong {
    font-weight: var(--weight-strong);
  }
  .stop:hover .label {
    text-decoration: underline;
  }
  .stop:hover circle {
    fill: var(--accent);
    stroke-width: 7;
  }
  @media (prefers-reduced-motion: reduce) {
    .rail,
    .stop {
      transition: none;
    }
  }
  .stop:focus-visible {
    outline: none;
  }
  .stop:focus-visible circle {
    stroke: var(--ring);
    stroke-width: 6;
  }
</style>
