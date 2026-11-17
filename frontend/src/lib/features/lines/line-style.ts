/** Human labels for line types the API sends as free text. */
const TYPES: Record<string, string> = {
  bus: 'Autobusová',
  replacement: 'Náhradní doprava',
  tram: 'Tramvajová',
  trolleybus: 'Trolejbusová',
  night: 'Noční',
  shuttle: 'Kyvadlová'
};

export function typeLabel(type: string) {
  return TYPES[type.toLowerCase()] ?? type;
}

/** Near-black or white text, whichever reads better on the line colour (WCAG luminance). */
export function inkFor(hex: string) {
  const channel = (offset: number) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  };
  const luminance = 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
  return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05) ? '#1a1a1a' : '#ffffff';
}

function luminance(hex: string) {
  const channel = (offset: number) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
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

const NOUNS: Record<string, string> = {
  bus: 'autobus',
  replacement: 'náhradní doprava',
  tram: 'tramvaj',
  trolleybus: 'trolejbus',
  night: 'noční linka',
  shuttle: 'kyvadlová doprava'
};

/** Short mode label for compact meta lines ("autobus"), falling back to the raw API value. */
export function typeNoun(type: string) {
  return NOUNS[type.toLowerCase()] ?? type;
}
