import { expect, test, type Page } from '@playwright/test';
import { mockBrandAssets } from './fixtures/assets';

const line = { id: 1, number: 'C', name: 'Campus Loop', type: 'bus', color: '#0070BB' };
const stop = (id: number, name: string) => ({ id, name, image_url: null });
const names = ['Edison East', 'Kernel Hub', 'Syntax Square', 'Quantum Commons'];
const ids = [12, 8, 9, 15];

const detail = {
  ...line,
  routes: [
    {
      id: 10,
      bound: 'outbound',
      direction: 'Quantum Commons',
      stops: names.map((name, i) => ({ id: ids[i], name, position: i + 1 }))
    }
  ]
};

/** Two trips: a morning one with a dwell, and an evening one that ends after midnight. */
function trip(id: number, times: [string, number, string, number][]) {
  const stops = times.map(([arr, arrDay, dep, depDay], i) => ({
    stop_order: i + 1,
    stop: stop(ids[i], names[i]),
    arrival_time: arr,
    arrival_day_offset: arrDay,
    departure_time: dep,
    departure_day_offset: depDay
  }));
  return {
    id,
    route_id: 10,
    line,
    direction: 'outbound',
    origin: stop(ids[0], names[0]),
    destination: stop(ids[3], names[3]),
    departure_time: times[0][2],
    departure_day_offset: 0,
    stops
  };
}
const trips = [
  trip(301001, [
    ['07:00', 0, '07:00', 0],
    ['07:05', 0, '07:05', 0],
    ['07:08', 0, '07:10', 0],
    ['07:20', 0, '07:20', 0]
  ]),
  trip(301007, [
    ['23:52', 0, '23:52', 0],
    ['23:58', 0, '23:58', 0],
    ['00:02', 1, '00:04', 1],
    ['00:14', 1, '00:14', 1]
  ])
];
const summary = (t: (typeof trips)[number]) => {
  const { stops, ...rest } = t;
  void stops;
  return rest;
};

async function mockApi(page: Page, { tripsFail = 0 } = {}) {
  let failures = tripsFail;
  await mockBrandAssets(page);
  await page.route('**/api/v1/stops', (r) => r.fulfill({ json: [] }));
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: [line] }));
  await page.route('**/api/v1/lines/1', (r) => r.fulfill({ json: detail }));
  await page.route('**/api/v1/lines/1/trips', (r) => {
    if (failures-- > 0) return r.fulfill({ status: 500, json: { error: 'boom' } });
    return r.fulfill({ json: trips.map(summary) });
  });
  await page.route('**/api/v1/trips/*', (r) => {
    const id = Number(new URL(r.request().url()).pathname.split('/').pop());
    const found = trips.find((t) => t.id === id);
    return found
      ? r.fulfill({ json: found })
      : r.fulfill({ status: 404, json: { error: 'Trip not found' } });
  });
}

/** 06:30 in Prague on a Monday: before both trips, so "next" and countdowns are deterministic. */
const MORNING = new Date('2026-11-23T06:30:00+01:00');

test('line page shows the hour grid, the next departure and re-flows for the boarding stop', async ({
  page
}) => {
  await page.clock.setFixedTime(MORNING);
  await mockApi(page);
  await page.goto('/lines/1');
  const grid = page.locator('table.grid');
  await expect(grid).toBeVisible();
  await expect(grid.getByRole('rowheader', { name: '07' })).toBeVisible();

  // Default boarding stop is the origin: the morning trip at 07:00 is the next departure.
  const next = page.locator('a.next');
  await expect(next).toContainText('07:00');
  await expect(next).toHaveAttribute('href', /\/trips\/301001\?od=12$/);
  const morning = grid.locator('a.min', { hasText: '00' }).first();
  await expect(morning).toHaveAttribute('href', /\/trips\/301001\?od=12$/);

  // Pick Syntax Square as the boarding stop: times re-flow without leaving the page.
  await page
    .getByRole('combobox', { name: 'Ze zastávky' })
    .selectOption({ label: 'Syntax Square' });
  await expect(grid.locator('a.min', { hasText: '10' })).toHaveAttribute(
    'href',
    /\/trips\/301001\?od=9$/
  );
  await expect(next).toContainText('07:10');
  // The evening trip leaves Syntax Square after midnight: hour 00 is read as the next day.
  await expect(grid.getByRole('rowheader', { name: '00' })).toBeVisible();
  await expect(grid.getByRole('link', { name: /00:04, následující den/ })).toHaveAttribute(
    'href',
    /\/trips\/301007\?od=9$/
  );
});

test('trip opened from a stop marks the boarding stop and shows dwell on the rail', async ({
  page
}) => {
  await page.clock.setFixedTime(MORNING);
  await mockApi(page);
  await page.goto('/trips/301001?od=9');
  await expect(page.getByRole('heading', { level: 1 })).toContainText(
    'Syntax Square 07:10 → Quantum Commons 07:20'
  );
  await expect(page.getByRole('status').filter({ hasText: 'Odjíždí' })).toHaveText(
    'Odjíždí za 40 min'
  );
  const route = page.getByRole('list', { name: 'Trasa spoje' }).getByRole('listitem');
  await expect(route).toHaveCount(4);
  const boarding = route.filter({ hasText: 'Syntax Square' });
  await expect(boarding).toContainText('nástup');
  await expect(boarding).toContainText('stojí 2 min');
  // Kernel Hub has no dwell, so it shows a single time and no wait.
  await expect(route.filter({ hasText: 'Kernel Hub' })).not.toContainText('stojí');
  await expect(route.filter({ hasText: 'Quantum Commons' })).toContainText('výstup');
  // The page ends with where to go next.
  await expect(
    page.getByRole('complementary', { name: 'Další cesta' }).getByRole('link', {
      name: 'Jízdní řád linky'
    })
  ).toHaveAttribute('href', /^\/lines\/1/);
});

test('unknown trip shows the not-found state', async ({ page }) => {
  await mockApi(page);
  await page.goto('/trips/999999');
  await expect(page.getByRole('heading', { name: 'Spoj nebyl nalezen' })).toBeVisible();
});

test('a failed trips load can be retried', async ({ page }) => {
  await page.clock.setFixedTime(MORNING);
  await mockApi(page, { tripsFail: 1 });
  await page.goto('/lines/1');
  await expect(
    page.getByRole('alert').getByRole('heading', { name: 'Spoje se nepodařilo načíst' })
  ).toBeVisible();
  await page.getByRole('button', { name: 'Zkusit znovu' }).click();
  await expect(page.locator('table.grid')).toBeVisible();
});
