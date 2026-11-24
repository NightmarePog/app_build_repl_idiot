import { expect, test, type Page } from '@playwright/test';
import { fileURLToPath } from 'node:url';
import { createStop } from './fixtures/stops';
import { mockBrandAssets } from './fixtures/assets';
import { mockNetwork } from './fixtures/network';

const stops = [
  createStop(12, {
    name: 'Cejl',
    image_url: 'http://127.0.0.1:3301/api/v1/stops-images/turingTerminal.png',
    wheelchair_accessible: true,
    has_shelter: true,
    has_ticket_machine: false
  }),
  createStop(13, {
    name: 'Ada Exchange',
    image_url: null,
    wheelchair_accessible: false,
    has_shelter: false,
    has_ticket_machine: true
  })
];

/** The search menu (Hledat, Ctrl K) holds the map's search text, views and filters. */
async function openMenu(page: Page) {
  const menu = page.getByRole('dialog', { name: 'Hledat v aplikaci' });
  if (!(await menu.isVisible()))
    await page
      .getByRole('banner')
      .getByRole('button', { name: /^Hledat/ })
      .click();
  await expect(menu).toBeVisible();
  return menu;
}
async function closeMenu(page: Page) {
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Hledat v aplikaci' })).toBeHidden();
}
const searchBox = (page: Page) =>
  page.getByRole('dialog', { name: 'Hledat v aplikaci' }).getByRole('combobox');

test.beforeEach(async ({ page }) => {
  await mockBrandAssets(page);
  await mockNetwork(page);
  await page.route('**/api/v1/stops', (route) => route.fulfill({ json: stops }));
  await page.route('**/api/v1/stops-images/turingTerminal.png', (route) =>
    route.fulfill({
      path: fileURLToPath(
        new URL(
          '../../backend/src/main/resources/seed/stopsImages/turingTerminal.png',
          import.meta.url
        )
      ),
      contentType: 'image/png'
    })
  );
});

