import { useEffect, useMemo, useState } from 'react'
import {
  BadgeCheck, Check, ChevronDown, ChevronRight, Clock3, Loader2, RefreshCw, ShieldAlert,
  ShieldCheck, Sparkles, ThumbsDown, ThumbsUp, UserRound, X
} from '../components/icons'
import { api, errorMessage } from '../lib/api'
import { formatDateTime, relativeTime, shortId, sideEffectTone } from '../lib/format'
import {
  approvalStatusLabel, decisionLabel, riskLevelLabel, sideEffectLabel
} from '../lib/labels'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { ApprovalView, DecisionOutcome } from '../types'

const FILTERS = [
  { id: 'PENDING', label: '待处理' },
  { id: 'ALL', label: '全部' },
  { id: 'EXECUTED', label: '已执行' },
  { id: 'REJECTED', label: '已拒绝' }
]

const STATUS_TONE: Record<string, string> = {
  PENDING: 'warn', APPROVED: 'info', EXECUTED: 'ok', REJECTED: 'bad',
  FAILED: 'bad', EXPIRED: 'bad', CANCELLED: 'bad'
}

interface RiskPrompt {
  ids: string[]
  title: string
  items: ApprovalView[]
}

export function Approvals() {
  const principal = useAuth((s) => s.desktop?.principal)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const toast = useShell((s) => s.toast)
  const openApp = useShell((s) => s.openAppGuarded)
  const permissions = principal?.permissions ?? []
  const canDecide = can(permissions, 'approval:decide')
  const [items, setItems] = useState<ApprovalView[]>([])
  const [filter, setFilter] = useState('PENDING')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [expanded, setExpanded] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [riskPrompt, setRiskPrompt] = useState<RiskPrompt | null>(null)
  const [riskChecked, setRiskChecked] = useState(false)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.approvals(filter === 'ALL' ? undefined : filter)
      setItems(data)
      setSelected((prev) => new Set([...prev].filter((id) => data.some((item) =>
        item.id === id && item.status === 'PENDING'
        && item.requesterUserId !== principal?.id))))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [filter])
  useEffect(() => {
    const timer = setInterval(() => { void load() }, 20_000)
    return () => clearInterval(timer)
  }, [filter])

  const decide = async (item: ApprovalView, approve: boolean, confirmRisk = false) => {
    if (approve && item.requiresConfirmation && !confirmRisk) {
      setRiskChecked(false)
      setRiskPrompt({ ids: [item.id], title: item.intentSummary, items: [item] })
      return
    }
    setBusy(item.id)
    try {
      const result = await api.decideApproval(item.id, approve,
        approve ? '在审批队列中批准' : '在审批队列中拒绝', confirmRisk)
      const executed = result.execution?.status === 'EXECUTED'
      if (approve && result.errorCode) {
        toast(`批准失败：${decisionLabel(result.errorCode)}`, 'bad')
      } else {
        toast(approve
          ? (executed ? `${item.toolName} 已执行：${result.execution?.summary ?? '完成'}` : '审批已通过')
          : '审批已拒绝，业务数据未变更',
          approve ? 'ok' : 'info')
      }
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  const batch = async (approve: boolean, confirmRisk = false) => {
    const ids = [...selected]
    if (!ids.length) return
    const chosen = items.filter((item) => ids.includes(item.id))
    if (approve && chosen.some((item) => item.requiresConfirmation) && !confirmRisk) {
      setRiskChecked(false)
      setRiskPrompt({
        ids,
        title: `${chosen.filter((item) => item.requiresConfirmation).length} 条操作可操作度较低`,
        items: chosen
      })
      return
    }
    setBusy('batch')
    try {
      const results: DecisionOutcome[] = await api.batchDecide(ids, approve,
        approve ? '批量批准' : '批量拒绝', confirmRisk)
      const failed = results.filter((result) => result.errorCode || result.error).length
      const success = results.length - failed
      toast(`批量${approve ? '批准' : '拒绝'}：${success} 成功，${failed} 失败`,
        failed ? 'bad' : approve ? 'ok' : 'info')
      if (failed > 0) {
        const first = results.find((result) => result.errorCode || result.error)
        if (first) toast(decisionLabel(first.errorCode ?? first.error ?? '部分条目失败'), 'bad')
      }
      setSelected(new Set())
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  const confirmRisk = async () => {
    if (!riskPrompt || !riskChecked) return
    const prompt = riskPrompt
    setRiskPrompt(null)
    setRiskChecked(false)
    if (prompt.ids.length === 1) {
      await decide(prompt.items[0], true, true)
    } else {
      await batch(true, true)
    }
  }

  const pendingCount = items.filter((item) => item.status === 'PENDING').length
  const selectable = useMemo(() => items.filter((item) => item.status === 'PENDING'), [items])

  return (
    <div className="app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">审批队列</span>
        <span className="chip chip--warn">{pendingCount} 待处理</span>
        {!canDecide && <span className="chip chip--bad"><ShieldAlert size={10} /> 缺少审批权限</span>}
        <div className="spacer" />
        <div className="segmented">
          {FILTERS.map((item) => (
            <button key={item.id} aria-pressed={filter === item.id} onClick={() => setFilter(item.id)}>{item.label}</button>
          ))}
        </div>
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      <div className="flow-notice approval-origin">
        <ShieldCheck size={14} />
        <div className="approval-origin__text">
          <b>审批从哪里来？</b>{' '}
          审批不能手工新建：只有<b>角色变更</b>等提权操作需要另一位成员复核；权限内的写操作由本人确认风险后直接执行。
          执行前会重新校验权限、激活状态与参数指纹。
          <small>发起人不能自批 · 演示审批人 auditor / audit123</small>
        </div>
        <div className="approval-origin__actions">
          <button className="btn btn--ghost" onClick={() => openApp('agent')}>
            <Sparkles size={12} /> 去 Agent
          </button>
          <button className="btn btn--ghost" onClick={() => openApp('tools')}>工具注册表</button>
          <button className="btn btn--ghost" onClick={() => openApp('access')}>权限申请</button>
        </div>
      </div>

      {selected.size > 0 && (
        <div className="appr-toolbar" style={{ background: 'var(--accent-soft)' }}>
          <b style={{ fontSize: 11.5 }}>已选 {selected.size} 条</b>
          <button className="btn btn--primary" disabled={busy === 'batch'} onClick={() => void batch(true)}>
            <ThumbsUp size={12} /> 批量批准
          </button>
          <button className="btn" disabled={busy === 'batch'} onClick={() => void batch(false)}>
            <ThumbsDown size={12} /> 批量拒绝
          </button>
          <button className="btn btn--ghost" onClick={() => setSelected(new Set())}><X size={12} /> 取消</button>
          <span className="muted" style={{ fontSize: 10.5 }}>高风险条目会要求二次确认；自批条目已禁用</span>
        </div>
      )}

      <div className="app-scroll app-scroll--flush" style={{ overflow: 'auto' }}>
        {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}
        {loading && !items.length && <div className="app-loading"><Loader2 size={15} className="spin" /> 读取审批…</div>}
        {!loading && items.length === 0 && (
          <div className="app-empty">
            <span className="app-empty__icon"><BadgeCheck size={22} /></span>
            <b>没有{filter === 'PENDING' ? '待处理' : ''}审批</b>
            <small>当前的常规写操作不再需要他人审批。只有角色变更等提权操作会出现；你也可以在权限申请里邀请其他角色审批。</small>
            <button className="btn btn--primary" style={{ marginTop: 6 }} onClick={() => openApp('access')}>
              <ShieldCheck size={12} /> 去权限申请
            </button>
          </div>
        )}
        <div className="appr-grid">
          {items.map((item) => {
            const selfForbidden = item.requesterUserId === principal?.id && item.sideEffect !== 'READ'
            const expired = item.status === 'PENDING' && new Date(item.expiresAt).getTime() < Date.now()
            const canAct = canDecide && item.status === 'PENDING' && !selfForbidden && !expired
            return (
              <article className="appr-card" data-status={item.status} key={item.id}>
                <div className="appr-card__head">
                  {item.status === 'PENDING' && (
                    <input type="checkbox" className="appr-check" aria-label="选择审批"
                      disabled={selfForbidden}
                      title={selfForbidden ? '不能选择自己发起的审批' : undefined}
                      checked={selected.has(item.id)}
                      onChange={(e) => {
                        const next = new Set(selected)
                        if (e.target.checked) next.add(item.id); else next.delete(item.id)
                        setSelected(next)
                      }} />
                  )}
                  <b>{item.toolName}</b>
                  <span className={`chip chip--${sideEffectTone(item.sideEffect)}`}>
                    {sideEffectLabel(item.sideEffect)}
                  </span>
                  <span className="appr-hash">参数指纹 {item.argsHash}</span>
                  <div className="spacer" />
                  <span className={`chip chip--${STATUS_TONE[item.status] ?? ''}`}>
                    {approvalStatusLabel(item.status)}
                  </span>
                  <button className="icon-btn" style={{ width: 22, height: 22 }} aria-label="展开详情"
                    onClick={() => setExpanded(expanded === item.id ? null : item.id)}>
                    {expanded === item.id ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                  </button>
                </div>

                <div className="appr-intent">{item.intentSummary || '执行一次受控操作'}</div>
                <div className="appr-risk">
                  <span className={`chip risk-chip risk-${(item.riskLevel || 'MEDIUM').toLowerCase()}`}>
                    {riskLevelLabel(item.riskLevel, item.operability)} · 可操作度 {item.operability}%
                  </span>
                  <div className="risk-track">
                    <div className={`risk-fill risk-${(item.riskLevel || 'MEDIUM').toLowerCase()}`}
                      style={{ width: `${item.operability}%` }} />
                  </div>
                  {item.riskReasons && <span className="appr-risk__reasons">{item.riskReasons}</span>}
                  {item.requiresConfirmation && <span className="chip chip--bad">批准需二次确认</span>}
                </div>

                <div className="appr-meta">
                  <span>发起人</span>
                  <i style={{ fontStyle: 'normal' }}>
                    <UserRound size={10} style={{ verticalAlign: -1 }} /> {item.requesterName}
                    {item.actorType === 'AGENT' && <span className="muted">（Agent 代表）</span>}
                  </i>
                  <span>参数摘要</span>
                  <i className="mono" style={{ fontStyle: 'normal', fontSize: 10.5 }}>{item.argsSummary}</i>
                  <span>影响对象</span>
                  <i style={{ fontStyle: 'normal' }}>{item.affectedObjects ?? '—'} · 预计 {item.affectedCount} 条</i>
                  <span>回滚性</span>
                  <i style={{ fontStyle: 'normal' }}>{item.reversible}</i>
                  <span>创建 / 过期</span>
                  <i style={{ fontStyle: 'normal' }}>{relativeTime(item.createdAt)} · {formatDateTime(item.expiresAt)}</i>
                </div>

                {expanded === item.id && (
                  <div className="gateway-steps" style={{ marginBottom: 9 }}>
                    <div className="gateway-step"><span>轨迹</span><span className="mono">{item.traceId}</span></div>
                    <div className="gateway-step"><span>会话</span><span className="mono">{item.sessionId ?? '—'}</span></div>
                    <div className="gateway-step"><span>工具版本</span><span>{item.toolVersion}</span></div>
                    <div className="gateway-step"><span>决策人</span><span>{item.decidedBy ?? '—'} {item.decidedAt ? `· ${formatDateTime(item.decidedAt)}` : ''}</span></div>
                    {item.executedAt && <div className="gateway-step"><span>执行时间</span><span>{formatDateTime(item.executedAt)}</span></div>}
                    {item.errorCode && <div className="gateway-step"><span>错误码</span><span style={{ color: 'var(--bad)' }} className="mono">{decisionLabel(item.errorCode)}</span></div>}
                    {item.resultSummary && <div className="gateway-result">{item.resultSummary}</div>}
                    {item.note && <div className="gateway-step"><span>备注</span><span>{item.note}</span></div>}
                  </div>
                )}

                <div className="appr-actions">
                  {item.status === 'PENDING' ? (
                    <>
                      <button className="btn btn--primary" disabled={!canAct || busy === item.id}
                        onClick={() => void decide(item, true)}
                        title={selfForbidden ? '职责分离：不能批准自己发起的写操作' : undefined}>
                        {busy === item.id ? <Loader2 size={12} className="spin" /> : <Check size={13} />} 批准并执行
                      </button>
                      <button className="btn" disabled={!canAct || busy === item.id} onClick={() => void decide(item, false)}>
                        <X size={13} /> 拒绝
                      </button>
                      {selfForbidden && <span className="chip chip--warn"><ShieldAlert size={10} /> 职责分离：不得自批</span>}
                      {!canDecide && <span className="muted" style={{ fontSize: 10.5 }}>缺少审批权限</span>}
                      {expired && <span className="chip chip--bad"><Clock3 size={10} /> 已过期</span>}
                      <span className="muted" style={{ marginLeft: 'auto', fontSize: 10.5 }}>
                        执行前会重新校验权限、激活状态与参数指纹
                      </span>
                    </>
                  ) : (
                    <span className="muted" style={{ fontSize: 10.5 }}>
                      {item.status === 'EXECUTED' ? '已执行并写入审计' : `状态：${approvalStatusLabel(item.status)}`}
                      {item.decidedBy ? ` · 决策人 ${item.decidedBy}` : ''}
                    </span>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      </div>

      {riskPrompt && (
        <div className="modal-backdrop" onClick={() => !busy && setRiskPrompt(null)}>
          <div className="modal glass glass-opaque" onClick={(e) => e.stopPropagation()}>
            <h3>高风险操作 · 需要二次确认</h3>
            <p>{riskPrompt.title}。批准后立即执行，部分操作不可回滚。</p>
            <div className="risk-confirm-list">
              {riskPrompt.items.map((item) => (
                <div key={item.id}>
                  <b>{riskLevelLabel(item.riskLevel, item.operability)} · 可操作度 {item.operability}%</b>
                  <span>{item.intentSummary}</span>
                  {item.riskReasons && <small>{item.riskReasons}</small>}
                </div>
              ))}
            </div>
            <label className="risk-check">
              <input type="checkbox" checked={riskChecked}
                onChange={(e) => setRiskChecked(e.target.checked)} />
              我已了解以上风险，确认批准并执行
            </label>
            <div className="modal__actions">
              <button className="btn btn--ghost" onClick={() => setRiskPrompt(null)}>取消</button>
              <button className="btn btn--danger" disabled={!riskChecked || busy === 'batch'}
                onClick={() => void confirmRisk()}>
                <ShieldAlert size={13} /> 确认批准
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
