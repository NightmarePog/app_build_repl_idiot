import { BusFront, FileText, Mail, MapPin, Navigation, Route, TicketCheck } from '@lucide/svelte';

/**
 * The fixed set of page types. Each has one word, one icon and one accent colour,
 * so a line, a stop, a trip and a connection never look alike in the header.
 */
export const PAGE_KINDS = {
  line: { label: 'Linka', icon: BusFront, color: 'var(--primary)' },
  lines: { label: 'Linky', icon: Route, color: 'var(--primary)' },
  stop: { label: 'Zastávka', icon: MapPin, color: 'var(--kind-stop)' },
  stops: { label: 'Zastávky', icon: MapPin, color: 'var(--kind-stop)' },
  trip: { label: 'Spoj', icon: TicketCheck, color: 'var(--kind-trip)' },
  connection: { label: 'Spojení', icon: Navigation, color: 'var(--kind-connection)' },
  contact: { label: 'Kontakt', icon: Mail, color: 'var(--muted-foreground)' },
  terms: { label: 'Podmínky', icon: FileText, color: 'var(--muted-foreground)' }
} as const;

export type PageKind = (typeof PAGE_KINDS)[keyof typeof PAGE_KINDS];

/** The page type a header tag starts with, e.g. "Spoj · linka A" is a trip. */
export function kindFor(tag: string): PageKind | undefined {
  const word = tag.split('·')[0].trim().toLowerCase();
  if (word.startsWith('vyhled')) return PAGE_KINDS.connection;
  if (word.includes('linka')) return PAGE_KINDS.line;
  return Object.values(PAGE_KINDS).find((k) => k.label.toLowerCase() === word);
}
