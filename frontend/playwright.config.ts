import { defineConfig, devices } from '@playwright/test'

// End-to-end tests (npm run test:e2e): Chromium against the whole application
// (nginx + API + PostgreSQL) started in Docker by e2e/global-setup.ts with an
// isolated, throw-away database seeded with the demo data.
const port = process.env.MATRIZ_E2E_WEB_PORT || '5180'

export default defineConfig({
  testDir: './e2e',
  // *.e2e.ts, so Vitest (which picks up *.test.* and *.spec.*) never runs them.
  testMatch: '**/*.e2e.ts',
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    // Evidence only when a test fails.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
