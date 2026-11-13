import { test, expect, request as pwRequest } from '@playwright/test'
import { login, openApp } from './helpers'

test.describe('data ingress and closure', () => {
  test('create user -> visible -> delete with own confirmation -> gone', async ({ page }) => {
    const suffix = String(Date.now()).slice(-7)
    const username = `e2e${suffix}`
    const displayName = `E2E 成员 ${suffix}`

    await login(page)
    await openApp(page, '用户管理')
    await page.getByRole('button', { name: '新建用户' }).click()
    await page.getByLabel('username').fill(username)
    await page.getByLabel('显示名称').fill(displayName)
    await page.getByLabel('职位').fill('测试成员')
    await page.getByLabel('初始密码').fill('test1234')
    await page.getByLabel('初始角色').selectOption('guest')
    await page.getByRole('button', { name: '保存' }).click()

    await expect(page.locator('.toast', { hasText: '创建' }).first()).toBeVisible()
    const row = page.locator('.directory__row', { hasText: displayName })
    await expect(row).toBeVisible({ timeout: 12_000 })

    const editedName = `${displayName} 已编辑`
    await row.click()
    await page.locator('.directory__actions').getByRole('button', { name: '编辑资料' }).click()
    await page.getByLabel('显示名称').fill(editedName)
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.toast', { hasText: '已更新用户' })).toBeVisible()
    const editedRow = page.locator('.directory__row', { hasText: editedName })
    await expect(editedRow).toBeVisible()

    await editedRow.click()
    await page.locator('.directory__actions').getByRole('button', { name: '删除用户' }).click()
    await page.getByRole('button', { name: '删除…' }).click()
    const riskModal = page.locator('.modal', { hasText: '确认这次操作' })
    await expect(riskModal).toBeVisible()
    await riskModal.getByLabel('我已了解风险，确认以我的权限执行').check()
    await riskModal.getByRole('button', { name: '确认执行' }).click()
    await expect(page.locator('.directory__row', { hasText: editedName })).toHaveCount(0)
  })

  test('upload file -> MinIO + MySQL + vector index -> searchable', async ({ page }) => {
    const stamp = Date.now()
    const title = `E2E 上传文档 ${stamp}`
    await login(page)
    await openApp(page, '访达')
    await page.getByRole('button', { name: '上传文件' }).click()
    await page.getByLabel('文件').setInputFiles({
      name: `e2e-note-${stamp}.md`,
      mimeType: 'text/markdown',
      buffer: Buffer.from(`# ${title}\n\n这份文档由 E2E 上传，用于验证 MinIO、MySQL 与 pgvector 的闭环。\n检索关键词：AuroraUpload${stamp}`)
    })
    await page.getByLabel('标题').fill(title)
    await page.getByLabel('摘要').fill('E2E 上传闭环验证文档')
    await page.getByLabel('可见性').selectOption('PUBLIC')
    await page.getByLabel('所需权限').selectOption('knowledge:public')
    await page.getByRole('button', { name: '上传并重建索引' }).click()

    await expect(page.locator('.toast', { hasText: '已上传' })).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.finder__row', { hasText: title })).toBeVisible({ timeout: 12_000 })

    const api = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:8080' })
    const loginRes = await api.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' }
    })
    const token = (await loginRes.json()).data.token
    const search = await api.get(`/api/search?q=${encodeURIComponent(title)}&limit=12`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    const results = (await search.json()).data.results as Array<{ title: string }>
    expect(results.some((result) => result.title === title)).toBeTruthy()
    await api.dispose()
  })

  test('plugin tool can be trialled and returns a result', async ({ page }) => {
    await login(page)
    await openApp(page, '插件')
    const card = page.locator('.plugin-card', { hasText: '文献检索 Tool 包' })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '试用 Tool' }).click()
    const modal = page.locator('.modal', { hasText: '试用 paper.search' })
    await expect(modal).toBeVisible()
    await expect(modal.locator('.gateway-result').first()).toContainText('Retrieval')
    await modal.getByRole('button', { name: '关闭' }).click()
  })
})
