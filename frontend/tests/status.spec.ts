import { createStop } from './fixtures/stops';
import { mockBrandAssets } from './fixtures/assets';
import { mockNetwork } from './fixtures/network';

const featuredStop = createStop(42, {
  image_url: 'http://127.0.0.1:3301/api/v1/stops-images/turingTerminal.png'
});
import { expect, test, type Page } from '@playwright/test';
import { fileURLToPath } from 'node:url';

/** The footer line that reports whether the timetable API is healthy. */
const dataStatus = (page: Page) =>
  page.getByRole('status').filter({ hasText: /^Data jízdních řádů:/ });

test.beforeEach(async ({ page }) => {
  await mockBrandAssets(page);
  await mockNetwork(page);
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
  await page.route('**/api/v1/stops', (route) => route.fulfill({ json: [featuredStop] }));
  await page.route('**/api/v1/health', (route) => route.fulfill({ json: { status: 'ok' } }));
  await page.route('**/api/v1/team', (route) =>
    route.fulfill({ json: { name: 'Database team', members: ['Žofie', 'Jan'] } })
  );
});

test('homepage fetches health and displays OK', async ({ page }, testInfo) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Autobusy po kampusu');
  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
  await expect(page.getByRole('link', { name: 'Otevřít mapu', exact: true })).toHaveAttribute(
    'href',
    '/stops'
  );
  await expect(page.getByRole('link', { name: 'Seznam linek' })).toHaveAttribute('href', '/#lines');
  await expect(
    page.getByRole('main').getByRole('heading', { level: 2, name: 'Linky' })
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({
    path: `/tmp/tda-home-${testInfo.project.name}.png`,
    fullPage: true,
    animations: 'allow'
  });
});

// The theme now follows the system preference until the user picks one (light by default).
test('system theme is the default and keyboard theme choice persists', async ({
  page
}, testInfo) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  const root = page.locator('html');
  await expect(root).not.toHaveClass(/dark/);

  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
  const darkSwitch = page.getByRole('button', { name: 'Přepnout na tmavý režim' });
  await darkSwitch.focus();
  await page.keyboard.press('Enter');
  await expect(root).toHaveClass(/dark/);
  await expect(page.getByRole('button', { name: 'Přepnout na světlý režim' })).toBeVisible();
  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
  await page.reload();
  await expect(root).toHaveClass(/dark/);

  await page.getByRole('button', { name: 'Přepnout na světlý režim' }).click();
  await expect(root).not.toHaveClass(/dark/);
  await page.screenshot({
    path: `/tmp/tda-home-light-${testInfo.project.name}.png`,
    fullPage: true,
    animations: 'allow'
  });
  // An explicit light choice wins over a dark system preference.
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.reload();
  await expect(root).not.toHaveClass(/dark/);
  await expect(page.getByRole('button', { name: 'Přepnout na tmavý režim' })).toBeVisible();
});

test('dark system preference applies without a stored choice', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('/');
  await expect(page.locator('html')).toHaveClass(/dark/);
});

