import { useEffect } from 'react'
import { Loader2 } from './components/icons'
import { useAuth } from './store/auth'
import { AppLibrary } from './components/AppLibrary'
import { ProfileDialog } from './components/ProfileDialog'
import { useShell } from './store/shell'
import { useFpsGuard } from './lib/useFpsGuard'
import { useEnhancedSurfaceGuard } from './lib/useEnhancedSurfaceGuard'
import { AuroraBackground } from './components/AuroraBackground'
import { Login } from './components/Login'
import { MenuBar } from './components/MenuBar'
import { DesktopWidgets } from './components/DesktopWidgets'
import { WindowLayer } from './components/WindowLayer'
import { Dock } from './components/Dock'
import { CommandPalette } from './components/CommandPalette'
import { ControlCenter } from './components/ControlCenter'
import { Toasts } from './components/Toasts'

export default function App() {
  const initialized = useAuth((s) => s.initialized)
  const session = useAuth((s) => s.session)
  const desktop = useAuth((s) => s.desktop)
  const bootstrap = useAuth((s) => s.bootstrap)
  const logout = useAuth((s) => s.logout)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const bootDesktop = useShell((s) => s.bootDesktop)
  const syncWallpaper = useShell((s) => s.syncWallpaper)
  const syncDock = useShell((s) => s.syncDock)
  const paletteOpen = useShell((s) => s.paletteOpen)
  const controlCenterOpen = useShell((s) => s.controlCenterOpen)
  const setPaletteOpen = useShell((s) => s.setPaletteOpen)
  const setControlCenterOpen = useShell((s) => s.setControlCenterOpen)
  const setUserMenuOpen = useShell((s) => s.setUserMenuOpen)
  const focused = useShell((s) => s.focused)
  const closeWindow = useShell((s) => s.closeWindow)
  const minimizeWindow = useShell((s) => s.minimizeWindow)
  const toggleMaximize = useShell((s) => s.toggleMaximize)
  const toast = useShell((s) => s.toast)

  useFpsGuard()
  useEnhancedSurfaceGuard()

  useEffect(() => { void bootstrap() }, [bootstrap])

  useEffect(() => {
    const onUnauthorized = () => {
      if (useAuth.getState().token) {
        toast('会话已过期，请重新登录', 'bad')
        void logout()
      }
    }
    window.addEventListener('aurora:unauthorized', onUnauthorized)
    return () => window.removeEventListener('aurora:unauthorized', onUnauthorized)
  }, [logout, toast])

  useEffect(() => {
    if (desktop) bootDesktop()
  }, [desktop, bootDesktop])

  const wallpaperUserId = desktop?.principal.id
  useEffect(() => {
    if (wallpaperUserId) {
      void syncWallpaper()
      void syncDock()
    }
  }, [wallpaperUserId, syncWallpaper, syncDock])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const meta = event.metaKey || event.ctrlKey
      if (meta && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setPaletteOpen(!useShell.getState().paletteOpen)
        return
      }
      if (event.key === 'Escape') {
        if (useShell.getState().paletteOpen) setPaletteOpen(false)
        setControlCenterOpen(false)
        setUserMenuOpen(false)
        return
      }
      if (!meta) return
      const key = event.key.toLowerCase()
      if (key === 'w' && focused) {
        event.preventDefault()
        closeWindow(focused)
      } else if (key === 'm' && focused) {
        event.preventDefault()
        minimizeWindow(focused)
      } else if (key === 'r') {
        event.preventDefault()
        void refreshDesktop()
        toast('桌面数据已刷新', 'ok')
      } else if (event.key === 'Enter' && focused) {
        event.preventDefault()
        toggleMaximize(focused)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [focused, closeWindow, minimizeWindow, toggleMaximize, refreshDesktop, setPaletteOpen,
    setControlCenterOpen, setUserMenuOpen, toast])

  if (!initialized) {
    return (
      <div className="boot">
        <img className="boot__logo" src="/bapi-logo.png" alt="BaPi" />
        <div className="boot__name">BaPi</div>
        <div className="boot__status"><Loader2 size={13} className="spin" /> 正在恢复会话…</div>
      </div>
    )
  }

  if (!session || !desktop) {
    return (
      <>
        <AuroraBackground />
        <Login />
        <Toasts />
      </>
    )
  }

  return (
    <>
      <AuroraBackground />
      <div className="shell">
        <MenuBar />
        <main className="desktop" aria-label="桌面">
          <DesktopWidgets />
          <WindowLayer />
          <div className="desktop-hint">
            长按程序坞图标管理 · 按住图标拖动可调整顺序
          </div>
        </main>
        <Dock />
        {paletteOpen && <CommandPalette />}
        {controlCenterOpen && <ControlCenter />}
        <AppLibrary />
        <ProfileDialog />
        <Toasts />
      </div>
    </>
  )
}
