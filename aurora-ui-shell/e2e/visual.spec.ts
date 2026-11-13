import { test } from '@playwright/test'
import { login, openApp, closeFocusedWindow } from './helpers'

test.setTimeout(120_000)

test('capture desktop and core apps', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('bapi.theme', 'dark'))
  await login(page)
  await page.screenshot({ path: 'screenshots/e2e-desktop.png', fullPage: false })
  for (const [name, file] of [
    ['用户管理', 'e2e-directory'],
    ['权限申请', 'e2e-access'],
    ['数据流', 'e2e-guide'],
    ['审批队列', 'e2e-approvals'],
    ['审计回放', 'e2e-audit'],
    ['Tool 注册表', 'e2e-tools'],
    ['插件', 'e2e-plugins'],
    ['权限中心', 'e2e-rbac'],
    ['访达', 'e2e-finder'],
    ['控制中心', 'e2e-glass']
  ] as const) {
    await openApp(page, name)
    await page.waitForTimeout(700)
    await page.screenshot({ path: `screenshots/${file}.png` })
    await closeFocusedWindow(page)
  }
})
