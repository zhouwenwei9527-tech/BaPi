import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { login, openApp } from './helpers'

async function seriousViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  return results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
}

test.describe('authentication and shell', () => {
  test('about dialog stays inside the viewport', async ({ page }) => {
    await login(page)
    await page.getByRole('button', { name: '帮助' }).click()
    await page.getByRole('button', { name: '关于 BaPi' }).click()
    const modal = page.locator('.modal')
    await expect(modal).toBeVisible()
    const box = await modal.boundingBox()
    expect(box).not.toBeNull()
    if (!box) return
    const viewport = page.viewportSize()
    expect(box.y).toBeGreaterThanOrEqual(0)
    expect(box.y + box.height).toBeLessThanOrEqual((viewport?.height ?? 0) + 1)
  })

  test('login page renders without white screen and passes axe', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.login__title')
    await expect(page.locator('.login__title')).toHaveText('BaPi')
    await expect(page.locator('.login__card')).toBeVisible()
    const violations = await seriousViolations(page)
    expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  })

  test('admin desktop, palette and keyboard guard', async ({ page }) => {
    await login(page)
    await expect(page.locator('.dock')).toBeVisible()
    await expect(page.locator('.dock-item[aria-label="打开Agent 助手"]')).toBeVisible()
    await expect(page.locator('.widget__title', { hasText: '审批队列' })).toBeVisible()
    await expect(page.locator('.window')).toHaveCount(1)

    await page.keyboard.press('Meta+k')
    await page.waitForSelector('.palette')
    await page.fill('.palette__input', '数据范围')
    await page.waitForTimeout(700)
    const rows = page.locator('.palette__row')
    expect(await rows.count()).toBeGreaterThan(2)
    await page.keyboard.press('Escape')
    await expect(page.locator('.palette')).toHaveCount(0)

    const violations = await seriousViolations(page)
    expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  })

  test('role switch hides unauthorized entries and closes windows', async ({ page }) => {
    await login(page)
    await page.locator('.menubar__user').click()
    await page.locator('.menu-pop__item', { hasText: '只读访客' }).click()
    await page.waitForTimeout(1300)
    await expect(page.locator('.dock-item[aria-label="打开Agent 助手"]')).toHaveCount(0)
    await expect(page.locator('.dock-item[aria-label="打开审批队列"]')).toHaveCount(0)
    await expect(page.locator('.window')).toHaveCount(0)
    await expect(page.locator('.dock-item[aria-label="打开访达"]')).toBeVisible()
  })
})


test('dark theme passes axe on login and desktop', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('bapi.theme', 'dark'))
  await page.goto('/')
  await page.waitForSelector('.login__title')
  let violations = await seriousViolations(page)
  expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  await login(page)
  violations = await seriousViolations(page)
  expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
})


test('new data-entry apps pass axe', async ({ page }) => {
  await login(page)
  for (const name of ['用户管理', '数据流', '权限申请', '权限中心']) {
    await openApp(page, name)
    await page.waitForTimeout(900)
    const violations = await seriousViolations(page)
    expect(violations, `${name}: ${JSON.stringify(violations, null, 2)}`).toHaveLength(0)
    const close = page.locator('.window--focused .traffic__light--close')
    if (await close.count()) await close.click()
    await page.waitForTimeout(300)
  }
})
