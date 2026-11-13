import { test, expect } from '@playwright/test'
import { login, openApp } from './helpers'

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64'
)

const DEFAULT_DOCK = [
  'finder', 'directory', 'agent', 'approvals', 'audit', 'tools',
  'plugins', 'access', 'guide', 'rbac', 'glass'
]

test.describe('wallpaper and taskbar customization', () => {
  const serverWallpaper = (page: import('@playwright/test').Page) => page.evaluate(async () => {
    const token = localStorage.getItem('bapi.token')
    const response = await fetch('/api/preferences/wallpaper', {
      headers: { Authorization: `Bearer ${token}` }
    })
    return (await response.json()).data
  })

  const serverDock = (page: import('@playwright/test').Page) => page.evaluate(async () => {
    const token = localStorage.getItem('bapi.token')
    const response = await fetch('/api/preferences/dock', {
      headers: { Authorization: `Bearer ${token}` }
    })
    return (await response.json()).data
  })

  const setDock = (page: import('@playwright/test').Page, apps: string[]) =>
    page.evaluate(async (next) => {
      const token = localStorage.getItem('bapi.token')
      await fetch('/api/preferences/dock', {
        method: 'PUT',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ apps: next })
      })
    }, apps)

  test('wallpaper is stored in the account and restored on reload', async ({ page }) => {
    await login(page)
    await page.evaluate(async () => {
      const token = localStorage.getItem('bapi.token')
      await fetch('/api/preferences/wallpaper', {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` }
      })
    })
    await page.reload()
    await expect(page.locator('.dock')).toBeVisible()
    await openApp(page, '控制中心')
    await page.locator('.wallpaper-picker .wallpaper-swatch[data-preset="dawn"]').click()
    expect(await page.evaluate(() => document.documentElement.dataset.wallpaper)).toBe('dawn')
    await expect.poll(async () => (await serverWallpaper(page)).presetId).toBe('dawn')
    await page.locator('.wallpaper-picker .wallpaper-swatch[data-preset="aurora"]').click()
    await expect.poll(async () => (await serverWallpaper(page)).presetId).toBe('aurora')
    await page.locator('.window--focused .traffic__light--close').click()

    await openApp(page, 'Agent 助手')
    await page.locator('.composer input[type=file]').setInputFiles({
      name: 'wallpaper.png', mimeType: 'image/png', buffer: PNG
    })
    await page.getByLabel('Agent 输入框').fill('更换壁纸')
    await page.getByLabel('Agent 输入框').press('Enter')
    await expect(page.locator('.toast', { hasText: '壁纸已更换' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.dataset.wallpaper)).toBe('custom')

    const stored = await serverWallpaper(page)
    expect(stored.kind).toBe('IMAGE')
    expect(stored.provider).toBe('minio')
    expect(stored.url).toContain('/wallpapers/')

    await page.reload()
    await expect(page.locator('.dock')).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.dataset.wallpaper))
      .toBe('custom')

    await openApp(page, '控制中心')
    await expect(page.locator('.wallpaper-history__item')).toHaveCount(3)
    await page.getByTitle('切换到 晨雾').click()
    await expect.poll(() => page.evaluate(() => document.documentElement.dataset.wallpaper))
      .toBe('dawn')
    await expect.poll(async () => (await serverWallpaper(page)).presetId).toBe('dawn')

    await page.evaluate(async () => {
      const token = localStorage.getItem('bapi.token')
      await fetch('/api/preferences/wallpaper', {
        method: 'DELETE', headers: { Authorization: `Bearer ${token}` }
      })
    })
  })

  test('app library cannot place apps on the desktop; dock order persists in the account', async ({ page }) => {
    await login(page)
    await setDock(page, DEFAULT_DOCK)
    await page.reload()
    await expect(page.locator('.dock')).toBeVisible()
    await page.getByRole('button', { name: '打开应用库' }).click()
    await expect(page.getByRole('button', { name: '放到桌面' })).toHaveCount(0)

    const auditRow = page.locator('.app-library__row', { hasText: '审计回放' })
    await auditRow.getByRole('button', { name: '在程序坞' }).click()
    await page.getByRole('button', { name: '关闭应用库' }).click()
    await expect(page.locator('.dock-item[aria-label="打开审计回放"]')).toHaveCount(0)

    await page.getByRole('button', { name: '打开应用库' }).click()
    await page.locator('.app-library__row', { hasText: '审计回放' })
      .getByRole('button', { name: '加入程序坞' }).click()
    const finderRow = page.locator('.app-library__row', { hasText: '访达' })
    await expect(finderRow.getByRole('button', { name: '在程序坞' })).toBeDisabled()
    await page.getByRole('button', { name: '关闭应用库' }).click()
    await expect(page.locator('.dock-item[aria-label="打开审计回放"]')).toBeVisible()

    const audit = page.locator('.dock-item[aria-label="打开审计回放"]')
    const directory = page.locator('.dock-item[aria-label="打开用户管理"]')
    const from = await audit.boundingBox()
    const to = await directory.boundingBox()
    expect(from).not.toBeNull()
    expect(to).not.toBeNull()
    if (!from || !to) return

    await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
    await page.mouse.down()
    await page.waitForTimeout(450)
    await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 12 })
    await page.mouse.up()

    await expect.poll(async () => (await serverDock(page)).apps[1]).toBe('audit')
    await page.reload()
    await expect(page.locator('.dock')).toBeVisible()
    await expect.poll(async () =>
      page.locator('.dock-item[aria-label^="打开"]').nth(1).getAttribute('aria-label'))
      .toBe('打开审计回放')

    await page.getByRole('button', { name: '打开应用库' }).click()
    await page.getByRole('button', { name: '重置布局' }).click()
    await page.getByRole('button', { name: '关闭应用库' }).click()
    await expect.poll(async () => (await serverDock(page)).apps[1]).toBe('directory')
  })
})
