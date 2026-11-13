import { useEffect, useState } from 'react'
import { ArrowUpRight, BadgeCheck, ShieldCheck, Sparkles } from './icons'
import { sideEffectLabel } from '../lib/labels'
import { api } from '../lib/api'
import { formatTime, scopeLabel } from '../lib/format'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { ApprovalView } from '../types'

export function DesktopWidgets() {
  const desktop = useAuth((s) => s.desktop)
  const openAppGuarded = useShell((s) => s.openAppGuarded)
  const [pending, setPending] = useState<ApprovalView[]>([])

  useEffect(() => {
    if (!desktop) return
    if (!can(desktop.principal.permissions, 'approval:read')) return
    let alive = true
    const load = () => {
      api.approvals('PENDING').then((rows) => alive && setPending(rows)).catch(() => undefined)
    }
    load()
    const timer = setInterval(load, 25_000)
    return () => { alive = false; clearInterval(timer) }
  }, [desktop])

  if (!desktop) return null
  const { principal, stats } = desktop
  // 自批被禁止：自己发起的审批不会出现在这里。
  const pendingOthers = pending.filter((item) => item.requesterUserId !== principal.id)

  return (
    <div className="desktop__widgets">
      <section className="widget glass glass--enhanced">
        <div className="widget__head">
          <span className="widget__title">控制面 · 执行面</span>
          <span className="scope-pill"><ShieldCheck size={11} /> {scopeLabel(principal.dataScope.type)}</span>
        </div>
        <div className="widget__name">{principal.displayName}</div>
        <div className="widget__sub">{principal.title} · @{principal.username}</div>
        <div className="kpis">
          <div className="kpi"><b>{stats.pendingApprovals}</b><small>待审批</small></div>
          <div className="kpi"><b>{stats.tools}</b><small>已注册 Tool</small></div>
          <div className="kpi"><b>{stats.auditEvents}</b><small>审计事件</small></div>
        </div>
        <div className="widget__meta">
          <span>{desktop.activeRole} · {principal.roles.join(' / ')}</span>
          <button className="btn btn--ghost" style={{ height: 22, padding: '0 7px', fontSize: 10 }}
            onClick={() => openAppGuarded('guide')}>数据流</button>
          <button className="icon-btn" style={{ width: 22, height: 22 }} aria-label="打开审批队列"
            onClick={() => openAppGuarded('approvals')}>
            <ArrowUpRight size={13} />
          </button>
        </div>
      </section>

      {can(principal.permissions, 'approval:read') && (
        <section className="widget glass">
          <div className="widget__head">
            <span className="widget__title">审批队列</span>
            <span className="chip chip--warn">{pendingOthers.length}</span>
          </div>
          {pendingOthers.length === 0 ? (
            <div className="muted" style={{ fontSize: 11.5, padding: '6px 0 2px' }}>
              没有待处理审批。只有角色变更等提权操作会出现在这里。
            </div>
          ) : (
            <div className="pending-mini">
              {pendingOthers.slice(0, 2).map((item) => (
                <button key={item.id} className="pending-mini__row" onClick={() => openAppGuarded('approvals')}>
                  <BadgeCheck size={14} style={{ color: 'var(--warn)' }} />
                  <span style={{ textAlign: 'left', minWidth: 0 }}>
                    <b>{item.intentSummary || item.toolName}</b>
                    <small style={{ display: 'block', color: 'var(--muted)' }}>
                      {item.requesterName} · {sideEffectLabel(item.sideEffect)}
                    </small>
                  </span>
                  <small>{formatTime(item.createdAt)}</small>
                </button>
              ))}
              {pending.length > 2 && (
                <div className="muted" style={{ fontSize: 10.5, paddingLeft: 4 }}>
                  还有 {pendingOthers.length - 2} 条…
                </div>
              )}
            </div>
          )}
          <div className="widget__meta">
            <span><Sparkles size={11} style={{ verticalAlign: -1 }} /> 审批绑定操作参数</span>
            <button className="btn btn--ghost" style={{ height: 24, padding: '0 9px', fontSize: 10.5 }}
              onClick={() => openAppGuarded('approvals')}>查看全部</button>
          </div>
        </section>
      )}
    </div>
  )
}
