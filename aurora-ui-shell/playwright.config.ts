import { defineConfig } from '@playwright/test'

const executablePath = process.env.PW_EXECUTABLE
  ?? '/Users/zhouwenwei/Library/Caches/ms-playwright/chromium-1254/chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    headless: true,
    viewport: { width: 1440, height: 900 },
    launchOptions: {
      executablePath,
      args: [
        '--disable-background-timer-throttling',
        '--disable-renderer-backgrounding',
        '--disable-backgrounding-occluded-windows',
        '--disable-frame-rate-limit'
      ]
    },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
})
