import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  Command, Expand, Info, Keyboard, LogOut, Maximize2, Minus, Moon, RefreshCw,
  Search, Settings2, ShieldCheck, Sun, UserRound, X
} from './icons'
import { APPS, APP_LIST, type AppId } from '../data/apps'
import { clockText, scopeLabel } from '../lib/format'
import { useAuth, currentPrincipal } from '../store/auth'
import { useShell } from '../store/shell'

interface Item {
  label?: string
  shortcut?: string
  action?: () => void
  disabled?: boolean
  separator?: boolean
  checked?: boolean
  header?: boolean
}

export function MenuBar() {
  const principal = currentPrincipal()
  const authSession = useAuth((s) => s.session)
  const desktop = useAuth((s) => s.desktop)
  const switchRole = useAuth((s) => s.switchRole)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const logout = useAuth((s) => s.logout)
  const theme = useShell((s) => s.theme)
  const setTheme = useShell((s) => s.setTheme)
  const windows = useShell((s) => s.windows)
  const focused = useShell((s) => s.focused)
  const openApp = useShell((s) => s.openApp)
  const closeWindow = useShell((s) => s.closeWindow)
  const minimizeWindow = useShell((s) => s.minimizeWindow)
  const toggleMaximize = useShell((s) => s.toggleMaximize)
  const focusWindow = useShell((s) => s.focusWindow)
  const pruneWindows = useShell((s) => s.pruneWindows)
  const setPaletteOpen = useShell((s) => s.setPaletteOpen)
  const setControlCenterOpen = useShell((s) => s.setControlCenterOpen)
  const userMenuOpen = useShell((s) => s.userMenuOpen)
  const setUserMenuOpen = useShell((s) => s.setUserMenuOpen)
  const toast = useShell((s) => s.toast)
  const activeMenu = useShell((s) => s.activeMenu)
  const setActiveMenu = useShell((s) => s.setActiveMenu)

  const [clock, setClock] = useState(() => clockText())
  const [modal, setModal] = useState<'about' | 'keys' | null>(null)
  const barRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const timer = setInterval(() => setClock(clockText()), 10_000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    const onPointerDown = (event: PointerEvent) => {
      if (!barRef.current?.contains(event.target as Node)) {
        setActiveMenu(null)
        setUserMenuOpen(false)
        setModal(null)
      }
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setActiveMenu(null)
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [setActiveMenu, setUserMenuOpen])

  const currentApp = focused ? APPS[focused] : null
  const fullscreen = () => {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => undefined)
    else document.documentElement.requestFullscreen?.().catch(() => toast('浏览器拒绝了全屏请求', 'bad'))
  }

  const changeRole = async (roleCode: string) => {
    setUserMenuOpen(false)
    try {
      const session = await switchRole(roleCode)
      pruneWindows(session.principal.permissions)
      toast(`已切换为 ${session.principal.displayName} · ${session.principal.title}`, 'ok')
    } catch (error) {
      toast(error instanceof Error ? error.message : '切换角色失败', 'bad')
    }
  }

  const appByCode: Record<string, AppId> = {
    'file:read': 'finder', 'file:finance': 'finder',
    'user:read': 'directory', 'agent:use': 'agent',
    'approval:read': 'approvals', 'audit:read': 'audit',
    'tool:read': 'tools', 'plugin:manage': 'plugins',
    'rbac:read': 'rbac', 'glass:use': 'glass',
    'knowledge:public': 'finder', 'knowledge:internal': 'finder',
    'knowledge:hr': 'finder'
  }
  const featureItems: Item[] = []
  for (const group of desktop?.menu ?? []) {
    featureItems.push({ header: true, label: group.name })
    for (const child of group.children) {
      const appId = appByCode[child.code]
      featureItems.push({
        label: child.name,
        action: appId ? () => openApp(appId) : undefined,
        disabled: !appId
      })
    }
  }
  if (featureItems.length === 0) featureItems.push({ label: '没有可见功能', disabled: true })

  const menus: { name: string; items: Item[] }[] = [
    {
      name: '文件',
      items: [
        { label: '打开访达', shortcut: '⌘1', action: () => openApp('finder') },
        { label: '用户管理', action: () => openApp('directory') },
        { label: '新建 Agent 会话', shortcut: '⌘2', action: () => openApp('agent') },
        { separator: true },
        { label: '关闭当前窗口', shortcut: '⌘W', action: () => focused && closeWindow(focused), disabled: !focused },
        { separator: true },
        { label: '退出登录', action: () => void logout() }
      ]
    },
    {
      name: '编辑',
      items: [
        { label: '命令面板…', shortcut: '⌘K', action: () => setPaletteOpen(true) },
        { label: '图片检索…', action: () => setPaletteOpen(true) },
        { separator: true },
        { label: '刷新桌面数据', shortcut: '⌘R', action: () => void refreshDesktop() }
      ]
    },
    {
      name: '视图',
      items: [
        { label: '深色外观', checked: theme === 'dark', action: () => setTheme('dark') },
        { label: '浅色外观', checked: theme === 'light', action: () => setTheme('light') },
        { separator: true },
        {
          label: '基础档：模糊 + 饱和',
          checked: useShell.getState().glassMode === 'base',
          action: () => useShell.getState().setGlassMode('base')
        },
        {
          label: '增强档：折射高光（≤3 surfaces）',
          checked: useShell.getState().glassMode === 'enhanced',
          action: () => useShell.getState().setGlassMode('enhanced')
        },
        { separator: true },
        { label: '进入全屏', action: fullscreen }
      ]
    },
    {
      name: '功能',
      items: featureItems
    },
    {
      name: '窗口',
      items: [
        ...(windows.length
          ? windows.map((w) => ({
            label: `${APPS[w.appId].name}${w.minimized ? '（已最小化）' : ''}`,
            checked: w.appId === focused,
            action: () => focusWindow(w.appId)
          }))
          : [{ label: '没有打开的窗口', disabled: true }]),
        { separator: true },
        { label: '最小化', shortcut: '⌘M', disabled: !focused, action: () => focused && minimizeWindow(focused) },
        { label: '缩放', disabled: !focused, action: () => focused && toggleMaximize(focused) }
      ]
    },
    {
      name: '帮助',
      items: [
        { label: '数据流导览', action: () => openApp('guide') },
        { label: '快捷键', action: () => setModal('keys') },
        { label: '关于 BaPi', action: () => setModal('about') }
      ]
    }
  ]

  const currentRole = authSession?.activeRole ?? principal?.roles?.[0] ?? '—'

  return (
    <div className="menubar glass" ref={barRef}>
      <button className="menubar__brand" onClick={() => setPaletteOpen(true)} aria-label="打开命令面板">
        <img className="menubar__logo" src="/bapi-logo.png" alt="BaPi" />
        <span>{currentApp?.name ?? 'BaPi'}</span>
      </button>

      {menus.map((menu) => (
        <div className="menubar__menu" key={menu.name}>
          <button
            aria-expanded={activeMenu === menu.name}
            onClick={() => setActiveMenu(activeMenu === menu.name ? null : menu.name)}
            onPointerEnter={() => activeMenu && setActiveMenu(menu.name)}
          >
            {menu.name}
          </button>
          {activeMenu === menu.name && (
            <div className="menu-pop glass-opaque">
              {menu.items.map((item, index) => item.separator ? (
                <div className="menu-pop__sep" key={`sep-${index}`} />
              ) : item.header ? (
                <div className="menu-pop__label" key={`header-${index}`}>{item.label}</div>
              ) : (
                <button
                  key={item.label}
                  className="menu-pop__item"
                  disabled={item.disabled}
                  onClick={() => {
                    setActiveMenu(null)
                    item.action?.()
                  }}
                >
                  <span style={{ width: 13, display: 'inline-flex', justifyContent: 'center' }}>
                    {item.checked ? '✓' : ''}
                  </span>
                  <span>{item.label}</span>
                  {item.shortcut && <span className="kbd">{item.shortcut}</span>}
                </button>
              ))}
            </div>
          )}
        </div>
      ))}

      <div className="menubar__spacer" />

      <div className="menubar__right">
        <button className="icon-btn" onClick={() => setPaletteOpen(true)} aria-label="打开命令面板" title="命令面板（⌘K）">
          <Search size={15} />
        </button>
        <button className="icon-btn" onClick={fullscreen} aria-label="全屏" title="全屏">
          <Expand size={14} />
        </button>
        <button className="icon-btn" aria-label="控制中心" title="控制中心"
          aria-pressed={useShell.getState().controlCenterOpen}
          onClick={() => setControlCenterOpen(!useShell.getState().controlCenterOpen)}>
          <Settings2 size={15} />
        </button>
        <span className="menubar__clock">{clock}</span>
        <div style={{ position: 'relative' }}>
          <button className="menubar__user" onClick={() => setUserMenuOpen(!userMenuOpen)} aria-expanded={userMenuOpen}>
            <span className="avatar" style={{ width: 21, height: 21, fontSize: 9, ['--hue' as string]: principal?.avatarHue ?? 210 }}>
              {principal?.displayName?.slice(0, 1) ?? '?'}
            </span>
            <span style={{ fontSize: 12 }}>{principal?.displayName}</span>
          </button>
          {userMenuOpen && (
            <div className="menu-pop menu-pop--right glass-opaque" style={{ minWidth: 248 }}>
              <div className="menu-pop__label">当前身份 · {scopeLabel(principal?.dataScope?.type)}</div>
              <div style={{ padding: '4px 9px 8px' }}>
                <b style={{ fontSize: 12.5 }}>{principal?.displayName}</b>
                <div className="muted" style={{ fontSize: 11 }}>{principal?.title} · {principal?.username}</div>
              </div>
              <div className="menu-pop__sep" />
              <div className="menu-pop__label">切换角色（演示权限实时生效）</div>
              {(authSession?.availableRoles ?? []).map((role) => (
                <button key={role.code} className="menu-pop__item" onClick={() => changeRole(role.code)}>
                  <span style={{ width: 13 }}>{role.code === currentRole ? '✓' : ''}</span>
                  <span>{role.name}</span>
                  <span className="muted" style={{ marginLeft: 'auto', fontSize: 10.5 }}>{role.scopeType}</span>
                </button>
              ))}
              <div className="menu-pop__sep" />
              <button className="menu-pop__item" onClick={() => {
                setUserMenuOpen(false)
                useShell.getState().setProfileOpen(true)
              }}>
                <UserRound size={14} /> 编辑我的资料
              </button>
              <button className="menu-pop__item" onClick={() => {
                setUserMenuOpen(false)
                openApp('access')
              }}>
                <ShieldCheck size={14} /> 申请权限
              </button>
              <button className="menu-pop__item" onClick={() => {
                setUserMenuOpen(false)
                openApp('glass')
              }}>
                <Sun size={14} /> 控制中心
              </button>
              <button className="menu-pop__item" onClick={() => void logout()}>
                <LogOut size={14} /> 退出登录
              </button>
            </div>
          )}
        </div>
      </div>

      {modal && createPortal((
        <div className="modal-backdrop" onClick={() => setModal(null)}>
          <div className="modal glass glass-opaque" onClick={(e) => e.stopPropagation()}>
            {modal === 'about' ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <img className="login__logo" src="/bapi-logo.png" alt="BaPi" style={{ width: 46, height: 46, margin: 0 }} />
                  <div>
                    <h3>BaPi · Aurora</h3>
                    <p style={{ margin: 0 }}>版本 1.0.0 · AI 原生桌面工作台</p>
                  </div>
                </div>
                <p style={{ marginTop: 14 }}>
                  一个以桌面为入口的 AI 原生工作台。每个受控操作都先校验权限，再按风险进入审批；
                  关键动作全程留痕，能力以插件扩展，数据范围始终跟随你的角色。
                </p>
                <div className="modal__actions">
                  <span className="chip chip--info">权限先行</span>
                  <span className="chip chip--violet">审批可控</span>
                  <span className="chip chip--ok">全程审计</span>
                  <button className="btn btn--primary" onClick={() => setModal(null)}>关闭</button>
                </div>
              </>
            ) : (
              <>
                <h3>键盘快捷键</h3>
                <p>桌面操作尽量不离开键盘。</p>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '7px 18px', marginTop: 14, fontSize: 12 }}>
                  <span>命令面板</span><span className="kbd">⌘ K</span>
                  <span>关闭当前窗口</span><span className="kbd">⌘ W</span>
                  <span>最小化当前窗口</span><span className="kbd">⌘ M</span>
                  <span>缩放当前窗口</span><span className="kbd">⌘ ⏎</span>
                  <span>刷新桌面数据</span><span className="kbd">⌘ R</span>
                  <span>关闭浮层</span><span className="kbd">Esc</span>
                </div>
                <div className="modal__actions">
                  <button className="btn btn--primary" onClick={() => setModal(null)}>知道了</button>
                </div>
              </>
            )}
          </div>
        </div>
      ), document.body)}
    </div>
  )
}
