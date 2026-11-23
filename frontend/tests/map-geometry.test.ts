import assert from 'node:assert/strict';
import { test } from 'node:test';
import { boundsOf, pathThrough, pointAlong } from '../src/lib/features/network/map-geometry';

const street: [number, number][] = [
  [0, 0],
  [1, 0],
  [2, 0],
  [3, 0],
  [4, 0]
];

test('cuts the street path between stops in travel order', () => {
  assert.deepEqual(
    pathThrough(street, [
      [1, 0],
      [3, 0]
    ]),
    [
      [1, 0],
      [2, 0],
      [3, 0]
    ]
  );
});

test('walks the path backwards for the opposite direction', () => {
  assert.deepEqual(
    pathThrough(street, [
      [4, 0],
      [2, 0],
      [0, 0]
    ]),
    [
      [4, 0],
      [3, 0],
      [2, 0],
      [1, 0],
      [0, 0]
    ]
  );
});

test('finds the point a share of the way along', () => {
  assert.deepEqual(pointAlong(street, 0.5), [2, 0]);
  assert.deepEqual(pointAlong(street, 0), [0, 0]);
  assert.deepEqual(pointAlong(street, 1), [4, 0]);
  assert.equal(pointAlong([], 0.5), null);
});

test('bounds around points', () => {
  assert.deepEqual(
    boundsOf([
      [1, 5],
      [3, 2]
    ]),
    [
      [1, 2],
      [3, 5]
    ]
  );
  assert.equal(boundsOf([]), null);
});
