import { useEffect, useMemo, useState } from 'react'
import {
  BadgeCheck, Check, Loader2, RefreshCw, ShieldCheck, UserRound, X
} from '../components/icons'
import { requestStatusLabel } from '../lib/labels'
import { api, errorMessage } from '../lib/api'
import { relativeTime } from '../lib/format'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { PermissionRequestView, PermissionRoleOption } from '../types'

// status codes stay in data; labels come from lib/labels
const STATUS_TONE: Record<string, string> = {
  PENDING: 'warn', APPROVED: 'ok', REJECTED: 'bad', FAILED: 'bad'
}

export function Access() {
  const principal = useAuth((s) => s.desktop?.principal)
  const refreshSession = useAuth((s) => s.refreshSession)
  const toast = useShell((s) => s.toast)
  const permissions = principal?.permissions ?? []
  const canApprove = can(permissions, 'approval:decide')
  const [roles, setRoles] = useState<PermissionRoleOption[]>([])
  const [requests, setRequests] = useState<PermissionRequestView[]>([])
  const [selectedRole, setSelectedRole] = useState('')
  const [reason, setReason] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    setLoading(true)
    try {
      const [nextRoles, nextRequests] = await Promise.all([
        api.permissionRoles(), api.permissionRequests()
      ])
      setRoles(nextRoles)
      setRequests(nextRequests)
      setSelectedRole((current) => current || nextRoles.find((r) => !r.alreadyAssigned)?.code || '')
      setError(null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])
  useEffect(() => {
    const timer = setInterval(() => { void load() }, 15_000)
    return () => clearInterval(timer)
  }, [])

  const mine = useMemo(() =>
    requests.filter((request) => request.requesterUserId === principal?.id), [requests, principal?.id])
  const pendingForMe = useMemo(() =>
    requests.filter((request) => request.status === 'PENDING'
      && request.requesterUserId !== principal?.id), [requests, principal?.id])

  const submit = async () => {
    if (!selectedRole) {
      toast('请选择要申请的角色', 'bad')
      return
    }
    if (reason.trim().length < 4) {
      toast('请填写申请理由（至少 4 个字符）', 'bad')
      return
    }
    setSubmitting(true)
    try {
      await api.createPermissionRequest(selectedRole, reason.trim())
      toast('权限申请已提交，等待其他角色审批', 'ok')
      setReason('')
      await load()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setSubmitting(false)
    }
  }

  const decide = async (request: PermissionRequestView, approve: boolean) => {
    setBusy(request.id)
    try {
      await api.decidePermissionRequest(request.id, approve, approve ? '批准权限申请' : '拒绝权限申请')
      toast(approve ? `已授予 ${request.roleName}` : '已拒绝申请', approve ? 'ok' : 'info')
      await load()
      if (request.requesterUserId === principal?.id) {
        await refreshSession()
      }
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="app access-app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">权限申请</span>
        <span className="chip chip--info">{principal?.roles.join(' / ')}</span>
        <span className="chip">{permissions.length} 项权限</span>
        {canApprove && <span className="chip chip--violet"><ShieldCheck size={10} /> 审批人</span>}
        <div className="spacer" />
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}

      <div className="access-grid" tabIndex={0} role="region" aria-label="权限申请面板">
        <section className="access-pane glass">
          <div className="widget__head">
            <span className="widget__title"><UserRound size={12} style={{ verticalAlign: -1 }} /> 申请一个角色</span>
          </div>
          <div className="access-roles">
            {roles.map((role) => (
              <button key={role.code} className="access-role" data-active={selectedRole === role.code}
                disabled={role.alreadyAssigned}
                onClick={() => setSelectedRole(role.code)}>
                <span>
                  <b>{role.name} <span className="mono">{role.code}</span></b>
                  <small>{role.description || '角色'}</small>
                </span>
                {role.alreadyAssigned
                  ? <span className="chip chip--ok">已拥有</span>
                  : <span className="perm-dot" data-on={selectedRole === role.code} />}
              </button>
            ))}
            {roles.length === 0 && <div className="muted" style={{ fontSize: 11 }}>没有可申请的角色。</div>}
          </div>
          <label className="eyebrow" style={{ display: 'block', margin: '12px 0 4px' }}>申请理由</label>
          <textarea className="code-input" aria-label="申请理由" style={{ minHeight: 70, fontFamily: 'var(--font-sans)' }}
            value={reason} placeholder="说明你需要这个角色完成什么工作…"
            onChange={(e) => setReason(e.target.value)} />
          <button className="btn btn--primary" style={{ width: '100%', marginTop: 8 }}
            disabled={submitting || !selectedRole} onClick={() => void submit()}>
            {submitting ? <Loader2 size={13} className="spin" /> : <ShieldCheck size={13} />}
            提交申请
          </button>
          <div className="directory__hint" style={{ marginTop: 10 }}>
            <ShieldCheck size={13} />
            <div>
              <b>管理员与安全审计员角色不可申请</b>
              <div>申请提交后由另一位具备 approval:decide 的成员审批；批准后重新登录或点击下方刷新身份即可切换新角色。</div>
            </div>
          </div>
          <button className="btn btn--ghost" style={{ width: '100%', marginTop: 7 }}
            onClick={() => void refreshSession().then(() => toast('身份已刷新', 'ok'))}>
            <RefreshCw size={12} /> 刷新身份 / 可用角色
          </button>
        </section>

        <section className="access-pane glass">
          <div className="widget__head">
            <span className="widget__title"><BadgeCheck size={12} style={{ verticalAlign: -1 }} /> 我的申请</span>
            <span className="chip">{mine.length}</span>
          </div>
          <div className="access-list">
            {mine.map((request) => (
              <div className="access-request" key={request.id}>
                <div>
                  <b>{request.roleName}</b>
                  <small>{request.reason || '未填写理由'} · {relativeTime(request.createdAt)}</small>
                </div>
                <span className={`chip chip--${STATUS_TONE[request.status] ?? ''}`}>{requestStatusLabel(request.status)}</span>
                {request.decidedBy && <small>审批人 {request.decidedBy}</small>}
              </div>
            ))}
            {mine.length === 0 && <div className="muted" style={{ fontSize: 11 }}>还没有申请记录。</div>}
          </div>

          {canApprove && (
            <>
              <div className="widget__head" style={{ marginTop: 14 }}>
                <span className="widget__title"><ShieldCheck size={12} style={{ verticalAlign: -1 }} /> 待我审批</span>
                <span className="chip chip--warn">{pendingForMe.length}</span>
              </div>
              <div className="access-list">
                {pendingForMe.map((request) => (
                  <div className="access-request access-request--approve" key={request.id}>
                    <div style={{ minWidth: 0 }}>
                      <b>{request.requesterName} 申请 {request.roleName}</b>
                      <small>{request.reason || '未填写理由'} · {relativeTime(request.createdAt)}</small>
                    </div>
                    <div className="access-request__actions">
                      <button className="btn btn--primary" disabled={busy === request.id}
                        onClick={() => void decide(request, true)}>
                        {busy === request.id ? <Loader2 size={11} className="spin" /> : <Check size={11} />} 批准
                      </button>
                      <button className="btn" disabled={busy === request.id}
                        onClick={() => void decide(request, false)}>
                        <X size={11} /> 拒绝
                      </button>
                    </div>
                  </div>
                ))}
                {pendingForMe.length === 0 && (
                  <div className="muted" style={{ fontSize: 11 }}>没有待审批的权限申请。</div>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  )
}
