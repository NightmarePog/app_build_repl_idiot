import { chromium } from '@playwright/test';
const [url, out, w] = process.argv.slice(2);
const b = await chromium.launch({ executablePath: process.env.CHROME });
const p = await b.newPage({ viewport: { width: +(w||1280), height: 900 } });
await p.goto(url, { waitUntil: 'networkidle' }); await p.screenshot({ path: out, fullPage: true }); await b.close();
