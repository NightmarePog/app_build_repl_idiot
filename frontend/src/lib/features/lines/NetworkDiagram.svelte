<script lang="ts">
  import { resolve } from '$app/paths';
  import type { Line } from '$lib/features/network/lines.svelte';
  import { layoutNetwork } from './network-layout';

  let { lines }: { lines: Line[] } = $props();

  /** Grid units in px: one stop step along a line, one line row. */
  const X = 110;
  const Y = 90;
  const PAD = 70;

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
  const py = (y: number) => PAD + 30 + y * Y;
  const width = $derived(PAD * 2 + layout.width * X);
  const height = $derived(py(layout.height) + 30);

  /** Metro-map segment: straight run, then a 45° bend into the next stop's row. */
  function segment(a: { x: number; y: number }, b: { x: number; y: number }) {
    const [x1, y1, x2, y2] = [px(a.x), py(a.y), px(b.x), py(b.y)];
    const dy = Math.abs(y2 - y1);
    const dir = Math.sign(x2 - x1) || 1;
    if (dy === 0 || Math.abs(x2 - x1) < dy) return `L${x2},${y2}`;
    return `L${x2 - dir * dy},${y1} L${x2},${y2}`;
  }

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
          d={path(line.stops.map((id) => layout.nodes.get(id)!))}
          style:stroke={meta?.color}
        />
      {/each}
      {#each layout.lines as line (line.id)}
        {@const meta = color.get(line.id)}
        {@const first = layout.nodes.get(line.stops[0]!)}
        {#if first && meta}
          <g transform={`translate(${px(first.x) - 44},${py(first.y)})`} aria-hidden="true">
            <rect class="badge" x="-14" y="-14" width="28" height="28" style:fill={meta.color} />
            <text class="badge-text" y="1" style:fill={meta.ink}>{meta.number}</text>
          </g>
        {/if}
      {/each}
      {#each [...layout.nodes.values()] as node (node.id)}
        <a href={resolve('/stops/[id]', { id: String(node.id) })} class="stop">
          <title
            >{node.name}{node.lines.length > 1 ? `, přestup ${node.lines.join(', ')}` : ''}</title
          >
          <circle
            cx={px(node.x)}
            cy={py(node.y)}
            r={node.lines.length > 1 ? 11 : 6}
            class:interchange={node.lines.length > 1}
            style:stroke={node.lines.length > 1 ? undefined : color.get(node.lines[0]!)?.color}
          />
          <text
            class="label"
            class:strong={node.lines.length > 1}
            x={px(node.x) + 6}
            y={py(node.y) - 16}
            transform={`rotate(-30 ${px(node.x) + 6} ${py(node.y) - 16})`}>{node.name}</text
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
    font-size: 18px;
    font-weight: 800;
    text-anchor: middle;
    dominant-baseline: middle;
  }
  .label {
    fill: var(--foreground);
    font-size: 15px;
  }
  .label.strong {
    font-weight: 700;
  }
  .stop:hover .label {
    text-decoration: underline;
  }
  .stop:hover circle {
    fill: var(--accent);
  }
  .stop:focus-visible {
    outline: 2px solid var(--ring);
  }
</style>
