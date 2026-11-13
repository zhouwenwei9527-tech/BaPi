import { useEffect, useMemo, useState } from 'react'
import { BadgeCheck, Loader2, Play, RefreshCw, ShieldAlert, Terminal, Wrench } from '../components/icons'
import { decisionLabel, policyLabel, sideEffectLabel, sourceLabel, toolStatusLabel } from '../lib/labels'
import { api, errorMessage } from '../lib/api'
import { sideEffectTone } from '../lib/format'
import { useShell } from '../store/shell'
import type { GatewayOutcome, ToolView } from '../types'

const EXAMPLES: Record<string, Record<string, unknown>> = {
  'user.search': { query: '', limit: 10 },
  'user.assignRole': { username: '李四', roleCode: 'ops' },
  'user.create': { username: 'newmember', displayName: '新成员', title: '成员', deptId: 5, password: 'test1234', roleCode: 'guest' },
  'user.update': { username: 'wangwu', displayName: '王五', title: '运营专员', deptId: 5, status: 'ACTIVE' },
  'user.delete': { username: '张三' },
  'file.share': { docId: 'd1', to: 'partner@lab.example', expiresHours: 24 },
  'knowledge.search': { query: '数据范围', limit: 5 },
  'audit.verify': {},
  'system.stats': {},
  'paper.search': { query: 'retrieval', limit: 5 },
  'dataset.import': { datasetName: '催化剂筛选-批次A', rows: 100 }
}

function example(tool: ToolView): string {
  return JSON.stringify(EXAMPLES[tool.name] ?? {}, null, 2)
}

