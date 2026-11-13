import { useEffect, useState } from 'react'
import {
  AlertTriangle, Box, CheckCircle2, Loader2, PackageOpen, Play, Plug, Power,
  RefreshCw, ShieldAlert, ShieldCheck, Trash2, Zap
} from '../components/icons'
import { decisionLabel, pluginStatusLabel, toolStatusLabel } from '../lib/labels'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { PluginView } from '../types'

const STATUS_STEPS = ['INSTALLED', 'PENDING_AUTH', 'ACTIVE'] as const

export function Plugins() {
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const toast = useShell((s) => s.toast)
  const openApp = useShell((s) => s.openAppGuarded)
  const [plugins, setPlugins] = useState<PluginView[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [trialBusy, setTrialBusy] = useState<string | null>(null)
  const [trialResult, setTrialResult] = useState<{
    title: string
    lines: string[]
    approvalId?: string
    status: string
    raw?: unknown
  } | null>(null)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      setPlugins(await api.plugins())
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const act = async (id: string, action: 'activate' | 'disable' | 'uninstall') => {
    setBusy(id)
    try {
      if (action === 'activate') await api.activatePlugin(id)
      else if (action === 'disable') await api.disablePlugin(id)
      else await api.uninstallPlugin(id)
      toast(action === 'activate' ? '插件已激活，其 Tool 现在可执行'
        : action === 'disable' ? '插件已停用，调用将返回 TOOL_NOT_ACTIVE'
          : '插件已卸载，其 Tool 已从注册表移除', 'ok')
      setConfirming(null)
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  const trialArgs: Record<string, Record<string, unknown>> = {
    'paper.search': { query: 'retrieval', limit: 3 },
    'dataset.import': { datasetName: '演示数据集', rows: 120 }
  }

  const trial = async (plugin: PluginView) => {
    const tool = plugin.tools[0]
    if (!tool) {
      toast('这个插件没有声明 Tool', 'bad')
      return
    }
    setTrialBusy(plugin.pluginId)
    try {
      if (plugin.status !== 'ACTIVE') {
        await api.activatePlugin(plugin.pluginId)
        await load()
      }
      const outcome = await api.invokeTool(tool, trialArgs[tool] ?? {}, `plugin-trial-${Date.now()}`)
      const data = outcome.data as any
      const lines: string[] = []
      if (Array.isArray(data?.results)) {
        data.results.slice(0, 5).forEach((result: any) => {
          lines.push(`${result.title} — ${result.subtitle ?? ''}`)
        })
      } else if (outcome.summary) {
        lines.push(outcome.summary)
      } else {
        lines.push(outcome.message)
      }
      setTrialResult({
        title: `试用 ${tool}`,
        lines,
        approvalId: outcome.approvalId,
        status: outcome.status,
        raw: outcome.data
      })
      if (outcome.status === 'PENDING') {
        toast('角色变更已提交复核，业务数据未变化', 'info')
      } else if (outcome.status === 'CONFIRMATION_REQUIRED') {
        toast('可操作度较低，需要你确认风险后再执行', 'info')
        void refreshDesktop()
      } else if (outcome.status === 'EXECUTED') {
        toast('Tool 执行完成', 'ok')
      } else {
        toast(`${decisionLabel(outcome.errorCode ?? outcome.status)}：${outcome.message}`, 'bad')
      }
      await load()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setTrialBusy(null)
    }
  }

  const demo = async (kind: 'unknown' | 'incompatible') => {
    setBusy(kind)
    try {
      const result = kind === 'unknown'
        ? await api.installUnknownPermission()
        : await api.installIncompatible()
      toast(kind === 'unknown'
        ? '插件已安装（不应发生）'
        : `插件已安装，但保持${pluginStatusLabel(result.status)}：${result.reason}`, kind === 'unknown' ? 'bad' : 'info')
      await load()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">插件</span>
        <span className="chip chip--info">{plugins.length}</span>
        <span className="chip chip--ok">{plugins.filter((p) => p.status === 'ACTIVE').length} 已激活</span>
        <div className="spacer" />
        <button className="btn btn--ghost" disabled={busy === 'unknown'} onClick={() => void demo('unknown')}>
          {busy === 'unknown' ? <Loader2 size={12} className="spin" /> : <ShieldAlert size={12} />}
          安装越权插件
        </button>
        <button className="btn btn--ghost" disabled={busy === 'incompatible'} onClick={() => void demo('incompatible')}>
          {busy === 'incompatible' ? <Loader2 size={12} className="spin" /> : <AlertTriangle size={12} />}
          安装不兼容插件
        </button>
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      <div className="app-scroll">
        <div className="plugin-usage">
          <span><b>1</b> 安装声明式描述</span><i>→</i>
          <span><b>2</b> 待授权</span><i>→</i>
          <span><b>3</b> 授权启用</span><i>→</i>
          <span><b>4</b> 工具可用 / 挂载点生效</span><i>→</i>
          <span><b>5</b> 卸载注销</span>
        </div>
        {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}
        {loading && !plugins.length && <div className="app-loading"><Loader2 size={15} className="spin" /> 读取插件…</div>}
        <div className="plugin-grid">
          {plugins.map((plugin) => (
            <article className="plugin-card" key={plugin.pluginId}>
              <div className="plugin-card__head">
                <span className="plugin-icon"><Plug size={16} /></span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <b>{plugin.name}</b>
                  <small>{plugin.vendor} · v{plugin.version}</small>
                </div>
                <span className={`chip ${plugin.status === 'ACTIVE' ? 'chip--ok'
                  : plugin.status === 'PENDING_AUTH' ? 'chip--warn'
                    : plugin.status === 'DISABLED' ? 'chip--bad' : ''}`}>{pluginStatusLabel(plugin.status)}</span>
              </div>

              <div className="plugin-steps">
                {STATUS_STEPS.map((step) => (
                  <span className="plugin-step" key={step}
                    data-on={step === plugin.status || (plugin.status === 'DISABLED' && step === 'INSTALLED')}>
                    {pluginStatusLabel(step)}
                  </span>
                ))}
              </div>

              <div className="muted" style={{ fontSize: 11, lineHeight: 1.5 }}>{plugin.description}</div>
              <div className="plugin-card__reason">{plugin.reason}</div>

              <div className="plugin-card__tools">
                {plugin.tools.map((tool) => <span className="chip chip--violet mono" key={tool}>{tool}</span>)}
                {plugin.tools.length === 0 && <span className="chip">无 Tool</span>}
              </div>
              <div className="plugin-card__tools">
                {plugin.mounts.map((mount) => (
                  <span className="chip" key={mount.mountPoint} title={mount.dataContract}>
                    <Box size={9} /> {mount.mountPoint}
                  </span>
                ))}
                {plugin.mounts.length > 0 && <span className="chip chip--info">声明式挂载点</span>}
              </div>

              <div className="plugin-card__actions">
                {plugin.tools.length > 0 && (
                  <button className="btn btn--primary" disabled={trialBusy === plugin.pluginId}
                    onClick={() => void trial(plugin)}>
                    {trialBusy === plugin.pluginId ? <Loader2 size={12} className="spin" /> : <Play size={12} />}
                    {plugin.status === 'PENDING_AUTH' ? '授权并试用' : '试用 Tool'}
                  </button>
                )}
                {plugin.status === 'PENDING_AUTH' || plugin.status === 'DISABLED' ? (
                  <button className="btn btn--primary" disabled={busy === plugin.pluginId}
                    onClick={() => void act(plugin.pluginId, 'activate')}>
                    {busy === plugin.pluginId ? <Loader2 size={12} className="spin" /> : <Power size={12} />}
                    {plugin.status === 'PENDING_AUTH' ? '授权启用' : '启用'}
                  </button>
                ) : (
                  <button className="btn" disabled={busy === plugin.pluginId}
                    onClick={() => void act(plugin.pluginId, 'disable')}>
                    <Power size={12} /> 停用
                  </button>
                )}
                {confirming === plugin.pluginId ? (
                  <>
                    <button className="btn btn--danger" disabled={busy === plugin.pluginId}
                      onClick={() => void act(plugin.pluginId, 'uninstall')}>
                      <Trash2 size={12} /> 确认卸载
                    </button>
                    <button className="btn btn--ghost" onClick={() => setConfirming(null)}>取消</button>
                  </>
                ) : (
                  <button className="btn btn--ghost" disabled={busy === plugin.pluginId}
                    onClick={() => setConfirming(plugin.pluginId)}>
                    <Trash2 size={12} /> 卸载
                  </button>
                )}
              </div>
            </article>
          ))}
          {!loading && plugins.length === 0 && (
            <div className="app-empty">
              <span className="app-empty__icon"><PackageOpen size={20} /></span>
              <b>没有已安装插件</b>
              <small>插件只能声明权限体系中已存在的权限码，安装后默认为待授权。</small>
            </div>
          )}
        </div>

        <div className="widget glass" style={{ marginTop: 14, padding: '13px 15px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
            <Zap size={15} style={{ color: 'var(--violet)' }} />
            <div>
              <b style={{ fontSize: 12 }}>扩展边界</b>
              <div className="muted" style={{ fontSize: 10.5, lineHeight: 1.5 }}>
                插件不提供任意 JS，只提交声明式描述；卸载时其 Tool 与挂载点一并注销。越权插件会被拒绝，
                版本不兼容插件保持已停用。
              </div>
            </div>
          </div>
        </div>
      </div>

      {trialResult && (
        <div className="modal-backdrop" onClick={() => setTrialResult(null)}>
          <div className="modal glass glass-opaque" style={{ width: 'min(600px, 94vw)' }}
            onClick={(e) => e.stopPropagation()}>
            <h3>{trialResult.title}</h3>
            <p>
              结果状态：<span className={`chip ${trialResult.status === 'EXECUTED' ? 'chip--ok'
                : trialResult.status === 'PENDING' || trialResult.status === 'CONFIRMATION_REQUIRED'
                  ? 'chip--warn' : 'chip--bad'}`}>{toolStatusLabel(trialResult.status)}</span>
              {trialResult.status === 'PENDING' && ' · 已进入审批队列，批准后才会执行'}
              {trialResult.status === 'CONFIRMATION_REQUIRED' && ' · 需要本人确认风险后执行'}
            </p>
            <div className="gateway-result" style={{ marginTop: 10 }}>
              {trialResult.lines.map((line, index) => <div key={index}>· {line}</div>)}
            </div>
            {trialResult.raw != null && (
              <details style={{ marginTop: 9 }}>
                <summary className="muted" style={{ fontSize: 11, cursor: 'pointer' }}>查看原始返回</summary>
                <pre className="gateway-result" style={{ marginTop: 6 }}>{JSON.stringify(trialResult.raw, null, 2)}</pre>
              </details>
            )}
            <div className="modal__actions">
              {trialResult.approvalId ? (
                <button className="btn btn--primary" onClick={() => { setTrialResult(null); openApp('approvals') }}>
                  <ShieldCheck size={13} /> 去审批队列
                </button>
              ) : (
                <span className="chip chip--ok"><CheckCircle2 size={10} /> Tool 已执行并写审计</span>
              )}
              <button className="btn btn--ghost" onClick={() => setTrialResult(null)}>关闭</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
