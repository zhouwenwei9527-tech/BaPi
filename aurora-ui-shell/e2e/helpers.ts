import type { Page } from '@playwright/test'

export async function login(page: Page, username = 'admin', password = 'admin123') {
  await page.goto('/')
  await page.waitForSelector('.login')
  await page.fill('#login-user', username)
  await page.fill('#login-pass', password)
  await page.click('.login__submit')
  await page.waitForSelector('.dock', { timeout: 20_000 })
  await page.waitForTimeout(900)
}

export async function openApp(page: Page, name: string) {
  const dock = page.getByRole('button', { name: `打开${name}` }).first()
  await dock.click()
  await page.waitForTimeout(500)
}

export async function closeFocusedWindow(page: Page) {
  const close = page.locator('.window--focused .traffic__light--close')
  if (await close.count()) {
    await close.click()
    await page.waitForTimeout(250)
  }
}
