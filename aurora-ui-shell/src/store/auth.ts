import { create } from 'zustand'
import { api, getToken, setToken, ApiError } from '../lib/api'
import type { DesktopData, Principal, RoleView, SessionView } from '../types'

interface AuthState {
  token: string | null
  session: SessionView | null
  desktop: DesktopData | null
  loading: boolean
  error: string | null
  initialized: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  bootstrap: () => Promise<void>
  refreshDesktop: () => Promise<void>
  refreshSession: () => Promise<void>
  switchRole: (roleCode: string) => Promise<SessionView>
  clearError: () => void
}

function principalOf(session: SessionView | null, desktop: DesktopData | null): Principal | null {
  return desktop?.principal ?? session?.principal ?? null
}

export const useAuth = create<AuthState>((set, get) => ({
  token: getToken(),
  session: null,
  desktop: null,
  loading: false,
  error: null,
  initialized: false,

  login: async (username, password) => {
    set({ loading: true, error: null })
    try {
      const result = await api.login(username, password)
      setToken(result.token)
      set({
        token: result.token,
        session: {
          principal: result.principal,
          availableRoles: result.roles,
          expiresAt: result.expiresAt,
          activeRole: result.principal.roles[0] ?? 'admin'
        },
        loading: false,
        initialized: true,
        error: null
      })
      await get().refreshDesktop()
    } catch (error) {
      const message = error instanceof ApiError ? error.message : '登录失败，请检查后端服务'
      set({ loading: false, error: message })
      throw error
    }
  },

  logout: async () => {
    try {
      if (get().token) await api.logout()
    } catch {
      // The token may already be invalid; local logout still proceeds.
    }
    setToken(null)
    set({ token: null, session: null, desktop: null, error: null, initialized: true })
  },

  bootstrap: async () => {
    if (!get().token) {
      set({ initialized: true })
      return
    }
    set({ loading: true })
    try {
      const [session, desktop] = await Promise.all([api.me(), api.desktop()])
      set({ session, desktop, loading: false, initialized: true, error: null })
    } catch {
      setToken(null)
      set({ token: null, session: null, desktop: null, loading: false, initialized: true })
    }
  },

  refreshSession: async () => {
    const session = await api.me()
    set({ session })
    await get().refreshDesktop()
  },

  refreshDesktop: async () => {
    try {
      const desktop = await api.desktop()
      const session = get().session
      set({
        desktop,
        session: session ? {
          ...session,
          principal: desktop.principal,
          availableRoles: desktop.roles,
          activeRole: desktop.activeRole
        } : session
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        await get().logout()
      } else {
        set({ error: error instanceof Error ? error.message : '刷新桌面失败' })
      }
    }
  },

  switchRole: async (roleCode) => {
    const session = await api.switchRole(roleCode)
    set({ session })
    await get().refreshDesktop()
    return session
  },

  clearError: () => set({ error: null })
}))

export function currentPermissions(): string[] {
  return principalOf(useAuth.getState().session, useAuth.getState().desktop)?.permissions ?? []
}

export function currentPrincipal(): Principal | null {
  return principalOf(useAuth.getState().session, useAuth.getState().desktop)
}

export function currentRoles(): RoleView[] {
  return useAuth.getState().session?.availableRoles ?? useAuth.getState().desktop?.roles ?? []
}
