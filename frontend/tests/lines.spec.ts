import { expect, test } from '@playwright/test';
import { mockBrandAssets } from './fixtures/assets';

const lines = [
  { id: 1, number: '7', name: 'Campus Express', type: 'bus', color: '#0070BB' },
  { id: 2, number: 'X', name: 'Náhrada za tramvaj', type: 'replacement', color: '#91F5AD' }
];

const route = (id: number, bound: 'outbound' | 'inbound', names: string[], ids: number[]) => ({
  id,
  bound,
  direction: names[names.length - 1],
  stops: names.map((name, index) => ({ id: ids[index], name, position: index + 1 }))
});

const details = {
  1: {
    ...lines[0],
    routes: [
      route(10, 'outbound', ['Cejl', 'Kernel Hub', 'Campus Gate'], [12, 8, 11]),
      route(11, 'inbound', ['Campus Gate', 'Kernel Hub', 'Cejl'], [11, 8, 12])
    ]
  },
  2: { ...lines[1], routes: [route(20, 'outbound', ['Kernel Hub', 'Pixel Park'], [8, 3])] }
};

test.beforeEach(async ({ page }) => {
  await mockBrandAssets(page);
  await page.route('**/api/v1/stops', (r) => r.fulfill({ json: [] }));
  await page.route('**/api/v1/lines/*', (r) => {
    const id = Number(new URL(r.request().url()).pathname.split('/').pop());
    return r.fulfill({ json: details[id as 1 | 2] });
  });
});

test('lists lines from the API with route strips, transfers and interchanges', async ({
  page,
  isMobile
}) => {
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: lines }));
  await page.goto('/lines');

  await expect(page.getByRole('heading', { level: 1, name: 'Linky' })).toBeVisible();
  await expect(
    page.getByRole('heading', { level: 2, name: 'Linka 7: Campus Express' })
  ).toBeVisible();
  const express = page.getByRole('listitem').filter({ hasText: 'Campus Express' }).first();
  await expect(express.getByText('autobus', { exact: true })).toBeVisible();
  await expect(express.getByRole('link', { name: 'Víc informací o lince 7' })).toHaveAttribute(
    'href',
    '/lines/1'
  );
  if (isMobile) {
    // Phones get the interchanges in words instead of the strip diagram.
    await expect(express.getByText(/přes\s+Kernel Hub/)).toBeVisible();
  } else {
    const strip = express.getByRole('list', { name: 'Trasa linky 7' });
    await expect(strip.getByRole('link', { name: 'Kernel Hub' })).toHaveAttribute(
      'href',
      '/stops/8'
    );
    await expect(strip.getByText('přestup na linky X')).toBeAttached();
  }
  await expect(
    page.getByRole('listitem').filter({ hasText: 'Náhrada za tramvaj' }).first()
  ).toContainText('náhradní doprava');

  const interchanges = page.getByRole('region', { name: 'Přestupní zastávky' });
  await expect(interchanges.getByRole('link', { name: /Kernel Hub/ })).toHaveAttribute(
    'href',
    '/stops/8'
  );
});

test('explains an empty network', async ({ page }) => {
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: [] }));
  await page.goto('/lines');
  await expect(page.getByText('Zatím nejsou evidované žádné linky')).toBeVisible();
});

test('reports a failed load and retries', async ({ page }) => {
  let calls = 0;
  await page.route('**/api/v1/lines', (r) =>
    ++calls === 1 ? r.fulfill({ status: 500, json: { error: 'boom' } }) : r.fulfill({ json: lines })
  );
  await page.goto('/lines');
  await expect(page.getByRole('alert')).toHaveText('Linky se nepodařilo načíst');
  await page.getByRole('button', { name: 'Zkusit znovu' }).click();
  await expect(page.getByRole('main').getByText('Campus Express').first()).toBeVisible();
});

test('line detail shows the route, transfers and switches direction', async ({ page }) => {
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: lines }));
  await page.goto('/lines/1');

  await expect(
    page.getByRole('heading', { level: 1, name: 'Linka 7: Campus Express' })
  ).toBeVisible();
  const diagram = page.getByRole('list', { name: 'Zastávky směr Campus Gate' });
  await expect(diagram.getByRole('link', { name: 'Cejl' })).toHaveAttribute('href', '/stops/12');
  await expect(diagram.getByRole('link', { name: 'linka X' })).toHaveAttribute(
    'href',
    '/lines/2#stop-8'
  );

  const directions = page.getByRole('navigation', { name: 'Směr jízdy' });
  await expect(directions.getByRole('link', { name: 'směr Campus Gate' })).toHaveAttribute(
    'aria-current',
    'page'
  );
  await directions.getByRole('link', { name: 'směr Cejl' }).click();
  await expect(page).toHaveURL(/\/lines\/1\?smer=inbound$/);
  await expect(page.getByRole('list', { name: 'Zastávky směr Cejl' })).toBeVisible();
});

test('line detail reports a missing line with a way back', async ({ page }) => {
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: lines }));
  await page.route('**/api/v1/lines/99', (r) =>
    r.fulfill({ status: 404, json: { error: 'Line not found' } })
  );
  await page.goto('/lines/99');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Linka nebyla nalezena' })
  ).toBeVisible();
  await expect(page.getByRole('main').getByRole('link', { name: 'Přehled linek' })).toHaveAttribute(
    'href',
    '/lines'
  );
});
