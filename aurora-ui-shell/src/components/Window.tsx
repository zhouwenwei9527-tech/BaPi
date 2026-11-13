import { useRef } from 'react'
import { Maximize2, Minimize2, Minus, X } from './icons'
import { APPS } from '../data/apps'
import { useShell, type WindowState } from '../store/shell'
import { AppRouter } from './AppRouter'

export function Window({ win }: { win: WindowState }) {
  const app = APPS[win.appId]
  const focused = useShell((s) => s.focused === win.appId)
  const focus = useShell((s) => s.focusWindow)
  const close = useShell((s) => s.closeWindow)
  const minimize = useShell((s) => s.minimizeWindow)
  const maximize = useShell((s) => s.toggleMaximize)
  const updateBounds = useShell((s) => s.updateBounds)
  const dragging = useRef(false)

  const startDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    if ((event.target as HTMLElement).closest('button, .traffic, .window__tools')) return
    if (win.maximized) return
    focus(win.appId)
    const startX = event.clientX
    const startY = event.clientY
    const originX = win.x
    const originY = win.y
    const target = event.currentTarget
    target.setPointerCapture(event.pointerId)
    dragging.current = true
    const move = (e: PointerEvent) => {
      if (!dragging.current) return
      const x = Math.max(-win.w + 120, Math.min(window.innerWidth - 100, originX + e.clientX - startX))
      const y = Math.max(0, Math.min(window.innerHeight - 80, originY + e.clientY - startY))
      updateBounds(win.appId, { x, y })
    }
    const up = () => {
      dragging.current = false
      target.removeEventListener('pointermove', move)
      target.removeEventListener('pointerup', up)
      target.removeEventListener('pointercancel', up)
    }
    target.addEventListener('pointermove', move)
    target.addEventListener('pointerup', up)
    target.addEventListener('pointercancel', up)
  }

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    event.stopPropagation()
    if (win.maximized) return
    focus(win.appId)
    const startX = event.clientX
    const startY = event.clientY
    const originW = win.w
    const originH = win.h
    const target = event.currentTarget
    target.setPointerCapture(event.pointerId)
    const move = (e: PointerEvent) => {
      const w = Math.max(app.minSize.w, Math.min(window.innerWidth - 24, originW + e.clientX - startX))
      const h = Math.max(app.minSize.h, Math.min(window.innerHeight - 100, originH + e.clientY - startY))
      updateBounds(win.appId, { w, h })
    }
    const up = () => {
      target.removeEventListener('pointermove', move)
      target.removeEventListener('pointerup', up)
      target.removeEventListener('pointercancel', up)
    }
    target.addEventListener('pointermove', move)
    target.addEventListener('pointerup', up)
    target.addEventListener('pointercancel', up)
  }

  const style = win.maximized ? {
    left: 8, top: 6, width: 'calc(100vw - 16px)', height: 'calc(100vh - 104px)', zIndex: win.z
  } : {
    left: win.x, top: win.y, width: win.w, height: win.h, zIndex: win.z
  }

  return (
    <section
      className={`window ${focused ? 'window--focused' : ''} ${win.maximized ? 'window--maximized' : ''} ${win.minimized ? 'window--minimized' : ''}`}
      style={style}
      role="dialog"
      aria-label={`${app.name}窗口`}
      aria-hidden={win.minimized}
      onPointerDown={() => focus(win.appId)}
    >
      <div
        className="window__titlebar"
        onPointerDown={startDrag}
        onDoubleClick={() => maximize(win.appId)}
      >
        <div className="traffic">
          <button className="traffic__light traffic__light--close" aria-label="关闭窗口"
            onClick={(e) => { e.stopPropagation(); close(win.appId) }}><X /></button>
          <button className="traffic__light traffic__light--min" aria-label="最小化窗口"
            onClick={(e) => { e.stopPropagation(); minimize(win.appId) }}><Minus /></button>
          <button className="traffic__light traffic__light--max" aria-label={win.maximized ? '还原窗口' : '最大化窗口'}
            onClick={(e) => { e.stopPropagation(); maximize(win.appId) }}>
            {win.maximized ? <Minimize2 /> : <Maximize2 />}
          </button>
        </div>
        <div className="window__title">{app.name}</div>
        <div className="window__tools" />
      </div>
      <div className="window__body">
        <AppRouter appId={win.appId} />
      </div>
      {!win.maximized && <div className="window__resize" onPointerDown={startResize} aria-hidden="true" />}
    </section>
  )
}
