import type { Principal } from '../types'

/**
 * The single guard used by desktop icons, menu entries and palette ACTION
 * results (AC-20). Desktop/menu/palette never re-implement permission logic.
 */
export function can(permissions: string[], permission?: string | null): boolean {
  if (!permission) return true
  return permissions.includes('*') || permissions.includes(permission)
}

export function canAll(permissions: string[], required: string[]): boolean {
  return required.every((p) => can(permissions, p))
}

export function guard(permissions: string[], permission?: string | null):
  { allowed: true } | { allowed: false; permission: string } {
  if (can(permissions, permission)) return { allowed: true }
  return { allowed: false, permission: permission ?? 'unknown' }
}

export function principalCan(principal: Principal | null, permission?: string | null): boolean {
  return can(principal?.permissions ?? [], permission)
}
