import { expect, test } from '@playwright/test';
import { mockBrandAssets } from './fixtures/assets';
import search from './fixtures/connections-9-15.json' with { type: 'json' };

const stops = [
  { id: 9, name: 'Syntax Square' },
  { id: 15, name: 'Quantum Commons' }
];
const key = '0-0706-202001.9-301002.8';

test.beforeEach(async ({ page }) => {
  await mockBrandAssets(page);
  await page.route('**/api/v1/stops', (r) => r.fulfill({ json: stops }));
  await page.route('**/api/v1/lines', (r) => r.fulfill({ json: [] }));
});

test('lists connections with a transfer and opens the detail', async ({ page }) => {
  await page.route('**/api/v1/connections?*', (r) => r.fulfill({ json: search }));
  await page.goto('/spojeni?z=9&do=15&cas=07:00');

  await expect(page.getByRole('heading', { level: 2, name: /Odjezd po 07:00/ })).toBeVisible();
  const card = page.locator(`[id="c-${key}-summary"]`);
  await expect(card).toContainText('07:06');
  await expect(card).toContainText('Kernel Hub');
  await card.click();
  await expect(page).toHaveURL(new RegExp(`detail=${key}`));

  const trip = page.getByRole('link', { name: /Spoj Bus B02/ });
  await expect(trip).toHaveAttribute('href', /\/trips\/202001\?od=9&do=8/);
  await expect(page.locator(`[id="c-${key}-detail"] a[href="/stops/8"]`).first()).toBeVisible();
});

test('shows the empty state', async ({ page }) => {
  await page.route('**/api/v1/connections?*', (r) =>
    r.fulfill({ json: { ...search, connections: [] } })
  );
  await page.goto('/spojeni?z=9&do=15');
  await expect(page.getByRole('heading', { name: 'Žádné spojení' })).toBeVisible();
});

test('reports a failed search', async ({ page }) => {
  await page.route('**/api/v1/connections?*', (r) =>
    r.fulfill({ status: 500, json: { error: 'boom' } })
  );
  await page.goto('/spojeni?z=9&do=15');
  await expect(page.getByRole('heading', { name: 'Spojení se nepodařilo vyhledat' })).toBeVisible();
});
