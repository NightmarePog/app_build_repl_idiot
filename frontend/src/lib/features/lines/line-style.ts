import type { Line } from '$lib/api/generated';

/** Line types the network uses; the API sends `type` as free text, so others fall back. */
type KnownType = 'bus' | 'replacement' | 'tram' | 'trolleybus' | 'night' | 'shuttle';

/** One table per type: the adjective for headings and the noun for compact meta lines. */
const TYPES: Record<KnownType, { label: string; noun: string }> = {
  bus: { label: 'Autobusová', noun: 'autobus' },
  replacement: { label: 'Náhradní doprava', noun: 'náhradní doprava' },
  tram: { label: 'Tramvajová', noun: 'tramvaj' },
  trolleybus: { label: 'Trolejbusová', noun: 'trolejbus' },
  night: { label: 'Noční', noun: 'noční linka' },
  shuttle: { label: 'Kyvadlová', noun: 'kyvadlová doprava' }
};

function known(type: Line['type']) {
  const key = type.toLowerCase();
  return key in TYPES ? TYPES[key as KnownType] : null;
}

export function typeLabel(type: Line['type']) {
  return known(type)?.label ?? type;
}

/** Short mode label for compact meta lines ("autobus"), falling back to the raw API value. */
export function typeNoun(type: Line['type']) {
  return known(type)?.noun ?? type;
}

/** WCAG relative luminance of a #rrggbb colour. */
function luminance(hex: string) {
  const channel = (offset: number) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

/** Near-black or white text, whichever reads better on the line colour. */
export function inkFor(hex: string) {
  const l = luminance(hex);
  return (l + 0.05) / 0.05 >= 1.05 / (l + 0.05)
    ? 'var(--ink-on-line-dark)'
    : 'var(--ink-on-line-light)';
}

/**
 * Pale line colours (under 3:1 against white, WCAG 1.4.11) get a dark keyline on light
 * surfaces, the way TfL cases the yellow Circle line. Apply the `line-cased` class.
 */
export function isPale(hex: string) {
  return 1.05 / (luminance(hex) + 0.05) < 3;
}

/**
 * Czech "z"/"ze" before a number: "ze" when the spoken numeral starts with s/š
 * (šest, sedm, šestnáct, sedmnáct, šedesát…, sedmdesát…, sto…).
 */
export function zOrZe(count: number) {
  const n = Math.abs(Math.trunc(count));
  if (n >= 100 && n < 200) return 'ze';
  if (n >= 60 && n < 80) return 'ze';
  return [6, 7, 16, 17].includes(n) ? 'ze' : 'z';
}
