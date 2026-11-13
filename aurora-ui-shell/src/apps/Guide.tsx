import { useEffect, useState } from 'react'
import {
  RefreshCw, BadgeCheck, BookOpen, Database, FolderOpen, Plug,
  Puzzle, ShieldCheck, Sparkles, Users, Wrench
} from '../components/icons'
import { api, errorMessage } from '../lib/api'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'

interface Live {
  users: number
  documents: number
  chunks: number
  tools: number
  pending: number
  events: number
  plugins: number
  activePlugins: number
  consistent: boolean
}

export function Guide() {
  const desktop = useAuth((s) => s.desktop)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const toast = useShell((s) => s.toast)
  const openApp = useShell((s) => s.openAppGuarded)
  const setPaletteOpen = useShell((s) => s.setPaletteOpen)
  const [live, setLive] = useState<Live | null>(null)
  const [chainValid, setChainValid] = useState<boolean | null>(null)
  const [loading, setLoading] = useState(true)
  const [reindexing, setReindexing] = useState(false)
  const permissions = desktop?.principal.permissions ?? []

  const load = async () => {
    setLoading(true)
    try {
      const overview = await api.overview() as Record<string, any>
      const [plugins, audit] = await Promise.all([
        api.plugins().catch(() => []),
        api.verifyAudit().catch(() => null)
      ])
      let users = 0
      if (can(permissions, 'user:read')) {
        users = (await api.users('', 0, 1).catch(() => null))?.total ?? 0
      }
      setLive({
        users,
        documents: Number(overview.documents ?? 0),
        chunks: Number(overview.vectorChunks ?? 0),
        tools: Number(overview.tools ?? 0),
        pending: Number(overview.pendingApprovals ?? 0),
        events: Number(overview.auditEvents ?? 0),
        plugins: plugins.length,
        activePlugins: plugins.filter((p) => p.status === 'ACTIVE').length,
        consistent: !(overview.consistency?.alert ?? false)
      })
      setChainValid(audit?.valid ?? null)
    } catch (error) {
      toast(errorMessage(error), 'bad')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const reindex = async () => {
    setReindexing(true)
    try {
      const result = await api.reindex()
      toast(`已重建 ${result.documents} 个文档的检索索引`, 'ok')
      await load()
    } catch (error) {
      toast(errorMessage(error), 'bad')
    } finally {
      setReindexing(false)
    }
  }

  const stats = live ?? {
    users: 0, documents: desktop?.stats.documents ?? 0, chunks: desktop?.stats.knowledgeChunks ?? 0,
    tools: desktop?.stats.tools ?? 0, pending: desktop?.stats.pendingApprovals ?? 0,
    events: desktop?.stats.auditEvents ?? 0, plugins: desktop?.stats.plugins ?? 0,
    activePlugins: desktop?.stats.activePlugins ?? 0,
    consistent: !(desktop?.consistency.alert ?? false)
  }

  const steps = [
    {
      no: '01', icon: Users, color: '#5aa7ff', title: '身份与权限',
      value: `${stats.users || '—'} 位成员`,
      text: '用户、角色、资源码与数据范围来自 业务数据。人能看到哪些入口、AI 能执行哪些动作，由权限体系决定。',
      action: () => openApp('directory'), actionLabel: '打开用户管理',
      disabled: !can(permissions, 'user:read')
    },
    {
      no: '02', icon: FolderOpen, color: '#38bdf8', title: '数据源',
      value: `${stats.documents} 个对象`,
      text: '文件内容进 文件服务，元数据进 业务数据；也可以从访达上传新文件，或修改文档访问范围。',
      action: () => openApp('finder'), actionLabel: '打开访达 / 上传'
    },
    {
      no: '03', icon: Database, color: '#34d399', title: '检索索引',
      value: `${stats.chunks} 个切片`,
      text: '索引器把标题、摘要与正文编码进 检索索引，并写入 visibility / roles / depts / owner 等访问范围标记。',
      action: () => void reindex(), actionLabel: reindexing ? '重建中…' : '重建索引',
      disabled: !can(permissions, 'knowledge:manage')
    },
    {
      no: '04', icon: BookOpen, color: '#2dd4bf', title: '统一检索',
      value: '操作 / 实体 / 知识',
      text: '全局搜索同时返回操作、实体与知识；访问范围过滤在检索内部完成，不是先召回再隐藏。',
      action: () => setPaletteOpen(true), actionLabel: '打开命令面板'
    },
    {
      no: '05', icon: Sparkles, color: '#a78bfa', title: 'AI 执行',
      value: `${stats.tools} 个 Tool`,
      text: 'Agent 只能提议 Tool；真正执行的是统一的执行通道，并且以你的权限为上限。',
      action: () => openApp('agent'), actionLabel: '与 Agent 对话',
      disabled: !can(permissions, 'agent:use')
    },
    {
      no: '06', icon: BadgeCheck, color: '#fbbf24', title: '审批',
      value: `${stats.pending} 条待处理`,
      text: '权限内的写入由本人确认风险后直接执行；只有角色变更等提权操作才交给另一位成员复核。',
      action: () => openApp('approvals'), actionLabel: '打开审批队列',
      disabled: !can(permissions, 'approval:read')
    },
    {
      no: '07', icon: ShieldCheck, color: '#f97316', title: '审计',
      value: `${stats.events} 条事件`,
      text: '每个出口都写审计，事件以 SHA-256 哈希链串起；审计不可用时写类 Tool 直接中止。',
      action: () => openApp('audit'), actionLabel: '打开审计回放',
      disabled: !can(permissions, 'audit:read')
    },
    {
      no: '08', icon: Puzzle, color: '#f472b6', title: '插件',
      value: `${stats.activePlugins}/${stats.plugins} 已激活`,
      text: '插件只提交声明式 Tool 与挂载点，安装后默认 待授权；激活后才进入 Tool Registry。',
      action: () => openApp('plugins'), actionLabel: '打开插件',
      disabled: !can(permissions, 'plugin:manage')
    }
  ]

  return (
    <div className="app guide-app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">数据流 · 闭环导览</span>
        <span className={`chip ${chainValid === false ? 'chip--bad' : 'chip--ok'}`}>
          <ShieldCheck size={10} /> {chainValid === false ? '哈希链异常' : chainValid ? '哈希链完整' : '校验中'}
        </span>
        <span className={`chip ${stats.consistent ? 'chip--ok' : 'chip--bad'}`}>
          <Database size={10} /> {stats.consistent ? '索引一致' : '索引偏差'}
        </span>
        <div className="spacer" />
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      <div className="app-scroll">
        <section className="guide-hero glass glass--enhanced">
          <div>
            <span className="eyebrow">从一条记录到一次可审计的操作</span>
            <h2>数据不是“凭空出现”的</h2>
            <p>
              身份来自权限体系；文件来自上传或种子；索引来自 检索索引；检索、Agent、审批与审计都围绕同一份
              Principal 与 Tool Registry 运转。下面每条链路都可以直接进入对应入口。
            </p>
          </div>
          <div className="guide-flow">
            {['身份', '数据', '索引', '检索', 'Agent', '执行', '复核', '审计'].map((label, i) => (
              <span key={label} className="guide-flow__node">
                {label}{i < 7 && <i>→</i>}
              </span>
            ))}
          </div>
          <div className="muted" style={{ fontSize: 10, marginTop: 7 }}>
            向下滚动：数据来源与一键体验入口
          </div>
        </section>

        <div className="guide-grid">
          {steps.map((step) => {
            const Icon = step.icon
            return (
              <article className="guide-step glass" key={step.no}>
                <span className="guide-step__no">{step.no}</span>
                <span className="guide-step__icon" style={{ color: step.color }}>
                  <Icon size={18} />
                </span>
                <div className="guide-step__body">
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 7, flexWrap: 'wrap' }}>
                    <b>{step.title}</b>
                    <span className="chip" style={{ fontSize: 9.5 }}>{step.value}</span>
                  </div>
                  <p>{step.text}</p>
                  <button className="btn btn--ghost" style={{ height: 25, padding: '0 9px', fontSize: 10.5 }}
                    onClick={step.action} disabled={step.disabled || (reindexing && step.no === '03')}>
                    {step.actionLabel}
                  </button>
                </div>
              </article>
            )
          })}
        </div>

        <section className="guide-sources glass">
          <div className="widget__head">
            <span className="widget__title"><Wrench size={12} style={{ verticalAlign: -1 }} /> 数据从哪来</span>
            <span className="chip">三类来源</span>
          </div>
          <div className="guide-sources__grid">
            <div>
              <b>① 初始化种子</b>
              <p>内置种子数据 写入部门、用户、角色、14 个文档；DemoRuntimeSeeder 通过真实执行通道生成演示读操作与审计轨迹。</p>
            </div>
            <div>
              <b>② 人工录入</b>
              <p>用户管理 → <span className="mono">user.create/update</span>；访达上传 → 文件服务 + 业务数据 + 检索索引；文档访问范围可在访达中修改。</p>
            </div>
            <div>
              <b>③ 运行时产生</b>
              <p>工具调用 → 风险确认 + 审计；角色变更 → 另一人复核；Agent 会话 → 轨迹可回放。</p>
            </div>
          </div>
        </section>

        <section className="guide-actions">
          <button className="btn btn--primary" onClick={() => openApp('directory')}>
            <Users size={13} /> 新建一个用户
          </button>
          <button className="btn" onClick={() => openApp('finder')}>
            <FolderOpen size={13} /> 上传一份文件
          </button>
          <button className="btn" onClick={() => openApp('agent')}>
            <Sparkles size={13} /> 让 Agent 发起写操作
          </button>
          <button className="btn" onClick={() => openApp('plugins')}>
            <Plug size={13} /> 试用插件
          </button>
          <button className="btn" onClick={() => openApp('access')}>
            <ShieldCheck size={13} /> 申请权限
          </button>
          <span className="muted" style={{ fontSize: 10.5, marginLeft: 'auto' }}>
            权限内操作本人确认即可；角色变更才需要复核（auditor / audit123）
          </span>
        </section>
      </div>
    </div>
  )
}
