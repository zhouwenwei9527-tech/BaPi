import { useEffect, useState } from 'react'
import { Loader2, ShieldCheck, X } from './icons'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'

/**
 * Self-service display name. Personal profile changes are direct and do not
 * create an approval; changing someone else still uses the unified execution path.
 */
export function ProfileDialog() {
  const open = useShell((s) => s.profileOpen)
  const setOpen = useShell((s) => s.setProfileOpen)
  const principal = useAuth((s) => s.desktop?.principal)
  const refreshSession = useAuth((s) => s.refreshSession)
  const toast = useShell((s) => s.toast)
  const [displayName, setDisplayName] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) setDisplayName(principal?.displayName ?? '')
  }, [open, principal?.displayName])

  if (!open) return null

  const save = async () => {
    if (!displayName.trim()) {
      toast('显示名称不能为空', 'bad')
      return
    }
    setSaving(true)
    try {
      await api.updateMyProfile(displayName.trim(), '')
      await refreshSession()
      toast('个人资料已更新（无需审批）', 'ok')
      setOpen(false)
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={() => !saving && setOpen(false)}>
      <div className="modal glass glass-opaque" style={{ width: 'min(420px, 94vw)' }}
        onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <span className="login__logo" style={{ width: 42, height: 42, fontSize: 16, margin: 0 }}>
            {principal?.displayName?.slice(0, 1)}
          </span>
          <div style={{ flex: 1 }}>
            <h3 style={{ margin: 0 }}>编辑我的资料</h3>
            <p style={{ margin: 0 }}>@{principal?.username} · {principal?.roles.join(' / ')}</p>
          </div>
          <button className="icon-btn" aria-label="关闭" onClick={() => setOpen(false)}><X size={15} /></button>
        </div>
        <div className="form-grid" style={{ marginTop: 16 }}>
          <label>显示名称</label>
          <input className="input" aria-label="我的显示名称" value={displayName}
            onChange={(e) => setDisplayName(e.target.value)} />
        </div>
        <div className="directory__hint" style={{ marginTop: 12 }}>
          <ShieldCheck size={13} />
          <div>
            <b>本人资料直接生效，无需审批</b>
            <div>修改他人资料、调整角色或删除用户仍然经过统一执行通道与审批。</div>
          </div>
        </div>
        <div className="modal__actions">
          <button className="btn btn--ghost" disabled={saving} onClick={() => setOpen(false)}>取消</button>
          <button className="btn btn--primary" disabled={saving} onClick={() => void save()}>
            {saving ? <Loader2 size={13} className="spin" /> : <ShieldCheck size={13} />} 保存
          </button>
        </div>
      </div>
    </div>
  )
}
