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

test('lists lines from the API with routes, transfers and stops', async ({ page }) => {
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: lines }));
  await page.goto('/lines');

  await expect(page.getByRole('heading', { level: 1, name: 'Linky' })).toBeVisible();
  const express = page.getByRole('listitem').filter({ hasText: 'Campus Express' });
  await expect(express.getByText('Autobusová linka')).toBeVisible();
  await expect(express.getByText('Cejl', { exact: true })).toBeVisible();
  await expect(express.getByLabel('Linka X')).toBeVisible();
  await expect(
    page
      .getByRole('listitem')
      .filter({ hasText: 'Náhrada za tramvaj' })
      .getByText('Náhradní doprava linka')
  ).toBeVisible();

  const toggle = express.getByRole('button', { name: 'Zastávky na trase' });
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await expect(express.getByRole('link', { name: 'Kernel Hub' })).toHaveAttribute(
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
  await expect(page.getByText('Campus Express')).toBeVisible();
});
