/**
 * Schematic layout for the combined network diagram on /lines. Every line gets its own row;
 * a stop shared by several lines becomes one interchange node placed between their rows. Along
 * a line, a stop sits at its position in the route, averaged over every line that serves it,
 * so interchanges line up. Coordinates are grid units starting at 0.
 */
export type LayoutInput = { id: string; stops: { id: number; name: string }[] };

export type LayoutNode = { id: number; name: string; x: number; y: number; lines: string[] };

export function layoutNetwork(lines: LayoutInput[]) {
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
