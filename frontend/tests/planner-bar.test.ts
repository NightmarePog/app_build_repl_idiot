import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { Connection, ConnectionLeg } from '../src/lib/api/generated';
import { fastestDuration, journeyBar } from '../src/lib/features/planner/planner-core';

const leg = (minutes: number, wait: number | null) =>
  ({ duration_minutes: minutes, transfer_minutes: wait }) as unknown as ConnectionLeg;
const connection = (legs: ConnectionLeg[], duration: number) =>
  ({ legs, duration_minutes: duration }) as unknown as Connection;

test('journeyBar compresses long waits so rides stay readable', () => {
  const [ride, wait] = journeyBar(connection([leg(20, null), leg(13, 780)], 813));
  assert.ok(wait.kind === 'wait' && wait.long && wait.share < 0.4 && ride.share > 0.3);
});

test('fastestDuration marks only a unique minimum', () => {
  const a = connection([leg(20, null)], 20);
  const b = connection([leg(30, null)], 30);
  assert.equal(fastestDuration([a, b]), 20);
  assert.equal(fastestDuration([a, a]), null);
  assert.equal(fastestDuration([a]), null);
});