test('command palette finds a stop by keyboard', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/');
  // The status is fetched client-side, so it also signals that the page is hydrated.
  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
  await page.keyboard.press('ControlOrMeta+k');
  const dialog = page.getByRole('dialog', { name: 'Hledat v aplikaci' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('combobox').fill(featuredStop.name);
  await expect(dialog.getByRole('option').first()).toContainText(featuredStop.name);
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
});

test('reduced motion keeps the small-screen homepage readable and keyboard accessible', async ({
  page
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
  await expect(page.getByLabel('Autoři')).toContainText('Žofie · Jan');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(
    await page.evaluate(() =>
      document.getAnimations().some((animation) => {
        const timing = animation.effect?.getComputedTiming();
        return animation.playState === 'running' && Number(timing?.activeDuration) > 100;
      })
    )
  ).toBe(false);

  const skipLink = page.getByRole('link', { name: 'Přejít na obsah' });
  await page.keyboard.press('Tab');
  await expect(skipLink).toBeFocused();
  await expect(skipLink).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/#main$/);
});

for (const failure of ['http', 'invalid', 'wrong-value', 'network']) {
  test(`health ${failure} failure is displayed`, async ({ page }) => {
    await page.route('**/api/v1/health', (route) =>
      failure === 'network'
        ? route.abort()
        : route.fulfill({
            status: failure === 'http' ? 503 : 200,
            json:
              failure === 'http'
                ? { status: 'ok' }
                : failure === 'wrong-value'
                  ? { status: 'UP' }
                  : { unexpected: true }
          })
    );
    await page.goto('/');
    await expect(dataStatus(page)).toHaveText('Data jízdních řádů: nedostupná');
  });
}

test('authors show data returned by the team API', async ({ page }) => {
  await page.route('**/api/v1/health', (route) => route.fulfill({ json: { status: 'ok' } }));
  await page.goto('/');
  const authors = page.getByLabel('Autoři');
  await expect(authors).toContainText('Database team');
  await expect(authors).toContainText('Žofie · Jan');
  await page.route('**/api/v1/team', (route) =>
    route.fulfill({ json: { name: 'Changed team', members: ['Eva'] } })
  );
  await page.reload();
  await expect(authors).toContainText('Changed team');
  await expect(authors).toContainText('Eva');
  await expect(authors).not.toContainText('Žofie');
});

for (const invalid of [false, true]) {
  test(`team ${invalid ? 'invalid response' : 'HTTP failure'} shows fallback`, async ({ page }) => {
    await page.route('**/api/v1/team', (route) =>
      route.fulfill({ status: invalid ? 200 : 503, json: {} })
    );
    await page.goto('/');
    await expect(page.getByLabel('Autoři')).toHaveText('Údaje o týmu nejsou k dispozici.');
  });
}

test('landing planner sends the chosen stops and time to the connection search', async ({
  page
}) => {
  const target = createStop(43, { name: 'Quantum Commons' });
  await page.route('**/api/v1/stops', (route) => route.fulfill({ json: [featuredStop, target] }));
  await page.goto('/');
  const form = page.getByRole('form', { name: 'Vyhledat spojení' });
  await form.getByRole('combobox', { name: 'Odkud' }).fill(featuredStop.name.slice(0, 4));
  await form.getByRole('option', { name: featuredStop.name }).click();
  await form.getByRole('combobox', { name: 'Kam' }).fill('Quantum');
  await form.getByRole('option', { name: 'Quantum Commons' }).click();
  await form.getByLabel('Čas odjezdu').fill('07:30');
  await form.getByRole('button', { name: 'Vyhledat spojení' }).click();
  await expect(page).toHaveURL(/\/spojeni\?/);
  const params = new URL(page.url()).searchParams;
  expect(Object.fromEntries(params)).toEqual({ z: '42', do: '43', cas: '07:30' });
});

test('landing map preview and primary action lead to the stop map', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/');
  await page.getByRole('link', { name: 'Otevřít mapu zastávek' }).click();
  await expect(page).toHaveURL(/\/stops$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávky: Všechny zastávky');
  await page.getByRole('link', { name: 'Úvod' }).first().click();
  await page.getByRole('link', { name: 'Otevřít mapu', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Zastávky: Všechny zastávky');
});

test('landing stays usable when stops fail to load', async ({ page }) => {
  await page.route('**/api/v1/stops', (route) => route.fulfill({ status: 503, json: [] }));
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Autobusy po kampusu');
  await expect(page.getByRole('link', { name: 'Otevřít mapu', exact: true })).toBeVisible();
  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
});

test('header links go straight to stops and lines', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/');
  const nav = page.getByRole('navigation', { name: 'Hlavní navigace' });
  await expect(nav.getByRole('button')).toHaveCount(0);
  await nav.getByRole('link', { name: 'Linky', exact: true }).click();
  await expect(page).toHaveURL(/\/lines$/);
  await nav.getByRole('link', { name: 'Zastávky', exact: true }).click();
  await expect(page).toHaveURL(/\/stops$/);
});

test('footer links open contact and terms', async ({ page }, testInfo) => {
  await page.goto('/');
  await expect(dataStatus(page)).toHaveText('Data jízdních řádů: v provozu');
  const footer = page.getByRole('contentinfo', { name: 'Patička webu' });
  await expect(footer.getByRole('link', { name: 'GitHub' })).toHaveAttribute(
    'href',
    'https://github.com/NightmarePog/2idiotz'
  );
  await footer.screenshot({ path: `/tmp/tda-footer-${testInfo.project.name}.png` });
  await footer.getByRole('link', { name: 'Kontakt' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Kontakt: Kontakt na tým');
  await expect(page.getByRole('link', { name: 'Napsat týmu' })).toHaveAttribute(
    'href',
    'https://github.com/NightmarePog/2idiotz/issues/new'
  );
  await footer.getByRole('link', { name: 'Podmínky používání' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Podmínky: Podmínky používání');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
