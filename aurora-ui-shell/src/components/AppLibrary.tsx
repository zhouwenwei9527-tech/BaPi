import { APP_LIST } from '../data/apps'
import { Check, Plus, RefreshCw, SquaresFour, X } from './icons'
import { AppGlyph } from './AppGlyph'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'

export function AppLibrary() {
  const open = useShell((s) => s.libraryOpen)
  const setOpen = useShell((s) => s.setLibraryOpen)
  const dockApps = useShell((s) => s.dockApps)
  const addToDock = useShell((s) => s.addToDock)
  const removeFromDock = useShell((s) => s.removeFromDock)
  const resetDock = useShell((s) => s.resetDock)
  const toast = useShell((s) => s.toast)
  const permissions = useAuth((s) => s.desktop?.principal.permissions ?? [])

  if (!open) return null
  const apps = APP_LIST.filter((app) => can(permissions, app.permission))

  return (
    <div className="modal-backdrop" onClick={() => setOpen(false)}>
      <div className="modal app-library glass glass-opaque" onClick={(e) => e.stopPropagation()}>
        <div className="app-library__head">
          <span className="app-library__icon"><SquaresFour size={18} /></span>
          <div style={{ flex: 1 }}>
            <h3>应用库</h3>
            <p>把应用加入程序坞；在程序坞按住图标可以拖拽排序，长按可以移除。访达是固定入口。</p>
          </div>
          <button className="btn btn--ghost" onClick={() => { resetDock(); toast('已恢复默认程序坞', 'ok') }}>
            <RefreshCw size={12} /> 重置布局
          </button>
          <button className="icon-btn" aria-label="关闭应用库" onClick={() => setOpen(false)}><X size={16} /></button>
        </div>

        <div className="app-library__grid">
          {apps.map((app) => {
            const inDock = dockApps.includes(app.id)
            const fixed = app.id === 'finder'
            return (
              <div className="app-library__row" key={app.id}>
                <AppGlyph app={app} size={38} />
                <div className="app-library__text">
                  <b>{app.name}</b>
                  <small>{app.subtitle}</small>
                </div>
                <div className="app-library__actions">
                  <button className={`btn ${inDock ? 'btn--ghost' : ''}`}
                    disabled={fixed}
                    onClick={() => {
                      if (fixed) { toast('访达固定保留在程序坞', 'info'); return }
                      if (inDock) removeFromDock(app.id); else addToDock(app.id)
                    }}>
                    {inDock ? <><Check size={11} /> 在程序坞</> : <><Plus size={11} /> 加入程序坞</>}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
        <div className="app-library__foot muted">
          共 {apps.length} 个可用应用 · 在程序坞按住图标拖拽排序 · 权限变化后不可见应用自动隐藏
        </div>
      </div>
    </div>
  )
}
