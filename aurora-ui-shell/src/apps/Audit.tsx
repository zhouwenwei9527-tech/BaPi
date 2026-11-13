import { useEffect, useMemo, useState } from 'react'
import {
  CheckCircle2, Clock3, GitBranch, History, Loader2, Play, RefreshCw, ShieldCheck,
  ShieldX, Terminal
} from '../components/icons'
import { api, errorMessage } from '../lib/api'
import { decisionTone, formatDateTime, formatTime, shortId } from '../lib/format'
import { actorTypeLabel, decisionLabel } from '../lib/labels'
import { useShell } from '../store/shell'
import type { AuditEvent, ChainVerification, ReplayView } from '../types'

const TYPE_LABEL: Record<string, string> = {
  TOOL_CALL: 'Tool 调用', APPROVAL: '审批', RETRIEVAL: '检索', PERMISSION: '权限',
  MODEL_CALL: '模型调用', BUDGET: '预算', PLUGIN: '插件'
}

const TYPE_TONE: Record<string, string> = {
  TOOL_CALL: 'info', APPROVAL: 'warn', RETRIEVAL: 'ok', PERMISSION: 'violet',
  MODEL_CALL: '', BUDGET: 'warn', PLUGIN: 'violet'
}

export function Audit() {
  const toast = useShell((s) => s.toast)
  const [events, setEvents] = useState<AuditEvent[]>([])
  const [verification, setVerification] = useState<ChainVerification | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<AuditEvent | null>(null)
  const [replay, setReplay] = useState<ReplayView | null>(null)
  const [replayLoading, setReplayLoading] = useState(false)
  const [typeFilter, setTypeFilter] = useState('ALL')
  const [search, setSearch] = useState('')

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const [rows, chain] = await Promise.all([api.audit(160), api.verifyAudit()])
      setEvents(rows)
      setVerification(chain)
      setSelected((current) => current ?? rows.find((event) => event.type === 'TOOL_CALL' && event.traceId) ?? rows.find((event) => event.traceId) ?? rows[0] ?? null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  useEffect(() => {
    if (!selected?.traceId) { setReplay(null); return }
    let alive = true
    setReplayLoading(true)
    api.replay(selected.traceId)
      .then((data) => alive && setReplay(data))
      .catch(() => alive && setReplay(null))
      .finally(() => alive && setReplayLoading(false))
    return () => { alive = false }
  }, [selected?.traceId])

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase()
    return events.filter((event) => {
      if (typeFilter !== 'ALL' && event.type !== typeFilter) return false
      if (!needle) return true
      return `${event.toolName ?? ''} ${event.username ?? ''} ${event.decision ?? ''} ${event.traceId ?? ''}`
        .toLowerCase().includes(needle)
    })
  }, [events, typeFilter, search])

  const traceCount = useMemo(() => new Set(events.map((e) => e.traceId).filter(Boolean)).size, [events])

  return (
    <div className="audit">
      <section className="audit__list">
        <div className="app-toolbar" style={{ height: 44 }}>
          <span className="app-toolbar__title">审计事件</span>
          <span className="chip chip--info">{events.length}</span>
          <div className="spacer" />
          <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
          </button>
        </div>
        <div className={`audit__verify ${verification?.valid === false ? '' : ''}`} data-valid={verification?.valid ?? true}>
          {verification?.valid ? <ShieldCheck size={14} /> : <ShieldX size={14} />}
          {verification ? verification.message : '正在校验哈希链…'}
          <span className="mono" style={{ marginLeft: 'auto', fontSize: 10 }}>{verification?.checked ?? 0} events</span>
        </div>
        <div style={{ display: 'flex', gap: 6, padding: '0 12px 9px' }}>
          <input className="input" placeholder="搜索 Tool / 用户 / trace…" value={search}
            onChange={(e) => setSearch(e.target.value)} style={{ height: 30 }} />
          <select className="input" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} style={{ width: 100, height: 30 }}>
            <option value="ALL">全部类型</option>
            {Object.entries(TYPE_LABEL).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
        </div>
        <div className="audit__rows">
          {loading && !events.length && <div className="app-loading"><Loader2 size={15} className="spin" /> 读取审计链…</div>}
          {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}
          {filtered.map((event) => (
            <button key={event.seq} className="audit-row" aria-pressed={selected?.seq === event.seq}
              onClick={() => setSelected(event)}>
              <time>{formatTime(event.ts, true)}</time>
              <span>
                <b>{event.toolName ?? TYPE_LABEL[event.type] ?? event.type}</b>
                <small>{event.username ?? 'system'} · {actorTypeLabel(event.actorType)}{event.decision ? ` · ${decisionLabel(event.decision)}` : ''}</small>
              </span>
              <span className={`chip chip--${TYPE_TONE[event.type] ?? ''} audit-row__badge`}>
                {TYPE_LABEL[event.type] ?? event.type}
              </span>
            </button>
          ))}
          {!loading && !filtered.length && (
            <div className="app-empty"><b>没有匹配事件</b><small>调整筛选条件，或执行一次 Agent 操作。</small></div>
          )}
        </div>
      </section>

      <section className="audit__detail">
        <div className="audit-summary">
          <div><b>{events.length}</b><small>事件总数</small></div>
          <div><b>{traceCount}</b><small>可回放轨迹</small></div>
          <div><b>{verification?.valid ? 'OK' : 'BROKEN'}</b><small>哈希链</small></div>
        </div>
        {selected ? (
          <>
            <h3>{selected.toolName ?? TYPE_LABEL[selected.type] ?? selected.type}</h3>
            <div className="audit__detail-sub">
              #{selected.seq} · {formatDateTime(selected.ts)} · {selected.username ?? 'system'} · {actorTypeLabel(selected.actorType)}
              {selected.decision ? ` · ${decisionLabel(selected.decision)}` : ''} · {selected.status === 'OK' ? '正常' : '异常'}
            </div>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }}>
              <span className={`chip chip--${decisionTone(selected.decision)}`}>{decisionLabel(selected.decision ?? selected.status)}</span>
              <span className="chip mono">轨迹 {shortId(selected.traceId, 16)}</span>
              {selected.approvalId && <span className="chip chip--warn">审批 {shortId(selected.approvalId)}</span>}
              {selected.latencyMs != null && <span className="chip">{selected.latencyMs}ms</span>}
              <button className="btn btn--ghost" style={{ height: 24, fontSize: 10.5 }}
                onClick={() => void load()}>
                <RefreshCw size={11} /> 重新校验
              </button>
            </div>

            <div className="chain">
              {(replay?.trace ?? []).map((step) => (
                <div key={step.seq} className={`chain-item ${step.status === 'bad' ? 'chain-item--bad' : step.status === 'warn' ? 'chain-item--warn' : ''}`}>
                  <b>{step.stage}</b>
                  <small>{step.status === 'ok' ? '正常' : step.status === 'warn' ? '警告' : '异常'} · {formatTime(step.ts, true)} {step.decision ? `· ${decisionLabel(step.decision)}` : ''}</small>
                </div>
              ))}
              {replayLoading && <div className="app-loading"><Loader2 size={13} className="spin" /> 回放轨迹…</div>}
              {!replayLoading && !replay?.trace.length && (
                <div className="app-empty" style={{ minHeight: 120, padding: 14 }}>
                  <History size={18} />
                  <b>这条事件没有可视化轨迹</b>
                  <small>选择一条带 traceId 的 Tool 调用事件查看完整管线。</small>
                </div>
              )}
            </div>

            {replay && (
              <>
                <div className="eyebrow" style={{ margin: '12px 0 6px' }}>
                  <Terminal size={10} style={{ verticalAlign: -1 }} /> 回放摘要（不重新调用模型）
                </div>
                <div className="gateway-steps">
                  <div className="gateway-step"><span>tool 序列</span>
                    <span>{replay.toolSequence.length ? replay.toolSequence.join(' → ') : '—'}</span></div>
                  <div className="gateway-step"><span>模型调用</span>
                    <span>{replay.modelCalls.length} 次</span></div>
                  <div className="gateway-step"><span>审计事件</span>
                    <span>{replay.audit.length} 条</span></div>
                </div>
                {replay.audit.length > 0 && (
                  <div style={{ marginTop: 10 }}>
                    {replay.audit.map((event) => (
                      <div key={event.seq} className="chain-item">
                        <b>{decisionLabel(event.decision ?? event.status)}</b>
                        <small>{TYPE_LABEL[event.type] ?? event.type} · {formatDateTime(event.ts)} · {event.toolName ?? '—'}</small>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}

            <div className="eyebrow" style={{ margin: '14px 0 6px' }}>
              <GitBranch size={10} style={{ verticalAlign: -1 }} /> 哈希链
            </div>
            <div className="hash-line">prev {selected.prevHash ?? 'GENESIS'}</div>
            <div className="hash-line">hash {selected.hash}</div>
            <div className="hash-line">args {selected.argsDigest ?? '—'}</div>

            <div style={{ display: 'flex', gap: 6, marginTop: 12, flexWrap: 'wrap' }}>
              {selected.traceId && (
                <button className="btn btn--primary" onClick={() => {
                  navigator.clipboard?.writeText(selected.traceId!)
                  toast('traceId 已复制，可用于确定性回放', 'ok')
                }}>
                  <Play size={12} /> 复制 traceId 回放
                </button>
              )}
              <button className="btn btn--ghost" onClick={() => {
                navigator.clipboard?.writeText(selected.hash)
                toast('事件哈希已复制', 'ok')
              }}><CheckCircle2 size={12} /> 复制哈希</button>
            </div>
          </>
        ) : (
          <div className="app-empty">
            <span className="app-empty__icon"><Clock3 size={20} /></span>
            <b>选择一条事件</b>
            <small>右侧会显示哈希链与确定性回放时间线。</small>
          </div>
        )}
      </section>
    </div>
  )
}
