import { describe, expect, it } from 'vitest'
import { can, canAll, guard, principalCan } from '../lib/permissions'
import type { Principal } from '../types'

const principal: Principal = {
  id: 1, username: 'ops', displayName: '陈曦', title: '运营',
  roles: ['ops'], permissions: ['file:read', 'agent:use', 'approval:read'],
  dataScope: { type: 'DEPT_AND_CHILD', deptIds: [2, 5, 6] }, avatarHue: 265
}

describe('permission guard', () => {
  it('allows missing permission (public)', () => {
    expect(can(principal.permissions, undefined)).toBe(true)
    expect(can(principal.permissions, '')).toBe(true)
  })

  it('allows only granted permissions', () => {
    expect(can(principal.permissions, 'agent:use')).toBe(true)
    expect(can(principal.permissions, 'user:delete')).toBe(false)
  })

  it('canAll requires every permission', () => {
    expect(canAll(principal.permissions, ['file:read', 'agent:use'])).toBe(true)
    expect(canAll(principal.permissions, ['file:read', 'user:delete'])).toBe(false)
  })

  it('guard returns the missing permission for palette/desktop/menu', () => {
    expect(guard(principal.permissions, 'agent:use')).toEqual({ allowed: true })
    expect(guard(principal.permissions, 'plugin:manage')).toEqual(
      { allowed: false, permission: 'plugin:manage' })
  })

  it('handles a null principal', () => {
    expect(principalCan(null, 'file:read')).toBe(false)
    expect(principalCan(null, undefined)).toBe(true)
  })
})
