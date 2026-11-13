import { test, expect, request as pwRequest } from '@playwright/test'
import { login, openApp } from './helpers'

test.describe('permission-first tool flows', () => {
  test('agent READ flows through gateway and reports steps', async ({ page }) => {
    await login(page)
    await openApp(page, 'Agent 助手')
    const newChat = page.getByRole('button', { name: '新对话' })
    if (await newChat.count()) await newChat.first().click()
    await page.locator('.suggestion', { hasText: '查询本月新增用户' }).click()
    const fold = page.locator('.agent-steps-fold').last()
    await expect(fold).toBeVisible()
    await fold.locator('summary').click()
    await expect(page.locator('.agent-step', { hasText: '权限校验' })).toBeVisible()
    await expect(page.locator('.bubble--agent', { hasText: '找到' })).toBeVisible({ timeout: 15_000 })
  })

  test('role change still requires a different human and executes once', async ({ page }) => {
    const username = `e2eappr${String(Date.now()).slice(-7)}`
    const adminApi = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const adminLogin = await adminApi.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' }
    })
    const adminToken = (await adminLogin.json()).data.token
    const created = await adminApi.post('/api/tools/user.create/invoke', {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: {
        args: { username, displayName: `审批成员 ${username}`, title: '成员', deptId: 5,
          password: 'test1234', roleCode: 'guest' },
        sessionId: `e2e-create-${Date.now()}`
      }
    })
    expect((await created.json()).data.status).toBe('EXECUTED')

    const opsApi = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const opsLogin = await opsApi.post('/api/auth/login', {
      data: { username: 'ops', password: 'ops123' }
    })
    const opsToken = (await opsLogin.json()).data.token
    const invoke = await opsApi.post('/api/tools/user.assignRole/invoke', {
      headers: { Authorization: `Bearer ${opsToken}` },
      data: { args: { username, roleCode: 'ops' }, sessionId: `e2e-role-${Date.now()}` }
    })
    const outcome = (await invoke.json()).data
    expect(outcome.status).toBe('PENDING')
    expect(outcome.approvalId).toBeTruthy()
    await opsApi.dispose()

    await login(page)
    await openApp(page, '审批队列')
    const card = page.locator('.appr-card', { hasText: username })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: /批准并执行/ }).click()
    await expect(page.locator('.toast', { hasText: '已执行' })).toBeVisible({ timeout: 20_000 })
    await page.getByRole('button', { name: '全部', exact: true }).click()
    await page.waitForTimeout(1000)
    const executed = page.locator('.appr-card', { hasText: username })
    await expect(executed.locator('.chip', { hasText: '已执行' })).toBeVisible({ timeout: 10_000 })

    const cleanup = await adminApi.post('/api/tools/user.delete/invoke', {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { args: { username }, sessionId: `e2e-clean-${Date.now()}`, confirm: true }
    })
    expect((await cleanup.json()).data.status).toBe('EXECUTED')
    await adminApi.dispose()
  })

  test('agent asks the user to confirm a risky action instead of another approver', async ({ page }) => {
    const username = `e2eagent${String(Date.now()).slice(-7)}`
    const api = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const loginRes = await api.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' }
    })
    const token = (await loginRes.json()).data.token
    const created = await api.post('/api/tools/user.create/invoke', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        args: { username, displayName: `Agent 确认 ${username}`, title: '成员', deptId: 5,
          password: 'test1234', roleCode: 'guest' },
        sessionId: `e2e-agent-create-${Date.now()}`
      }
    })
    expect((await created.json()).data.status).toBe('EXECUTED')

    await login(page)
    await openApp(page, 'Agent 助手')
    const newChat = page.getByRole('button', { name: '新对话' })
    if (await newChat.count()) await newChat.first().click()
    await page.getByLabel('Agent 输入框').fill(`删除用户 ${username}`)
    await page.getByLabel('Agent 输入框').press('Enter')

    const confirmation = page.locator('.agent-confirm')
    await expect(confirmation).toBeVisible({ timeout: 15_000 })
    await expect(confirmation).toContainText('永久删除')

    const listBefore = await api.get(`/api/users?q=${encodeURIComponent(username)}&page=0&size=10`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    expect((await listBefore.json()).data.items).toHaveLength(1)

    await confirmation.getByRole('button', { name: '确认执行' }).click()
    await expect(page.locator('.bubble--agent', { hasText: '已删除用户' })).toBeVisible({ timeout: 20_000 })

    const listAfter = await api.get(`/api/users?q=${encodeURIComponent(username)}&page=0&size=10`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    expect((await listAfter.json()).data.items).toHaveLength(0)
    await api.dispose()
  })

  test('audit chain verifies and trajectory can be replayed', async ({ page }) => {
    await login(page)
    await openApp(page, '审计回放')
    await expect(page.locator('.audit__verify')).toContainText('哈希链完整')
    const rows = page.locator('.audit-row')
    expect(await rows.count()).toBeGreaterThan(0)
    const toolRow = rows.filter({ hasText: 'Tool 调用' }).first()
    if (await toolRow.count()) {
      await toolRow.click()
      await expect(page.locator('.chain-item').first()).toBeVisible()
    }
  })

  test('plugin lifecycle: unknown permission is rejected, pending plugin is not callable', async ({ page }) => {
    await login(page)
    await openApp(page, '插件')
    await page.getByRole('button', { name: '安装越权插件' }).click()
    await expect(page.locator('.toast', { hasText: 'PLUGIN_UNKNOWN_PERMISSION' })).toBeVisible({ timeout: 10_000 })

    const api = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const loginRes = await api.post('/api/auth/login', { data: { username: 'admin', password: 'admin123' } })
    const token = (await loginRes.json()).data.token
    const invoke = await api.post('/api/tools/dataset.import/invoke', {
      headers: { Authorization: `Bearer ${token}` },
      data: { args: { datasetName: 'e2e-pending', rows: 1 } }
    })
    const data = (await invoke.json()).data
    expect(['PENDING', 'DENIED']).toContain(data.status)
    if (data.status === 'DENIED') expect(data.errorCode).toBe('TOOL_NOT_ACTIVE')
    await api.dispose()
  })
})
