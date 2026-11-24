import { Accessibility, House, Ticket } from '@lucide/svelte';

/** Everyday wording for the three API flags, shared by every stop view. */
export const AMENITIES = [
  {
    value: 'accessible',
    field: 'wheelchair_accessible',
    label: 'Nástup bez schodů',
    /** Fits a filter chip. */
    short: 'Bez schodů',
    icon: Accessibility
  },
  {
    value: 'shelter',
    field: 'has_shelter',
    label: 'Střecha nad hlavou',
    /** Fits a filter chip. */
    short: 'Přístřešek',
    icon: House
  },
  {
    value: 'tickets',
    field: 'has_ticket_machine',
    label: 'Jízdenky na místě',
    /** Fits a filter chip. */
    short: 'Automat',
    icon: Ticket
  }
] as const;

export type AmenityValue = (typeof AMENITIES)[number]['value'];
