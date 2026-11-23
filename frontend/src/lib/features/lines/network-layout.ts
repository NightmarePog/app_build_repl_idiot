/**
 * Schematic layout for the combined network diagram on /lines.
 *
 * The campus network is drawn from a hand-made octilinear schematic (every segment horizontal,
 * vertical or 45°, as a metro map is drawn by hand), keyed by stop name. If any served stop is
 * missing from it, a generic layout is used instead: every line gets its own row, a stop shared
 * by several lines becomes one interchange node between their rows. Either way a new stop never
 * disappears from the diagram. Coordinates are grid units starting at 0.
 */
export type LayoutInput = { id: string; stops: { id: number; name: string }[] };

/** Where a stop's name sits relative to its node. */
export type LabelSide = 'above' | 'below' | 'left' | 'right' | 'below-right' | 'above-left';

export type LayoutNode = {
  id: number;
  name: string;
  x: number;
  y: number;
  lines: string[];
  label?: LabelSide;
};

const SCHEMATIC: Record<string, { x: number; y: number; label: LabelSide }> = {
  'Turing Terminal': { x: 0, y: 1.5, label: 'above' },
  'Ada Exchange': { x: 2, y: 3.5, label: 'below' },
  'Pixel Park': { x: 4, y: 1.5, label: 'above' },
  'Byte Square': { x: 7, y: 1.5, label: 'above-left' },
  'Stack Garden': { x: 8.5, y: 0, label: 'right' },
  'Hopper Hall': { x: 0.5, y: 3.5, label: 'below' },
  'Compiler Court': { x: 3.5, y: 3.5, label: 'below' },
  'Kernel Hub': { x: 5, y: 3.5, label: 'below-right' },
  'Syntax Square': { x: 6.75, y: 3.5, label: 'below' },
  'Lambda Lane': { x: 8.25, y: 3.5, label: 'below' },
  'Campus Gate': { x: 9.75, y: 3.5, label: 'below' },
  'Edison East': { x: 0, y: 5.5, label: 'below' },
  'Notebook Quay': { x: 2, y: 5.5, label: 'below' },
  'Library Loop': { x: 6, y: 2.5, label: 'left' },
  'Quantum Commons': { x: 8.5, y: 2.5, label: 'right' }
};

function schematic(lines: LayoutInput[]) {
  const nodes = new Map<number, LayoutNode>();
  for (const line of lines) {
    for (const stop of line.stops) {
      const at = SCHEMATIC[stop.name];
      if (!at) return null;
      const node = nodes.get(stop.id);
      if (!node) nodes.set(stop.id, { id: stop.id, name: stop.name, ...at, lines: [line.id] });
      else if (!node.lines.includes(line.id)) node.lines.push(line.id);
    }
  }
  if (nodes.size === 0) return null;
  const all = [...nodes.values()];
  return {
    nodes,
    lines: lines.map((line) => ({ id: line.id, stops: line.stops.map((stop) => stop.id) })),
    width: Math.max(...all.map((n) => n.x)),
    height: Math.max(...all.map((n) => n.y))
  };
}

export function layoutNetwork(lines: LayoutInput[]) {
  const drawn = schematic(lines);
  if (drawn) return drawn;
  const acc = new Map<number, { name: string; xs: number[]; ys: number[]; lines: string[] }>();
  lines.forEach((line, row) => {
    line.stops.forEach((stop, index) => {
      const entry = acc.get(stop.id) ?? { name: stop.name, xs: [], ys: [], lines: [] };
      if (!entry.lines.includes(line.id)) {
        entry.xs.push(index);
        entry.ys.push(row);
        entry.lines.push(line.id);
      }
      acc.set(stop.id, entry);
    });
  });
  const mean = (values: number[]) => values.reduce((a, b) => a + b, 0) / values.length;
  const nodes = new Map<number, LayoutNode>();
  for (const [id, entry] of acc) {
    nodes.set(id, {
      id,
      name: entry.name,
      x: mean(entry.xs),
      y: mean(entry.ys),
      lines: entry.lines
    });
  }
  const all = [...nodes.values()];
  return {
    nodes,
    lines: lines.map((line) => ({ id: line.id, stops: line.stops.map((stop) => stop.id) })),
    width: all.length ? Math.max(...all.map((n) => n.x)) : 0,
    height: Math.max(0, lines.length - 1)
  };
}
