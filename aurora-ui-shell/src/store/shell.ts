import { create } from 'zustand'
import { APPS, APP_LIST, type AppId } from '../data/apps'
import { currentPermissions } from './auth'
import { can } from '../lib/permissions'
import { api, errorMessage } from '../lib/api'
import { prepareWallpaperUpload } from '../lib/wallpaper'
import type { WallpaperHistoryView, WallpaperView } from '../types'

export type Theme = 'dark' | 'light'
export type GlassMode = 'base' | 'enhanced'
export type Tone = 'ok' | 'bad' | 'info'
export type WallpaperState = {
  kind: 'preset'; presetId: string; name: string; source?: 'server' | 'local'
} | {
  kind: 'image'; url?: string; dataUrl?: string; name: string; source?: 'server' | 'local'
}

export interface WindowState {
  id: string
  appId: AppId
  x: number
  y: number
  w: number
  h: number
  z: number
  minimized: boolean
  maximized: boolean
  openedAt: number
}

export interface Toast {
  id: number
  message: string
  tone: Tone
}

export interface GlassTokens {
  blur: number
  saturation: number
  opacity: number
  specular: number
  border: number
}

interface ShellState {
  theme: Theme
  glassMode: GlassMode
  glass: GlassTokens
  windows: WindowState[]
  focused: AppId | null
  zCounter: number
  paletteOpen: boolean
  controlCenterOpen: boolean
  userMenuOpen: boolean
  activeMenu: string | null
  toasts: Toast[]
  fps: number
  autoDowngraded: boolean
  booted: boolean
  wallpaper: WallpaperState
  wallpaperHistory: WallpaperHistoryView[]
  dockApps: AppId[]
  libraryOpen: boolean
  profileOpen: boolean
  setWallpaperPreset: (presetId: string, name: string) => Promise<void>
  setCustomWallpaper: (file: File) => Promise<void>
  clearWallpaper: () => Promise<void>
  syncWallpaper: () => Promise<void>
  applyWallpaperHistory: (id: number) => Promise<void>
  syncDock: () => Promise<void>
  moveDockApp: (from: number, to: number) => void
  addToDock: (appId: AppId) => void
  removeFromDock: (appId: AppId) => void
  resetDock: () => void
  setLibraryOpen: (open: boolean) => void
  setProfileOpen: (open: boolean) => void
  setTheme: (theme: Theme) => void
  setGlassMode: (mode: GlassMode) => void
  setGlassToken: (key: keyof GlassTokens, value: number) => void
  resetGlass: () => void
  openApp: (appId: AppId) => boolean
  openAppGuarded: (appId: AppId) => boolean
  closeWindow: (appId: AppId) => void
  minimizeWindow: (appId: AppId) => void
  toggleMaximize: (appId: AppId) => void
  focusWindow: (appId: AppId) => void
  updateBounds: (appId: AppId, bounds: Partial<Pick<WindowState, 'x' | 'y' | 'w' | 'h'>>) => void
  pruneWindows: (permissions: string[]) => void
  bootDesktop: () => void
  setPaletteOpen: (open: boolean) => void
  setControlCenterOpen: (open: boolean) => void
  setUserMenuOpen: (open: boolean) => void
  setActiveMenu: (menu: string | null) => void
  toast: (message: string, tone?: Tone) => void
  dismissToast: (id: number) => void
  setFps: (fps: number) => void
}

const DEFAULT_GLASS: GlassTokens = { blur: 30, saturation: 185, opacity: 52, specular: 28, border: 14 }

function readTheme(): Theme {
  try {
    const stored = localStorage.getItem('bapi.theme')
    if (stored === 'light' || stored === 'dark') return stored
  } catch {
    // ignore
  }
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

function readGlass(): GlassTokens {
  try {
    const raw = localStorage.getItem('bapi.glass')
    if (raw) {
      const parsed = JSON.parse(raw)
      return { ...DEFAULT_GLASS, ...parsed }
    }
  } catch {
    // ignore
  }
  return DEFAULT_GLASS
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem('bapi.theme', theme)
  } catch {
    // ignore
  }
}

export function applyGlass(tokens: GlassTokens) {
  const root = document.documentElement.style
  root.setProperty('--blur', `${tokens.blur}px`)
  root.setProperty('--saturation', `${tokens.saturation}%`)
  root.setProperty('--glass-opacity', String(tokens.opacity / 100))
  root.setProperty('--glass-specular', `rgba(255,255,255,${(tokens.specular / 100).toFixed(2)})`)
  root.setProperty('--glass-border', `rgba(255,255,255,${(tokens.border / 100).toFixed(2)})`)
  try {
    localStorage.setItem('bapi.glass', JSON.stringify(tokens))
  } catch {
    // ignore
  }
}

