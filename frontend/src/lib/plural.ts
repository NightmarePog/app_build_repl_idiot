/** Czech plural form: 1 spoj, 2–4 spoje, 5+ (and 0) spojů. */
export function czechPlural(count: number, one: string, few: string, many: string): string {
  if (count === 1) return one;
  return count >= 2 && count <= 4 ? few : many;
}

/** "3 spoje" — count plus the matching word. */
export function czechCount(count: number, one: string, few: string, many: string): string {
  return `${count} ${czechPlural(count, one, few, many)}`;
}
