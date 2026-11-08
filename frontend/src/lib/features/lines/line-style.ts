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
