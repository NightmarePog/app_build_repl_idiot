import assert from 'node:assert/strict';
import { test } from 'node:test';
import { layoutNetwork } from '../src/lib/features/lines/network-layout';

const s = (id: number) => ({ id, name: `S${id}` });

test('merges shared stops into one interchange node between the rows', () => {
  const layout = layoutNetwork([
    { id: 'A', stops: [s(1), s(2), s(3)] },
    { id: 'B', stops: [s(4), s(2), s(5)] }
  ]);
  assert.equal(layout.nodes.size, 5);
  assert.deepEqual(layout.nodes.get(2), { id: 2, name: 'S2', x: 1, y: 0.5, lines: ['A', 'B'] });
  assert.equal(layout.nodes.get(4)?.y, 1);
  assert.equal(layout.width, 2);
  assert.equal(layout.height, 1);
});

test('uses the hand-drawn schematic when every stop is known', () => {
  const layout = layoutNetwork([
    {
      id: 'B',
      stops: [
        { id: 6, name: 'Hopper Hall' },
        { id: 2, name: 'Ada Exchange' }
      ]
    },
    {
      id: 'A',
      stops: [
        { id: 1, name: 'Turing Terminal' },
        { id: 2, name: 'Ada Exchange' }
      ]
    }
  ]);
  assert.deepEqual(layout.nodes.get(2)?.lines, ['B', 'A']);
  assert.equal(layout.nodes.get(2)?.label, 'below');
});

test('handles an empty network', () => {
  assert.equal(layoutNetwork([]).width, 0);
});
