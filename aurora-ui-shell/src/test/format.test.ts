import { describe, expect, it } from 'vitest'
import { decisionTone, humanSize, scopeLabel, shortId, visibilityLabel } from '../lib/format'

describe('format helpers', () => {
  it('formats sizes', () => {
    expect(humanSize(0)).toBe('0 B')
    expect(humanSize(2048)).toBe('2.0 KB')
    expect(humanSize(2 * 1024 * 1024)).toBe('2.0 MB')
  })

  it('maps decision tones', () => {
    expect(decisionTone('EXECUTED')).toBe('ok')
    expect(decisionTone('PENDING')).toBe('warn')
    expect(decisionTone('PERMISSION_DENIED')).toBe('bad')
    expect(decisionTone('TEXT_QUERY')).toBe('info')
  })

  it('labels scopes and visibility', () => {
    expect(scopeLabel('DEPT_AND_CHILD')).toBe('部门及子部门')
    expect(visibilityLabel('ROLES')).toBe('角色可见')
  })

  it('shortens ids without throwing', () => {
    expect(shortId('abcdefghijk', 6)).toBe('abcdef')
    expect(shortId(undefined)).toBe('—')
  })
})
