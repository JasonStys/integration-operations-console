/**
 * File: playwright.config.ts
 * Purpose: Run actual compiled-UI/Java-H2 browser acceptance with owned loopback services.
 * Symbols: checkedPort, apiPort/uiPort and exported configuration; docs/code-index.md lists lines.
 * Variables: unique test account keys isolate records; workers=1 prevents scheduler fixture contention.
 */
import { defineConfig, devices } from '@playwright/test';

/** Validate explicit test-only ports before interpolating a local URL. */
function checkedPort(value: string | undefined, fallback: number): number {
  const port = value === undefined ? fallback : Number(value);
  if (
    (value !== undefined && !/^\d{4,5}$/u.test(value)) ||
    !Number.isSafeInteger(port) ||
    port < 1024 ||
    port > 65535
  )
    throw new Error('Browser fixture port must be 1024..65535');
  return port;
}
const apiPort = checkedPort(process.env.IOC_E2E_API_PORT, 18961);
const uiPort = checkedPort(process.env.IOC_E2E_UI_PORT, 18962);
if (apiPort === uiPort) throw new Error('Browser API and UI ports must differ');
const baseURL = `http://127.0.0.1:${uiPort}`;
export default defineConfig({
  testDir: './tests/browser',
  testMatch: '**/*.browser.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 30000,
  expect: { timeout: 5000 },
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  outputDir: '../backend/target/browser-results',
  reporter: [['list'], ['json', { outputFile: '../backend/target/browser-results.json' }]],
  use: { baseURL, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
  ],
  webServer: {
    command: 'node tests/browser-server.mjs',
    url: baseURL,
    timeout: 120000,
    reuseExistingServer: false,
  },
});