let toastSeq = 1

function placeWindow(index: number, appId: AppId) {
  const def = APPS[appId]
  const width = Math.min(def.size.w, window.innerWidth - 36)
  const height = Math.min(def.size.h, window.innerHeight - 130)
  const x = Math.max(12, Math.min(
    window.innerWidth - width - 24,
    window.innerWidth * 0.5 - width * 0.38 + (index % 4) * 28
  ))
  const y = Math.max(48, Math.min(
    window.innerHeight - height - 110,
    58 + (index % 5) * 26
  ))
  return { x, y, w: width, h: height }
}

function readWallpaper(): WallpaperState {
  try {
    const raw = localStorage.getItem('bapi.wallpaper')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed.kind === 'image' && (parsed.url || parsed.dataUrl)) {
        return {
          kind: 'image', url: parsed.url, dataUrl: parsed.dataUrl,
          name: parsed.name ?? '自定义', source: 'local'
        }
      }
      if (parsed.kind === 'preset' && parsed.presetId) {
        return { kind: 'preset', presetId: parsed.presetId, name: parsed.name ?? '预设', source: 'local' }
      }
    }
  } catch {
    // Ignore damaged local preferences.
  }
  return { kind: 'preset', presetId: 'aurora', name: '极光' }
}

function wallpaperFromView(view: WallpaperView): WallpaperState {
  if (view.kind === 'IMAGE' && view.url) {
    return { kind: 'image', url: view.url, name: view.name || '自定义壁纸', source: 'server' }
  }
  if (view.kind === 'IMAGE') {
    return { kind: 'preset', presetId: 'aurora', name: '极光', source: 'server' }
  }
  return {
    kind: 'preset', presetId: view.presetId ?? 'aurora',
    name: view.name || '预设壁纸', source: 'server'
  }
}

function validAppIds(value: unknown): AppId[] {
  if (!Array.isArray(value)) return []
  const valid = new Set(APP_LIST.map((app) => app.id))
  return [...new Set(value.filter((id): id is AppId => typeof id === 'string' && valid.has(id as AppId)))]
}

function readDock(): AppId[] {
  try {
    const raw = localStorage.getItem('bapi.dock')
    const ids = raw ? validAppIds(JSON.parse(raw)) : APP_LIST.map((app) => app.id)
    if (!ids.includes('finder')) ids.unshift('finder')
    return ids
  } catch {
    return APP_LIST.map((app) => app.id)
  }
}

function persist(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // A full or blocked localStorage must not break the desktop.
  }
}

function applyWallpaper(wallpaper: WallpaperState) {
  document.documentElement.dataset.wallpaper = wallpaper.kind === 'preset'
    ? wallpaper.presetId : 'custom'
}

const initialTheme = typeof window !== 'undefined' ? readTheme() : 'dark'
const initialWallpaper = typeof window !== 'undefined' ? readWallpaper() : {
  kind: 'preset' as const, presetId: 'aurora', name: '极光'
}
const initialDock = typeof window !== 'undefined' ? readDock() : APP_LIST.map((app) => app.id)
const initialGlass = typeof window !== 'undefined' ? readGlass() : DEFAULT_GLASS
if (typeof document !== 'undefined') {
  applyTheme(initialTheme)
  applyGlass(initialGlass)
  applyWallpaper(initialWallpaper)
}