test('lists stops with images and opens a real detail page', async ({ page }, testInfo) => {
  await page.route('**/api/v1/stops/12', (route) => route.fulfill({ json: stops[0] }));
  await page.goto('/stops');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávky: Všechny zastávky');
  await expect(
    page.getByRole('list', { name: 'Seznam zastávek' }).getByRole('listitem')
  ).toHaveCount(2);
  await page
    .getByRole('list', { name: 'Seznam zastávek' })
    .getByRole('button', { name: 'Cejl' })
    .click();
  const image = page.getByRole('img', { name: 'Zastávka Cejl' });
  await expect(image).toBeVisible();
  await expect
    .poll(() =>
      image.evaluate((element: HTMLImageElement) => element.complete && element.naturalWidth > 0)
    )
    .toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({
    path: `/tmp/tda-stops-${testInfo.project.name}.png`,
    fullPage: true,
    animations: 'allow'
  });
  await page.getByRole('link', { name: 'Detail zastávky Cejl' }).click();
  await expect(page).toHaveURL(/\/stops\/12\?stop=12$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Cejl');
  await expect(page.getByText('Nástup bez schodů')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.locator('html')).not.toHaveAttribute('data-route-transition', 'true');
  await expect(page.getByRole('complementary', { name: 'Zastávka Cejl' })).toHaveCSS(
    'opacity',
    '1'
  );
  await page.screenshot({
    path: `/tmp/tda-detail-${testInfo.project.name}.png`,
    fullPage: true,
    animations: 'allow'
  });
  await page.getByRole('link', { name: 'Všechny zastávky' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávky: Všechny zastávky');
  await expect(page.getByRole('article', { name: 'Vybraná zastávka Cejl' })).toBeVisible();
});

test('map finder selects stops from the list, keyboard and map', async ({ page }) => {
  await page.route('**/api/v1/stops/13', (route) => route.fulfill({ json: stops[1] }));
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/stops');
  const list = page.getByRole('list', { name: 'Seznam zastávek' });
  await expect(list.getByRole('listitem')).toHaveCount(2);
  // The map preselects a transfer stop; the list opens a stop's summary only when it is picked.
  const summary = page.getByRole('article', { name: 'Vybraná zastávka Ada Exchange' });
  await expect(summary).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Vybrat zastávku Ada Exchange' })).toHaveAttribute(
    'aria-pressed',
    'true'
  );

  await list.getByRole('button', { name: 'Cejl' }).click();
  await expect(page.getByRole('article', { name: 'Vybraná zastávka Cejl' })).toBeVisible();
  await expect(page).toHaveURL(/\?stop=12$/);
  // Transfer stops lead the list, so Ada Exchange sits above Cejl.
  await page.keyboard.press('k');
  await expect(summary.getByText('Jízdenky na místě')).toBeVisible();
  await expect(page).toHaveURL(/\?stop=13$/);
  await page.keyboard.press('j');
  await expect(page.getByRole('article', { name: 'Vybraná zastávka Cejl' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('article', { name: 'Vybraná zastávka Cejl' })).toBeVisible();

  const menu = await openMenu(page);
  await menu.getByRole('button', { name: 'Linka A', exact: true }).click();
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await expect(page).toHaveURL(/line=A/);
  await menu.getByRole('button', { name: 'Linka A', exact: true }).click();
  await menu.getByRole('button', { name: 'Jízdenky na místě', exact: true }).click();
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await searchBox(page).fill('cejl');
  await expect(page.getByRole('heading', { name: 'Takovou zastávku tu nemáme' })).toBeVisible();
  await closeMenu(page);
  await page.getByRole('button', { name: 'Zobrazit všechny zastávky' }).click();
  await expect(list.getByRole('listitem')).toHaveCount(2);

  await page.getByRole('button', { name: 'Vybrat zastávku Ada Exchange' }).click();
  await expect(summary).toBeVisible();
  await expect(page).toHaveURL(/stop=13/);
  await summary.getByRole('link', { name: /Více/ }).click();
  await expect(page).toHaveURL(/\/stops\/13/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Ada Exchange');
});

test('shows an understandable empty state', async ({ page }) => {
  await page.route('**/api/v1/stops', (route) => route.fulfill({ json: [] }));
  await page.goto('/stops');
  await expect(
    page.getByRole('main').getByRole('status').filter({ hasNotText: 'Status:' })
  ).toContainText('Zatím tu nejsou žádné zastávky');
  await expect(page.getByRole('list', { name: 'Seznam zastávek' })).toHaveCount(0);
});

for (const failure of ['http', 'invalid', 'network']) {
  test(`handles ${failure} failure and retries without showing an empty state`, async ({
    page
  }) => {
    await page.route('**/api/v1/stops', (route) =>
      failure === 'network'
        ? route.abort()
        : route.fulfill({ status: failure === 'http' ? 503 : 200, json: {} })
    );
    await page.goto('/stops');
    await expect(page.getByRole('alert').getByRole('heading')).toHaveText(
      'Zastávky se nepodařilo načíst'
    );
    await expect(page.getByText('Zatím tu nejsou žádné zastávky')).toHaveCount(0);
    await page.route('**/api/v1/stops', (route) => route.fulfill({ json: stops }));
    await page.getByRole('button', { name: 'Zkusit znovu' }).click();
    await expect(page.getByRole('link', { name: 'Detail zastávky Cejl' })).toBeVisible();
  });
}

test('shows loading feedback while waiting for the API', async ({ page }) => {
  let release!: () => void;
  const pending = new Promise<void>((resolve) => (release = resolve));
  await page.route('**/api/v1/stops', async (route) => {
    await pending;
    await route.fulfill({ json: stops });
  });
  await page.goto('/stops');
  await expect(
    page.getByRole('main').getByRole('status').filter({ hasNotText: 'Status:' })
  ).toHaveText('Načítáme zastávky…');
  release();
  await expect(page.getByRole('link', { name: 'Detail zastávky Cejl' })).toBeVisible();
});

test('broken images retain a usable link and layout fits narrow screens', async ({ page }) => {
  await page.route('**/api/v1/stops-images/turingTerminal.png', (route) => route.abort());
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/stops?stop=12');
  await expect(
    page
      .getByRole('article', { name: 'Vybraná zastávka Cejl' })
      .getByText('Obrázek zastávky není k dispozici')
  ).toBeVisible();
  const card = page.getByRole('link', { name: 'Detail zastávky Cejl' });
  await card.focus();
  await expect(card).toBeFocused();
  expect(await card.evaluate((el) => getComputedStyle(el).outlineStyle)).toBe('solid');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('images load directly from the backend without local derivatives', async ({ page }) => {
  const localImages: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/images/')) localImages.push(request.url());
  });
  await page.goto('/stops?stop=12');
  const image = page.getByRole('img', { name: 'Zastávka Cejl' });
  await expect
    .poll(() =>
      image.evaluate(
        (element: HTMLImageElement) =>
          element.complete &&
          element.naturalWidth > 0 &&
          new URL(element.currentSrc).pathname === '/api/v1/stops-images/turingTerminal.png'
      )
    )
    .toBe(true);
  expect(localImages).toEqual([]);
});

test('search ignores accents and filter combinations work from the keyboard', async ({ page }) => {
  await page.route('**/api/v1/stops/12', (route) => route.fulfill({ json: stops[0] }));
  await page.route('**/api/v1/stops', (route) =>
    route.fulfill({
      json: [
        ...stops,
        {
          ...stops[0],
          id: 14,
          name: 'Žižkova',
          image_url: null,
          has_shelter: false
        }
      ]
    })
  );
  await page.goto('/stops');
  const list = page.getByRole('list', { name: 'Seznam zastávek' });
  await expect(list.getByRole('listitem')).toHaveCount(3);

  const menu = await openMenu(page);
  await page.keyboard.type('ziz');
  await expect(page.getByRole('link', { name: 'Detail zastávky Žižkova' })).toBeVisible();
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await expect(page).toHaveURL(/q=ziz/);

  await searchBox(page).fill('');
  const accessible = menu.getByRole('button', { name: 'Nástup bez schodů', exact: true });
  await accessible.focus();
  await page.keyboard.press('Space');
  await expect(accessible).toHaveAttribute('aria-pressed', 'true');
  await expect(list.getByRole('listitem')).toHaveCount(2);

  const shelter = menu.getByRole('button', { name: 'Střecha nad hlavou', exact: true });
  await shelter.focus();
  await page.keyboard.press('Space');
  await expect(shelter).toHaveAttribute('aria-pressed', 'true');
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await expect(page.getByRole('link', { name: 'Detail zastávky Cejl' })).toBeVisible();

  await searchBox(page).fill('cejl');
  await closeMenu(page);
  const detailLink = page.getByRole('link', { name: 'Detail zastávky Cejl' });
  await expect(detailLink).toHaveAttribute('href', /q=cejl/);
  await detailLink.click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Cejl');
  await page.getByRole('link', { name: 'Všechny zastávky' }).click();
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await openMenu(page);
  await expect(searchBox(page)).toHaveValue('cejl');
  await expect(accessible).toHaveAttribute('aria-pressed', 'true');
  await expect(shelter).toHaveAttribute('aria-pressed', 'true');
  await closeMenu(page);

  await detailLink.click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Cejl');
  await page.goBack();
  await expect(list.getByRole('listitem')).toHaveCount(1);
  await openMenu(page);
  await expect(searchBox(page)).toHaveValue('cejl');
  await expect(accessible).toHaveAttribute('aria-pressed', 'true');
  await expect(shelter).toHaveAttribute('aria-pressed', 'true');
  await closeMenu(page);

  const reset = page.getByRole('button', { name: 'Zrušit filtry', exact: true });
  await reset.focus();
  await page.keyboard.press('Enter');
  await expect(list.getByRole('listitem')).toHaveCount(3);
  await expect(page).toHaveURL(/\/stops$/);
  await openMenu(page);
  await expect(searchBox(page)).toHaveValue('');
  await expect(accessible).toHaveAttribute('aria-pressed', 'false');
  await expect(shelter).toHaveAttribute('aria-pressed', 'false');
});

test('deep-linked filters survive reload and detail navigation', async ({ page }) => {
  await page.route('**/api/v1/stops/12', (route) => route.fulfill({ json: stops[0] }));
  await page.goto('/stops?q=cejl&filter=accessible%2Cshelter');
  const list = page.getByRole('list', { name: 'Seznam zastávek' });
  const menu = page.getByRole('dialog', { name: 'Hledat v aplikaci' });
  const accessible = menu.getByRole('button', { name: 'Nástup bez schodů', exact: true });
  const check = async () => {
    await expect(list.getByRole('listitem')).toHaveCount(1);
    await openMenu(page);
    await expect(searchBox(page)).toHaveValue('cejl');
    await expect(accessible).toHaveAttribute('aria-pressed', 'true');
    await closeMenu(page);
  };
  await check();
  await page.reload();
  await check();

  await page.getByRole('link', { name: 'Detail zastávky Cejl' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Cejl');
  await page.getByRole('link', { name: 'Všechny zastávky' }).click();
  await check();

  await page.getByRole('link', { name: 'Detail zastávky Cejl' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Cejl');
  await page.goBack();
  await check();
});

test('detail keeps the selected image while validating the stop and removes a stale preview on 404', async ({
  page
}) => {
  let release!: () => void;
  const pending = new Promise<void>((resolve) => (release = resolve));
  await page.route('**/api/v1/stops/12', async (route) => {
    await pending;
    await route.fulfill({ status: 404, json: {} });
  });
  await page.goto('/stops');
  const card = page.getByRole('link', { name: 'Detail zastávky Cejl' });
  await expect(card).toBeVisible();

  try {
    await card.click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávka: Cejl');
    await expect(page.getByRole('img', { name: 'Zastávka Cejl' })).toBeVisible();
    await expect(page.locator('main .stop-image-frame')).not.toHaveCSS(
      'view-transition-name',
      'none'
    );
    await expect(page.getByText('Načítáme detail zastávky…')).toHaveCount(0);
  } finally {
    release();
  }

  await expect(page.getByRole('alert').getByRole('heading')).toHaveText('Zastávka nebyla nalezena');
  await expect(page.getByRole('img', { name: 'Zastávka Cejl' })).toHaveCount(0);
  await page.getByRole('link', { name: 'Všechny zastávky' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávky: Všechny zastávky');
});

test('unmatched searches can be reset without losing unrelated URL parameters', async ({
  page
}) => {
  await page.goto('/stops?source=shared&q=nenalezena');
  await expect(page.getByRole('heading', { name: 'Takovou zastávku tu nemáme' })).toBeVisible();
  await expect(page.getByRole('list', { name: 'Seznam zastávek' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Zrušit filtry', exact: true }).click();
  await openMenu(page);
  await expect(searchBox(page)).toHaveValue('');
  await closeMenu(page);
  await expect(
    page.getByRole('list', { name: 'Seznam zastávek' }).getByRole('listitem')
  ).toHaveCount(2);
  await expect(page).toHaveURL(/\/stops\?source=shared$/);
});

test('a missing stop has a clear message and a way back', async ({ page }) => {
  await page.route('**/api/v1/stops/999', (route) => route.fulfill({ status: 404, json: {} }));
  await page.goto('/stops/999');
  await expect(page.getByRole('alert').getByRole('heading')).toHaveText('Zastávka nebyla nalezena');
  await expect(page.getByRole('link', { name: 'Všechny zastávky' })).toHaveAttribute(
    'href',
    '/stops'
  );
});
