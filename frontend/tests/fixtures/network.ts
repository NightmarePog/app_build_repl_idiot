import type { Page } from '@playwright/test';

/** The seed network (backend V8__seed_network.sql), served the way the lines API returns it. */
const SEED = [
  {
    id: 1,
    number: 'A',
    name: 'Turing Terminal – Stack Garden',
    color: '#0070BB',
    stops: ['Turing Terminal', 'Ada Exchange', 'Pixel Park', 'Byte Square', 'Stack Garden']
  },
  {
    id: 2,
    number: 'B',
    name: 'Hopper Hall – Campus Gate',
    color: '#91F5AD',
    stops: [
      'Hopper Hall',
      'Ada Exchange',
      'Compiler Court',
      'Kernel Hub',
      'Syntax Square',
      'Lambda Lane',
      'Campus Gate'
    ]
  },
  {
    id: 3,
    number: 'C',
    name: 'Edison East – Quantum Commons',
    color: '#49B3B4',
    stops: [
      'Edison East',
      'Notebook Quay',
      'Kernel Hub',
      'Library Loop',
      'Byte Square',
      'Quantum Commons'
    ]
  }
];

const stopIds = new Map([...new Set(SEED.flatMap((line) => line.stops))].map((n, i) => [n, i + 1]));

const route = (id: number, bound: 'outbound' | 'inbound', names: string[]) => ({
  id,
  bound,
  direction: names[names.length - 1],
  stops: names.map((name, index) => ({ id: stopIds.get(name)!, name, position: index + 1 }))
});

export const seedLines = SEED.map(({ id, number, name, color }) => ({
  id,
  number,
  name,
  type: 'bus',
  color
}));

export const seedDetails = Object.fromEntries(
  SEED.map((line, index) => [
    line.id,
    {
      ...seedLines[index],
      routes: [
        route(line.id * 10, 'outbound', line.stops),
        route(line.id * 10 + 1, 'inbound', [...line.stops].reverse())
      ]
    }
  ])
);

export async function mockNetwork(page: Page) {
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: seedLines }));
  await page.route('**/api/v1/lines/*', (r) => {
    const id = Number(new URL(r.request().url()).pathname.split('/').pop());
    return seedDetails[id] ? r.fulfill({ json: seedDetails[id] }) : r.fulfill({ status: 404 });
  });
  await page.route('**/api/v1/stops/*/lines', (r) => r.fulfill({ json: [] }));
}