export const useShell = create<ShellState>((set, get) => ({
  theme: initialTheme,
  glassMode: 'base',
  glass: initialGlass,
  windows: [],
  focused: null,
  zCounter: 20,
  paletteOpen: false,
  controlCenterOpen: false,
  userMenuOpen: false,
  activeMenu: null,
  toasts: [],
  fps: 60,
  autoDowngraded: false,
  booted: false,
  wallpaper: initialWallpaper,
  wallpaperHistory: [],
  dockApps: initialDock,
  libraryOpen: false,
  profileOpen: false,

  setTheme: (theme) => {
    applyTheme(theme)
    set({ theme })
  },

  setGlassMode: (glassMode) => set({ glassMode }),

  setGlassToken: (key, value) => {
    const glass = { ...get().glass, [key]: value }
    applyGlass(glass)
    set({ glass })
  },

  resetGlass: () => {
    applyGlass(DEFAULT_GLASS)
    set({ glass: DEFAULT_GLASS, glassMode: 'base' })
  },

  openApp: (appId) => {
    const permissions = currentPermissions()
    const def = APPS[appId]
    if (!def || !can(permissions, def.permission)) {
      get().toast(`无权限：缺少 ${def?.permission ?? '未知权限'}`, 'bad')
      return false
    }
    const existing = get().windows.find((w) => w.appId === appId)
    if (existing) {
      set({
        windows: get().windows.map((w) => w.appId === appId
          ? { ...w, minimized: false, z: get().zCounter + 1 } : w),
        focused: appId,
        zCounter: get().zCounter + 1
      })
      return true
    }
    const index = get().windows.length
    const bounds = placeWindow(index, appId)
    const win: WindowState = {
      id: `${appId}-${Date.now()}`,
      appId,
      ...bounds,
      z: get().zCounter + 1,
      minimized: false,
      maximized: false,
      openedAt: Date.now()
    }
    set({
      windows: [...get().windows, win],
      focused: appId,
      zCounter: get().zCounter + 1
    })
    return true
  },

  openAppGuarded: (appId) => {
    const def = APPS[appId]
    if (!def) return false
    if (!can(currentPermissions(), def.permission)) {
      get().toast(`无权限：缺少 ${def.permission}`, 'bad')
      return false
    }
    return get().openApp(appId)
  },

  closeWindow: (appId) => {
    const windows = get().windows.filter((w) => w.appId !== appId)
    const top = [...windows].sort((a, b) => b.z - a.z)[0]
    set({ windows, focused: top?.appId ?? null })
  },

  minimizeWindow: (appId) => {
    set({
      windows: get().windows.map((w) => w.appId === appId ? { ...w, minimized: true } : w),
      focused: null
    })
  },

  toggleMaximize: (appId) => {
    set({
      windows: get().windows.map((w) => w.appId === appId
        ? { ...w, maximized: !w.maximized, minimized: false } : w),
      focused: appId
    })
  },

  focusWindow: (appId) => {
    const target = get().windows.find((w) => w.appId === appId)
    if (!target) return
    set({
      windows: get().windows.map((w) => w.appId === appId
        ? { ...w, minimized: false, z: get().zCounter + 1 } : w),
      focused: appId,
      zCounter: get().zCounter + 1
    })
  },

  updateBounds: (appId, bounds) => {
    set({
      windows: get().windows.map((w) => w.appId === appId ? { ...w, ...bounds } : w)
    })
  },

  pruneWindows: (permissions) => {
    const windows = get().windows.filter((w) => can(permissions, APPS[w.appId].permission))
    if (windows.length !== get().windows.length) {
      const top = [...windows].sort((a, b) => b.z - a.z)[0]
      set({ windows, focused: top?.appId ?? null })
      get().toast('角色已切换：无权限的窗口已自动关闭', 'info')
    }
  },

  bootDesktop: () => {
    if (get().booted) return
    set({ booted: true })
    const permissions = currentPermissions()
    if (can(permissions, APPS.agent.permission)) get().openApp('agent')
  },

  setPaletteOpen: (paletteOpen) => set(paletteOpen
    ? { paletteOpen: true, controlCenterOpen: false, userMenuOpen: false }
    : { paletteOpen: false }),
  setControlCenterOpen: (controlCenterOpen) => set(controlCenterOpen
    ? { controlCenterOpen: true, paletteOpen: false, userMenuOpen: false }
    : { controlCenterOpen: false }),
  setUserMenuOpen: (userMenuOpen) => set(userMenuOpen
    ? { userMenuOpen: true, paletteOpen: false, controlCenterOpen: false }
    : { userMenuOpen: false }),
  setActiveMenu: (activeMenu) => set({ activeMenu }),

  toast: (message, tone = 'info') => {
    const id = toastSeq++
    set({ toasts: [...get().toasts, { id, message, tone }] })
    setTimeout(() => get().dismissToast(id), 3800)
  },

  dismissToast: (id) => set({ toasts: get().toasts.filter((t) => t.id !== id) }),

  setWallpaperPreset: async (presetId, name) => {
    const wallpaper: WallpaperState = { kind: 'preset', presetId, name, source: 'server' }
    applyWallpaper(wallpaper)
    persist('bapi.wallpaper', wallpaper)
    set({ wallpaper })
    try {
      const view = await api.setWallpaperPreset(presetId)
      const synced = wallpaperFromView(view)
      applyWallpaper(synced)
      persist('bapi.wallpaper', synced)
      set({ wallpaper: synced })
      void api.wallpaperHistory()
        .then((wallpaperHistory) => set({ wallpaperHistory }))
        .catch(() => undefined)
    } catch (error) {
      get().toast(`壁纸已在当前设备生效，但同步到服务器失败：${errorMessage(error)}`, 'bad')
    }
  },

  setCustomWallpaper: async (file) => {
    try {
      const prepared = await prepareWallpaperUpload(file)
      const view = await api.uploadWallpaper(prepared.blob, prepared.fileName)
      const wallpaper = wallpaperFromView(view)
      applyWallpaper(wallpaper)
      persist('bapi.wallpaper', wallpaper)
      set({ wallpaper })
      void api.wallpaperHistory()
        .then((wallpaperHistory) => set({ wallpaperHistory }))
        .catch(() => undefined)
    } catch (error) {
      get().toast(`壁纸上传失败：${errorMessage(error)}`, 'bad')
      throw error
    }
  },

  syncWallpaper: async () => {
    try {
      const [view, history] = await Promise.all([api.wallpaper(), api.wallpaperHistory()])
      set({ wallpaperHistory: history })
      if (view.kind === 'IMAGE' && !view.url) {
        // Object storage is temporarily unreachable; keep the last cached
        // image rather than silently falling back to a preset.
        return
      }
      const wallpaper = wallpaperFromView(view)
      applyWallpaper(wallpaper)
      persist('bapi.wallpaper', wallpaper)
      set({ wallpaper })
    } catch {
      // Keep the cached wallpaper when the network or storage is unavailable.
    }
  },

  applyWallpaperHistory: async (id) => {
    try {
      const view = await api.applyWallpaperHistory(id)
      const wallpaper = wallpaperFromView(view)
      applyWallpaper(wallpaper)
      persist('bapi.wallpaper', wallpaper)
      set({ wallpaper })
      const history = await api.wallpaperHistory()
      set({ wallpaperHistory: history })
    } catch (error) {
      get().toast(`切换历史壁纸失败：${errorMessage(error)}`, 'bad')
    }
  },

  syncDock: async () => {
    try {
      const view = await api.dockPreference()
      const ids = validAppIds(view.apps)
      if (!ids.length) return
      if (!ids.includes('finder')) ids.unshift('finder')
      persist('bapi.dock', ids)
      set({ dockApps: ids })
    } catch {
      // Keep the cached dock order when the network is unavailable.
    }
  },

  moveDockApp: (from, to) => {
    const dockApps = [...get().dockApps]
    if (from < 0 || to < 0 || from >= dockApps.length || to >= dockApps.length || from === to) {
      return
    }
    const [moved] = dockApps.splice(from, 1)
    dockApps.splice(to, 0, moved)
    persist('bapi.dock', dockApps)
    set({ dockApps })
    void api.saveDockPreference(dockApps).catch(() => {
      get().toast('程序坞顺序已在本机生效，但同步到账号失败', 'bad')
    })
  },

  clearWallpaper: async () => {
    try {
      const view = await api.resetWallpaper()
      const wallpaper = wallpaperFromView(view)
      applyWallpaper(wallpaper)
      persist('bapi.wallpaper', wallpaper)
      set({ wallpaper })
      void api.wallpaperHistory()
        .then((wallpaperHistory) => set({ wallpaperHistory }))
        .catch(() => undefined)
    } catch (error) {
      get().toast(`恢复默认壁纸失败：${errorMessage(error)}`, 'bad')
    }
  },

  addToDock: (appId) => {
    if (get().dockApps.includes(appId)) return
    const dockApps = [...get().dockApps, appId]
    persist('bapi.dock', dockApps)
    set({ dockApps })
    void api.saveDockPreference(dockApps).catch(() => {
      get().toast('程序坞顺序已在本机生效，但同步到账号失败', 'bad')
    })
  },

  removeFromDock: (appId) => {
    if (appId === 'finder') {
      get().toast('访达是固定入口，不能从程序坞移除', 'info')
      return
    }
    const dockApps = get().dockApps.filter((id) => id !== appId)
    persist('bapi.dock', dockApps)
    set({ dockApps })
    void api.saveDockPreference(dockApps).catch(() => {
      get().toast('程序坞顺序已在本机生效，但同步到账号失败', 'bad')
    })
  },

  resetDock: () => {
    const dockApps = APP_LIST.map((app) => app.id)
    persist('bapi.dock', dockApps)
    set({ dockApps })
    void api.saveDockPreference(dockApps).catch(() => {
      get().toast('程序坞已在本机重置，但同步到账号失败', 'bad')
    })
  },

  setLibraryOpen: (libraryOpen) => set({ libraryOpen }),

  setProfileOpen: (profileOpen) => set({ profileOpen }),

  setFps: (fps) => {
    set({ fps: Math.round(fps) })
    if (get().glassMode === 'enhanced' && fps < 40 && !get().autoDowngraded) {
      set({ glassMode: 'base', autoDowngraded: true })
      get().toast('帧率低于 40fps，已自动降级为基础档玻璃（FPSGuard）', 'info')
    }
  }
}))
