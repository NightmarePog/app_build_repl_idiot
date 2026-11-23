import { nowMinutes } from './time';

/**
 * One app-wide minute clock (Prague time), so every countdown and "now" marker agrees. The
 * interval runs only while something reads it.
 */
let now = $state(nowMinutes());
let readers = 0;
let tick: ReturnType<typeof setInterval> | undefined;

export const clock = {
  get now() {
    return now;
  },
  /** Call from a component's $effect; returns the cleanup. */
  subscribe() {
    readers++;
    now = nowMinutes();
    tick ??= setInterval(() => {
      const next = nowMinutes();
      if (next !== now) now = next;
    }, 5000);
    return () => {
      readers--;
      if (readers === 0 && tick) {
        clearInterval(tick);
        tick = undefined;
      }
    };
  }
};
