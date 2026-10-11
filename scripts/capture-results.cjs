const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = process.env.PLAYWRIGHT_MODULE ? require(process.env.PLAYWRIGHT_MODULE) : require('playwright');

(async () => {
  const repository = path.resolve(process.argv[2] || process.cwd());
  const destination = path.join(repository, 'screenshots');
  fs.mkdirSync(destination, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1360, height: 1000 }, deviceScaleFactor: 1 });
    await page.goto(pathToFileURL(path.join(repository, 'evidence', 'report.html')).href);
    await page.locator('#results').screenshot({ path: path.join(destination, 'results.png') });
    await page.locator('#tests').screenshot({ path: path.join(destination, 'tests.png') });
    console.log(JSON.stringify({ repository: path.basename(repository), screenshots: ['results.png', 'tests.png'] }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