export function Tools() {
  const toast = useShell((s) => s.toast)
  const openApp = useShell((s) => s.openAppGuarded)
  const [tools, setTools] = useState<ToolView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<ToolView | null>(null)
  const [args, setArgs] = useState('{}')
  const [session, setSession] = useState('manual-demo')
  const [outcome, setOutcome] = useState<GatewayOutcome | null>(null)
  const [invoking, setInvoking] = useState(false)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.tools()
      setTools(data)
      setSelected((current) => current ?? data.find((tool) => tool.name === 'user.search') ?? data[0] ?? null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])
  useEffect(() => {
    if (selected) {
      setArgs(example(selected))
      setOutcome(null)
    }
  }, [selected?.name])

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) return tools
    return tools.filter((tool) =>
      `${tool.name} ${tool.description} ${tool.source}`.toLowerCase().includes(needle))
  }, [tools, query])

  const invoke = async (confirmed = false) => {
    if (!selected) return
    setInvoking(true)
    setOutcome(null)
    try {
      const parsed = args.trim() ? JSON.parse(args) : {}
      const result = await api.invokeTool(selected.name, parsed,
        session === 'manual-demo' ? `manual-${Date.now()}` : session, confirmed)
      setOutcome(result)
      if (result.status === 'PENDING') toast('角色变更已提交复核，业务数据未变化', 'info')
      else if (result.status === 'CONFIRMATION_REQUIRED') toast('可操作度较低，请确认风险后再执行', 'info')
      else if (result.status === 'EXECUTED') toast(`${selected.name} 执行完成`, 'ok')
      else toast(`${decisionLabel(result.errorCode ?? result.status)}：${result.message}`, 'bad')
    } catch (err) {
      const message = errorMessage(err)
      setOutcome({
        status: 'FAILED', errorCode: 'BAD_REQUEST', message, affectedCount: 0,
        traceId: '—', sessionId: session, latencyMs: 0, steps: []
      })
      toast(message, 'bad')
    } finally {
      setInvoking(false)
    }
  }

  return (
    <div className="app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">Tool 注册表</span>
        <span className="chip chip--info">{tools.length}</span>
        <span className="chip chip--ok">{tools.filter((t) => t.active).length} active</span>
        <div className="spacer" />
        <input className="input" placeholder="按名称 / 说明搜索…" value={query}
          onChange={(e) => setQuery(e.target.value)} style={{ width: 210, height: 30 }} />
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <div style={{ flex: 1, minWidth: 0, overflow: 'auto' }}>
          {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}
          {loading && !tools.length && <div className="app-loading"><Loader2 size={15} className="spin" /> 读取注册表…</div>}
          <table className="tools-table">
            <thead>
              <tr>
                <th>Tool</th><th>副作用 / 审批</th><th>权限码</th><th>状态</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((tool) => (
                <tr key={tool.name} onClick={() => setSelected(tool)} style={{ cursor: 'pointer' }}>
                  <td>
                    <div className="tool-name">{tool.name}
                      <span className={`chip ${tool.source === 'core' ? '' : 'chip--violet'}`}
                        style={{ marginLeft: 6, fontSize: 8, verticalAlign: 1 }}>
                        {sourceLabel(tool.source)}
                      </span>
                    </div>
                    <div className="tool-desc">{tool.description}</div>
                  </td>
                  <td>
                    <span className={`chip chip--${sideEffectTone(tool.sideEffect)}`}>{sideEffectLabel(tool.sideEffect)}</span>
                    <div className="muted" style={{ fontSize: 9.5, marginTop: 2 }}>{policyLabel(tool.approvalPolicy)}</div>
                  </td>
                  <td>
                    <span className="tool-perms mono">
                      {tool.requiredPermissions.length
                        ? tool.requiredPermissions.map((permission) => (
                          <span className="tool-perm" key={permission} title={permission}>{permission}</span>
                        ))
                        : '—'}
                    </span>
                  </td>
                  <td>
                    <span className={`chip ${tool.active ? 'chip--ok' : 'chip--warn'}`}>
                      {tool.active ? '已启用' : '未启用'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div style={{ width: 330, flex: 'none', borderLeft: '1px solid var(--line-soft)', overflow: 'auto', padding: 14 }}>
          {selected ? (
            <>
              <div className="eyebrow">执行通道试运行</div>
              <h3 style={{ fontSize: 14, margin: '4px 0 2px' }}>{selected.name}</h3>
              <p className="muted" style={{ fontSize: 11, lineHeight: 1.55 }}>{selected.description}</p>
              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', margin: '9px 0' }}>
                <span className={`chip chip--${sideEffectTone(selected.sideEffect)}`}>{sideEffectLabel(selected.sideEffect)}</span>
                <span className="chip">{policyLabel(selected.approvalPolicy)}</span>
                <span className="chip">v{selected.version}</span>
                {selected.compensation && <span className="chip chip--violet">{selected.compensation}</span>}
              </div>
              {!selected.active && (
                <div className="agent-approval" style={{ marginBottom: 8 }}>
                  <ShieldAlert size={12} /> 未激活：调用将返回「工具未激活」
                </div>
              )}
              <label className="eyebrow" style={{ display: 'block', marginBottom: 4 }}>args JSON</label>
              <textarea className="code-input" value={args} onChange={(e) => setArgs(e.target.value)} spellCheck={false} />
              <label className="eyebrow" style={{ display: 'block', margin: '8px 0 4px' }}>sessionId（预算隔离）</label>
              <input className="input" value={session} onChange={(e) => setSession(e.target.value)} style={{ height: 30 }} />
              <button className="btn btn--primary" style={{ width: '100%', marginTop: 9 }} disabled={invoking}
                onClick={() => void invoke()}>
                {invoking ? <Loader2 size={13} className="spin" /> : <Play size={13} />} 通过执行通道调用
              </button>
              <div className="muted" style={{ fontSize: 10, marginTop: 6, lineHeight: 1.5 }}>
                只读直接执行；权限内写操作由本人确认风险后执行；角色变更需要另一位成员复核。所有出口都会写审计。
              </div>

              {outcome && (
                <div style={{ marginTop: 13 }}>
                  <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', marginBottom: 6 }}>
                    <span className={`chip ${outcome.status === 'EXECUTED' ? 'chip--ok'
                      : outcome.status === 'PENDING' || outcome.status === 'CONFIRMATION_REQUIRED' ? 'chip--warn'
                        : outcome.status === 'DENIED' ? 'chip--bad' : 'chip--bad'}`}>{toolStatusLabel(outcome.status)}</span>
                    {outcome.errorCode && <span className="chip chip--bad mono">{decisionLabel(outcome.errorCode)}</span>}
                    {outcome.latencyMs > 0 && <span className="chip">{outcome.latencyMs}ms</span>}
                    {outcome.approvalId && (
                      <button className="chip chip--warn" onClick={() => openApp('approvals')}>
                        <BadgeCheck size={10} /> 去审批
                      </button>
                    )}
                  </div>
                  {outcome.status === 'CONFIRMATION_REQUIRED' && outcome.confirmation && (
                    <div className="agent-confirm" style={{ marginBottom: 9 }}>
                      <div className="agent-confirm__head">
                        <ShieldAlert size={13} />
                        <b>{outcome.confirmation.intent}</b>
                      </div>
                      <div className="agent-confirm__meta">
                        可操作度 {outcome.confirmation.operability}%
                        {outcome.confirmation.riskReasons.length > 0
                          ? ' · ' + outcome.confirmation.riskReasons.join('；') : ''}
                      </div>
                      <button className="btn btn--danger" disabled={invoking}
                        onClick={() => void invoke(true)}>
                        <ShieldAlert size={12} /> 确认风险并执行
                      </button>
                    </div>
                  )}
                  <div className="gateway-steps">
                    {outcome.steps.map((step, index) => (
                      <div className="gateway-step" key={index}>
                        <span>{step.stage}</span>
                        <span style={{ color: step.status === 'bad' ? 'var(--bad)'
                          : step.status === 'warn' ? 'var(--warn)' : 'var(--ok)' }}>
                          {step.detail}
                        </span>
                      </div>
                    ))}
                  </div>
                  {outcome.data != null && (
                    <div className="gateway-result" style={{ marginTop: 7 }}>
                      {JSON.stringify(outcome.data, null, 2)}
                    </div>
                  )}
                  {outcome.message && !outcome.data && (
                    <div className="gateway-result" style={{ marginTop: 7 }}>{outcome.message}</div>
                  )}
                  <div className="hash-line" style={{ marginTop: 7 }}>
                    <Terminal size={9} /> trace {outcome.traceId}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="app-empty"><Wrench size={18} /><b>选择一个 Tool</b></div>
          )}
        </div>
      </div>
    </div>
  )
}
