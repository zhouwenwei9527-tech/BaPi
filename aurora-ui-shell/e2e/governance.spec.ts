import { test, expect, request as pwRequest } from '@playwright/test'
import { login, openApp } from './helpers'

async function tokenFor(username: string, password: string): Promise<string> {
  const api = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
  const response = await api.post('/api/auth/login', { data: { username, password } })
  expect(response.ok()).toBeTruthy()
  const token = (await response.json()).data.token
  await api.dispose()
  return token
}

test.describe('governance and scale', () => {
  test('user requests permission and another role rejects it', async ({ page }) => {
    const marker = `需要运营权限 ${Date.now()}`
    const guestApi = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const guestLogin = await guestApi.post('/api/auth/login', {
      data: { username: 'guest', password: 'guest123' }
    })
    const guestToken = (await guestLogin.json()).data.token
    const created = await guestApi.post('/api/permission-requests', {
      headers: { Authorization: `Bearer ${guestToken}` },
      data: { roleCode: 'ops', reason: marker }
    })
    expect(created.ok()).toBeTruthy()
    await guestApi.dispose()

    await login(page)
    await openApp(page, '权限申请')
    const card = page.locator('.access-request--approve', { hasText: marker })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '拒绝' }).click()
    await expect(page.locator('.toast', { hasText: '已拒绝申请' })).toBeVisible()

    const verify = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const me = await verify.post('/api/auth/login', {
      data: { username: 'guest', password: 'guest123' }
    })
    const verifyToken = (await me.json()).data.token
    const list = await verify.get('/api/permission-requests', {
      headers: { Authorization: `Bearer ${verifyToken}` }
    })
    const requests = (await list.json()).data as Array<{ reason: string; status: string }>
    expect(requests.find((request) => request.reason === marker)?.status).toBe('REJECTED')
    await verify.dispose()
  })

  test('self profile changes take effect directly without approval', async ({ page }) => {
    await login(page)
    await openApp(page, '用户管理')
    await page.getByRole('button', { name: '编辑我的资料' }).click()
    await page.getByLabel('显示名称').fill('林深·自改')
    await page.getByRole('button', { name: '保存（直接生效）' }).click()
    await expect(page.locator('.toast', { hasText: '无需审批' })).toBeVisible()
    await expect(page.locator('.menubar__user')).toContainText('林深·自改')
    await page.getByRole('button', { name: '编辑我的资料' }).click()
    await page.getByLabel('显示名称').fill('林深')
    await page.getByRole('button', { name: '保存（直接生效）' }).click()
    await page.waitForTimeout(500)
  })

  test('super admin can toggle a menu/data permission for another role', async ({ page }) => {
    await login(page)
    await openApp(page, '权限中心')
    await page.getByRole('button', { name: '编辑权限' }).click()

    const grant = page.getByRole('button', { name: '授予 guest knowledge:internal' })
    await grant.click()
    await expect(page.locator('.toast', { hasText: '已授予' })).toBeVisible()
    const afterGrant = await tokenFor('guest', 'guest123')
    const grantApi = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const grantMe = await grantApi.get('/api/auth/me', {
      headers: { Authorization: `Bearer ${afterGrant}` }
    })
    expect((await grantMe.json()).data.principal.permissions).toContain('knowledge:internal')
    await grantApi.dispose()

    const revoke = page.getByRole('button', { name: '撤销 guest knowledge:internal' })
    await revoke.click()
    await expect(page.locator('.toast', { hasText: '已撤销' })).toBeVisible()
    const afterRevoke = await tokenFor('guest', 'guest123')
    const revokeApi = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const revokeMe = await revokeApi.get('/api/auth/me', {
      headers: { Authorization: `Bearer ${afterRevoke}` }
    })
    expect((await revokeMe.json()).data.principal.permissions).not.toContain('knowledge:internal')
    await revokeApi.dispose()
  })

  test('user directory paginates instead of loading everyone', async ({ page }) => {
    await login(page)
    await openApp(page, '用户管理')
    await page.locator('.directory__pager select').selectOption('5')
    await page.waitForTimeout(600)
    await expect(page.locator('.directory__row')).toHaveCount(5)
    const firstPage = await page.locator('.directory__row').allTextContents()
    await expect(page.locator('.directory__pager')).toContainText('第 1 / 2 页')
    await page.getByRole('button', { name: '下一页' }).click()
    await page.waitForTimeout(600)
    await expect(page.locator('.directory__pager')).toContainText('第 2 / 2 页')
    await expect(page.locator('.directory__row')).toHaveCount(5)
    const secondPage = await page.locator('.directory__row').allTextContents()
    expect(secondPage).not.toEqual(firstPage)
  })
})
