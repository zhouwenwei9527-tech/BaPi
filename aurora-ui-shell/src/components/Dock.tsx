import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { SquaresFour, Trash2 } from './icons'
import { AppGlyph } from './AppGlyph'
import { APPS, type AppId } from '../data/apps'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'

interface DockMenu {
  appId: AppId
  x: number
  y: number
}

interface DragGesture {
  appId: AppId
  startX: number
  startY: number
  moved: boolean
}

export function Dock() {
  const permissions = useAuth((s) => s.desktop?.principal.permissions ?? [])
  const pending = useAuth((s) => s.desktop?.stats.pendingApprovals ?? 0)
  const dockApps = useShell((s) => s.dockApps)
  const windows = useShell((s) => s.windows)
  const openAppGuarded = useShell((s) => s.openAppGuarded)
  const removeFromDock = useShell((s) => s.removeFromDock)
  const setLibraryOpen = useShell((s) => s.setLibraryOpen)
  const toast = useShell((s) => s.toast)
  const itemRefs = useRef<Record<string, HTMLButtonElement | null>>({})
  const dockRef = useRef<HTMLDivElement | null>(null)
  const ghostRef = useRef<HTMLDivElement | null>(null)
  const pressTimer = useRef<number | null>(null)
  const longPressed = useRef(false)
  const reorderActive = useRef(false)
  const gesture = useRef<DragGesture | null>(null)
  const dropTargetRef = useRef<AppId | null>(null)
  const cleanupDrag = useRef<(() => void) | null>(null)
  const appsRef = useRef<Array<(typeof APPS)[AppId]>>([])
  const [reordering, setReordering] = useState<string | null>(null)
  const [dropTarget, setDropTarget] = useState<AppId | null>(null)
  const [menu, setMenu] = useState<DockMenu | null>(null)

  const apps = useMemo(() => dockApps
    .map((id) => APPS[id])
    .filter((app) => app && can(permissions, app.permission)), [dockApps, permissions])

  useEffect(() => {
    appsRef.current = apps
  }, [apps])

  useEffect(() => {
    if (!menu) { longPressed.current = false; return }
    const close = () => setMenu(null)
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [menu])

  useEffect(() => () => cleanupDrag.current?.(), [])

  const magnify = (event: React.MouseEvent) => {
    if (reorderActive.current) return
    const x = event.clientX
    for (const app of apps) {
      const el = itemRefs.current[app.id]
      if (!el) continue
      const rect = el.getBoundingClientRect()
      const distance = Math.abs(x - (rect.left + rect.width / 2))
      const influence = Math.max(0, 1 - distance / 118)
      const scale = 1 + 0.34 * influence * influence
      const lift = 8 * influence * influence
      el.style.transform = `translateY(-${lift}px) scale(${scale})`
    }
  }

  const reset = () => {
    for (const el of Object.values(itemRefs.current)) {
      if (el) el.style.transform = ''
    }
  }

  const nearestAppId = (x: number): AppId | null => {
    let best: AppId | null = null
    let bestDistance = Number.POSITIVE_INFINITY
    for (const app of appsRef.current) {
      const el = itemRefs.current[app.id]
      if (!el) continue
      const rect = el.getBoundingClientRect()
      const distance = Math.abs(x - (rect.left + rect.width / 2))
      if (distance < bestDistance) {
        bestDistance = distance
        best = app.id
      }
    }
    return best
  }

  /** macOS-style live shift: neighbours make room for the dragged icon. */
  const applyShift = (targetId: AppId | null) => {
    const list = appsRef.current
    const from = list.findIndex((app) => app.id === gesture.current?.appId)
    const to = targetId ? list.findIndex((app) => app.id === targetId) : -1
    const rects = list.map((app) => itemRefs.current[app.id]?.getBoundingClientRect() ?? null)
    const step = rects.length > 1 && rects[0] && rects[1]
      ? rects[1].left - rects[0].left : 60
    list.forEach((app, index) => {
      const el = itemRefs.current[app.id]
      if (!el) return
      let shift = 0
      if (from >= 0 && to >= 0 && from !== to) {
        if (to < from && index >= to && index < from) shift = step
        else if (to > from && index > from && index <= to) shift = -step
      }
      el.style.transform = shift ? `translateX(${shift}px)` : ''
    })
  }

  const stopGesture = () => {
    if (pressTimer.current) window.clearTimeout(pressTimer.current)
    pressTimer.current = null
    cleanupDrag.current?.()
    cleanupDrag.current = null
    gesture.current = null
    dropTargetRef.current = null
    reorderActive.current = false
    reset()
    setReordering(null)
    setDropTarget(null)
  }

  const startPress = (appId: AppId, event: React.PointerEvent) => {
    if (event.button !== 0) return
    stopGesture()
    longPressed.current = false
    gesture.current = {
      appId,
      startX: event.clientX,
      startY: event.clientY,
      moved: false
    }

    pressTimer.current = window.setTimeout(() => {
      pressTimer.current = null
      reorderActive.current = true
      longPressed.current = true
      reset()
      setReordering(appId)
    }, 360)

    const onMove = (moveEvent: PointerEvent) => {
      const current = gesture.current
      if (!current || !reorderActive.current) return
      if (!current.moved
        && Math.abs(moveEvent.clientX - current.startX) < 4
        && Math.abs(moveEvent.clientY - current.startY) < 4) {
        return
      }
      current.moved = true
      if (ghostRef.current) {
        ghostRef.current.style.transform =
          `translate3d(${moveEvent.clientX - 28}px, ${moveEvent.clientY - 36}px, 0)`
      }
      const dockRect = dockRef.current?.getBoundingClientRect()
      const inBand = dockRect
        && moveEvent.clientY > dockRect.top - 70
        && moveEvent.clientY < dockRect.bottom + 70
      const target = inBand ? nearestAppId(moveEvent.clientX) : null
      dropTargetRef.current = target
      setDropTarget(target)
      applyShift(target)
    }

    const onUp = (upEvent: PointerEvent) => {
      const current = gesture.current
      if (reorderActive.current && current && !current.moved) {
        setMenu({ appId: current.appId, x: upEvent.clientX, y: upEvent.clientY })
      }
      if (reorderActive.current && current && current.moved) {
        const target = dropTargetRef.current
        const order = useShell.getState().dockApps
        const from = order.indexOf(current.appId)
        const to = target ? order.indexOf(target) : -1
        if (from >= 0 && to >= 0 && from !== to) {
          useShell.getState().moveDockApp(from, to)
        }
      }
      stopGesture()
    }

    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp, { once: true })
    window.addEventListener('pointercancel', onUp, { once: true })
    cleanupDrag.current = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      window.removeEventListener('pointercancel', onUp)
    }
  }

  const handleClick = (appId: AppId) => {
    if (longPressed.current) {
      longPressed.current = false
      return
    }
    openAppGuarded(appId)
  }

  const openMenu = (appId: AppId, event: React.MouseEvent) => {
    event.preventDefault()
    stopGesture()
    setMenu({ appId, x: event.clientX, y: event.clientY })
  }

  const menuApp = menu ? APPS[menu.appId] : null

  return (
    <div className="dock-wrap">
      <div className="dock glass" ref={dockRef} onMouseMove={magnify} onMouseLeave={reset}
        role="toolbar" aria-label="程序坞">
        {apps.map((app) => {
          const open = windows.some((w) => w.appId === app.id)
          const minimized = windows.some((w) => w.appId === app.id && w.minimized)
          return (
            <button
              key={app.id}
              className="dock-item"
              ref={(el) => { itemRefs.current[app.id] = el }}
              data-n={app.name}
              data-reordering={reordering === app.id}
              data-drop-target={dropTarget === app.id}
              onPointerDown={(event) => startPress(app.id, event)}
              onContextMenu={(event) => openMenu(app.id, event)}
              onClick={() => handleClick(app.id)}
              aria-label={`打开${app.name}`}
            >
              <AppGlyph app={app} size={46} />
              {app.id === 'approvals' && pending > 0 && <b className="dock-item__badge">{pending}</b>}
              {open && !minimized && <i className="dock-item__dot" aria-hidden="true" />}
              <span className="dock-tip">{app.name}</span>
            </button>
          )
        })}
        <i className="dock__sep" aria-hidden="true" />
        <button className="dock-item dock-library" data-n="应用库"
          onClick={() => setLibraryOpen(true)} aria-label="打开应用库">
          <span className="dock-library__tile"><SquaresFour aria-hidden="true" /></span>
          <span className="dock-tip">应用库 · 长按图标管理</span>
        </button>
      </div>

      {reordering && gesture.current && APPS[reordering as AppId] && createPortal(
        <div className="dock-ghost" ref={ghostRef} aria-hidden="true"
          style={{ transform: `translate3d(${gesture.current.startX - 28}px, ${gesture.current.startY - 36}px, 0)` }}>
          <AppGlyph app={APPS[reordering as AppId]} size={52} />
        </div>,
        document.body
      )}

      {menu && menuApp && (
        <div className="dock-context glass-opaque" onPointerDown={(event) => event.stopPropagation()} style={{
          left: Math.max(8, Math.min(window.innerWidth - 190, menu.x - 90)),
          top: Math.max(8, menu.y - 150)
        }}>
          <div className="dock-context__title">
            <AppGlyph app={menuApp} size={24} />
            <span>{menuApp.name}</span>
          </div>
          <button onClick={() => { setMenu(null); openAppGuarded(menu.appId) }}>打开</button>
          <button disabled={menu.appId === 'finder'} onClick={() => {
            removeFromDock(menu.appId)
            setMenu(null)
            toast(`${menuApp.name} 已从程序坞移除`, 'ok')
          }}>
            <Trash2 size={12} /> {menu.appId === 'finder' ? '访达不可移除' : '从程序坞移除'}
          </button>
        </div>
      )}
    </div>
  )
}
