import { useEffect, useRef, useState } from 'react'
import {
  ArrowUp, BadgeCheck, CheckCircle2, Copy, ImagePlus, Loader2, MessageSquarePlus, Plus,
  Send, ShieldCheck, Sparkles, Terminal, TriangleAlert, X
} from '../components/icons'
import { api, errorMessage } from '../lib/api'
import { scopeLabel } from '../lib/format'
import { agentStatusLabel, decisionLabel, riskLevelLabel } from '../lib/labels'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { AgentMessage, AgentReply, AgentSession } from '../types'

interface Turn {
  user: string
  assistant: string
  reply?: AgentReply
}

export function Agent() {
  const principal = useAuth((s) => s.desktop?.principal)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const toast = useShell((s) => s.toast)
  const openApp = useShell((s) => s.openAppGuarded)
  const setCustomWallpaper = useShell((s) => s.setCustomWallpaper)
  const [sessions, setSessions] = useState<AgentSession[]>([])
  const [activeSession, setActiveSession] = useState<string | null>(null)
  const [turns, setTurns] = useState<Turn[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingSession, setLoadingSession] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [wallpaperFile, setWallpaperFile] = useState<File | null>(null)
  const [wallpaperPreview, setWallpaperPreview] = useState<string | null>(null)
  const wallpaperInput = useRef<HTMLInputElement>(null)
  const chatRef = useRef<HTMLDivElement>(null)
  const touched = useRef(false)
  const sessionSeq = useRef(0)
  useEffect(() => {
    if (!wallpaperFile) { setWallpaperPreview(null); return }
    const url = URL.createObjectURL(wallpaperFile)
    setWallpaperPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [wallpaperFile])

  const latest = [...turns].reverse().find((t) => t.reply)?.reply
  const budget = latest?.budget

  const loadSessions = async (selectFirst = true) => {
    try {
      const data = await api.agentSessions()
      setSessions(data)
      if (selectFirst && !touched.current && !activeSession && data.length > 0) {
        await selectSession(data[0].id)
      }
    } catch {
      // Sessions are a convenience; a new chat still works.
    }
  }

  useEffect(() => { void loadSessions() }, [])

  useEffect(() => {
    const el = chatRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [turns, sending])

  const selectSession = async (id: string) => {
    const seq = ++sessionSeq.current
    setLoadingSession(true)
    setActiveSession(id)
    setError(null)
    try {
      const messages = await api.agentMessages(id)
      const next: Turn[] = []
      let currentUser: string | null = null
      let assistantParts: string[] = []
      const flush = () => {
        if (currentUser !== null) {
          next.push({ user: currentUser, assistant: assistantParts.filter(Boolean).join('\n\n') })
        }
        currentUser = null
        assistantParts = []
      }
      for (const message of messages) {
        if (message.role === 'user') {
          flush()
          currentUser = message.content
        } else if (message.role === 'assistant' && message.content.trim()) {
          assistantParts.push(message.content)
        }
      }
      flush()
      if (seq === sessionSeq.current) setTurns(next)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoadingSession(false)
    }
  }

  const newChat = () => {
    touched.current = true
    sessionSeq.current++
    setWallpaperFile(null)
    setActiveSession(null)
    setTurns([])
    setError(null)
  }

  const send = async (text?: string, confirm = false) => {
    const message = (text ?? input).trim()
    if (!message || sending) return
    if (wallpaperFile) {
      if (!/壁纸|wallpaper/i.test(message)) {
        toast('图片已附加；发送「更换壁纸」可直接设为桌面壁纸（无需审批）', 'info')
        return
      }
      try {
        await setCustomWallpaper(wallpaperFile)
        setTurns((prev) => [...prev, {
          user: `[图片] ${message}`,
          assistant: '已把这张图片压缩后保存，并设为桌面壁纸。壁纸跟随账号同步，无需审批，'
            + '只更新你的个人偏好。'
        }])
        setWallpaperFile(null)
        setInput('')
        toast('壁纸已更换并同步到账号', 'ok')
      } catch {
        // The shell store already surfaced the upload error.
      }
      return
    }
    touched.current = true
    sessionSeq.current++
    setInput('')
    setSending(true)
    setError(null)
    setTurns((prev) => [...prev, { user: message, assistant: '' }])
    try {
      const reply = await api.agentChat(message, activeSession ?? undefined, confirm)
      setActiveSession(reply.sessionId)
      setTurns((prev) => prev.map((turn, index) => index === prev.length - 1
        ? { ...turn, assistant: reply.message, reply } : turn))
      void loadSessions(false)
      void refreshDesktop()
      if (reply.status === 'PENDING') {
        toast('角色变更已提交复核，业务数据尚未变化', 'info')
      } else if (reply.status === 'CONFIRMATION_REQUIRED') {
        toast('这项操作需要你确认风险后才会执行', 'info')
      } else if (reply.status === 'DENIED') {
        toast(reply.errorCode ? `请求被拒绝：${decisionLabel(reply.errorCode)}` : '请求被拒绝', 'bad')
      }
    } catch (err) {
      const messageText = errorMessage(err)
      setError(messageText)
      setTurns((prev) => prev.map((turn, index) => index === prev.length - 1
        ? { ...turn, assistant: `执行失败：${messageText}` } : turn))
    } finally {
      setSending(false)
    }
  }

  const confirmExecution = async (message: string) => {
    if (sending) return
    setSending(true)
    setError(null)
    try {
      const reply = await api.agentChat(message, activeSession ?? undefined, true)
      setActiveSession(reply.sessionId)
      setTurns((prev) => prev.map((turn, index) => index === prev.length - 1
        ? { ...turn, assistant: reply.message, reply } : turn))
      void loadSessions(false)
      void refreshDesktop()
      if (reply.status === 'DENIED') {
        toast(reply.errorCode ? `请求被拒绝：${decisionLabel(reply.errorCode)}` : '请求被拒绝', 'bad')
      } else if (reply.status === 'OK') {
        toast('已按你的确认执行', 'ok')
      }
    } catch (err) {
      const messageText = errorMessage(err)
      setError(messageText)
    } finally {
      setSending(false)
    }
  }

  const onKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void send()
    }
  }

  const budgetPct = budget ? Math.min(100, (budget.toolCalls / budget.maxToolCalls) * 100) : 0

  return (
    <div className="agent">
      <aside className="agent__side">
        <button className="btn btn--primary" style={{ width: '100%' }} onClick={newChat}>
          <Plus size={14} /> 新对话
        </button>
        <div className="app-side__label" style={{ marginTop: 10 }}>会话</div>
        <div className="agent__sessions">
          {sessions.length === 0 && (
            <div className="muted" style={{ fontSize: 10.5, padding: '6px 9px' }}>还没有会话。发送第一条指令开始。</div>
          )}
          {sessions.map((session) => (
            <button key={session.id} className="agent__session"
              aria-current={activeSession === session.id}
              onClick={() => void selectSession(session.id)}>
              {session.title}
            </button>
          ))}
        </div>
        <div className="divider" style={{ margin: '8px 4px' }} />
        <div className="muted" style={{ fontSize: 10, lineHeight: 1.5, padding: '2px 9px' }}>
          <ShieldCheck size={10} style={{ verticalAlign: -1 }} /> 会话轨迹可回放
        </div>
      </aside>

      <div className="agent__main">
        <header className="agent__header">
          <span className="bubble-avatar bubble-avatar--agent" style={{ width: 30, height: 30 }}>
            <Sparkles size={14} />
          </span>
          <div>
            <div className="agent__header-title">Aurora Agent</div>
            <small>{principal?.displayName} · {scopeLabel(principal?.dataScope?.type)} · 权限上限 = 本人</small>
          </div>
          <div className="agent__budget" title="会话预算">
            <div className="agent__budget-bar">
              <div className="agent__budget-fill" style={{ width: `${budgetPct}%` }} />
            </div>
            <small>
              <span>Tool {budget?.toolCalls ?? 0}/{budget?.maxToolCalls ?? 20}</span>
              <span>{budget?.consecutiveFailures ?? 0} 失败</span>
            </small>
          </div>
        </header>

        <div className="chat" ref={chatRef}>
          {loadingSession && <div className="app-loading"><Loader2 size={15} className="spin" /> 载入会话轨迹…</div>}
          {!loadingSession && turns.length === 0 && (
            <div className="bubble-row">
              <span className="bubble-avatar bubble-avatar--agent">A</span>
              <div className="bubble bubble--agent">
                我以 <b>{principal?.displayName}（{principal?.title}）</b> 的身份工作，权限不会超过你。
                所有操作都经过统一执行通道：先校验权限，权限内的写操作由你确认风险后直接执行。
                <div className="bubble__meta">只有角色变更等提权操作需要另一位成员复核。</div>
              </div>
            </div>
          )}
          {turns.map((turn, index) => (
            <div key={index} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div className="bubble-row bubble-row--user">
                <span className="avatar" style={{ width: 26, height: 26, fontSize: 10, ['--hue' as string]: principal?.avatarHue ?? 210 }}>
                  {principal?.displayName?.slice(0, 1)}
                </span>
                <div className="bubble bubble--user">{turn.user}</div>
              </div>
              {turn.reply && turn.reply.steps.length > 0 && (
                <details className="agent-steps-fold">
                  <summary>Tool 工具链 · {turn.reply.steps.length} 步</summary>
                  <div className="agent-steps">
                    {turn.reply.steps.map((step, stepIndex) => (
                      <div className="agent-step" key={stepIndex}>
                        <i className={`agent-step__dot ${step.status === 'bad' ? 'agent-step__dot--bad'
                          : step.status === 'warn' ? 'agent-step__dot--warn' : ''}`} />
                        <div>
                          <b style={{ fontWeight: 600 }}>{step.label}</b>
                          <small>{step.detail}</small>
                        </div>
                      </div>
                    ))}
                  </div>
                </details>
              )}
              {turn.assistant && (
                <div className="bubble-row">
                  <span className="bubble-avatar bubble-avatar--agent">A</span>
                  <div style={{ minWidth: 0, maxWidth: '100%' }}>
                    <div className="bubble bubble--agent">
                      {turn.assistant}
                      {turn.reply && (
                        <div className="agent-result">
                          <span className={`chip ${turn.reply.status === 'OK' ? 'chip--ok'
                            : turn.reply.status === 'PENDING' ? 'chip--warn'
                              : turn.reply.status === 'DENIED' ? 'chip--bad' : 'chip--warn'}`}>
                            {agentStatusLabel(turn.reply.status)}
                          </span>
                          {turn.reply.errorCode && <span className="chip chip--bad mono">{decisionLabel(turn.reply.errorCode)}</span>}
                          <button className="icon-btn" style={{ width: 20, height: 20 }}
                            aria-label="复制轨迹 ID"
                            onClick={() => {
                              navigator.clipboard?.writeText(turn.reply!.traceId)
                              toast('traceId 已复制', 'ok')
                            }}>
                            <Copy size={10} />
                          </button>
                          <span className="mono" style={{ fontSize: 9.5, color: 'var(--faint)' }}>{turn.reply.traceId.slice(0, 18)}…</span>
                        </div>
                      )}
                      {turn.reply?.status === 'PENDING' && turn.reply.approvalId && (
                        <button className="agent-approval" style={{ width: '100%', textAlign: 'left' }}
                          onClick={() => openApp('approvals')}>
                          <BadgeCheck size={14} />
                          审批 {turn.reply.approvalId.slice(0, 8)} 等待人工决定
                          <span style={{ marginLeft: 'auto', fontSize: 10.5 }}>前往审批 →</span>
                        </button>
                      )}
                      {turn.reply?.status === 'CONFIRMATION_REQUIRED' && turn.reply.confirmation && (
                        <div className="agent-confirm">
                          <div className="agent-confirm__head">
                            <TriangleAlert size={13} />
                            <b>{turn.reply.confirmation.intent}</b>
                          </div>
                          <div className="agent-confirm__meta">
                            {riskLevelLabel(turn.reply.confirmation.riskLevel,
                              turn.reply.confirmation.operability)}
                            {' '}· 可操作度 {turn.reply.confirmation.operability}%
                          </div>
                          {turn.reply.confirmation.riskReasons.length > 0 && (
                            <ul>
                              {turn.reply.confirmation.riskReasons.map((reason) => (
                                <li key={reason}>{reason}</li>
                              ))}
                            </ul>
                          )}
                          <button className="btn btn--danger" disabled={sending}
                            onClick={() => void confirmExecution(turn.user)}>
                            <ShieldCheck size={12} /> 确认执行
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
          {sending && (
            <div className="bubble-row">
              <span className="bubble-avatar bubble-avatar--agent">A</span>
              <div className="bubble bubble--agent" style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                <Loader2 size={13} className="spin" /> 正在经过能力校验、权限与预算检查…
              </div>
            </div>
          )}
          {error && (
            <div className="agent-approval" style={{ background: 'var(--bad-soft)', color: 'var(--bad)' }}>
              <TriangleAlert size={13} /> {error}
            </div>
          )}
        </div>

        <div className="composer">
          <div className="suggestions">
            {(latest?.suggestions ?? ['查询本月新增用户', '把李四加入运营组', '删除用户 张三',
              '检索数据范围在 Tool 层如何传递', '直接调用 /internal/db/reset']).slice(0, 4).map((suggestion) => (
              <button className="suggestion" key={suggestion} onClick={() => void send(suggestion)}>{suggestion}</button>
            ))}
          </div>
          {wallpaperPreview && (
            <div className="agent-attachment">
              <img src={wallpaperPreview} alt="待设为壁纸的图片" />
              <span>
                <b>{wallpaperFile?.name}</b>
                <small>发送「更换壁纸」会保存并随账号同步，无需审批</small>
              </span>
              <button className="icon-btn" aria-label="移除图片" onClick={() => setWallpaperFile(null)}>
                <X size={14} />
              </button>
            </div>
          )}
          <div className="composer__box">
            <button className="icon-btn" aria-label="上传壁纸图片" title="上传图片并发送「更换壁纸」"
              onClick={() => wallpaperInput.current?.click()}>
              <ImagePlus size={16} />
            </button>
            <textarea
              className="composer__input"
              rows={1}
              placeholder="给 Agent 一个任务…（Enter 发送，Shift+Enter 换行）"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              aria-label="Agent 输入框"
            />
            <button className="icon-btn" aria-label="发送" onClick={() => void send()} disabled={sending || !input.trim()}
              style={{ background: input.trim() ? 'var(--accent)' : 'transparent', color: input.trim() ? '#fff' : 'var(--muted)' }}>
              {sending ? <Loader2 size={15} className="spin" /> : <ArrowUp size={16} />}
            </button>
          </div>
          <input ref={wallpaperInput} type="file" accept="image/*" hidden
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''
              if (file) setWallpaperFile(file)
            }} />
          <div className="composer__hint">
            <span><ShieldCheck size={10} /> 未注册 / 未激活的工具默认拒绝</span>
            <span>·</span>
            <span><Terminal size={10} /> 参数指纹绑定审批</span>
            <span>·</span>
            <span><CheckCircle2 size={10} /> 全链路写审计</span>
            <span><ImagePlus size={10} /> 图片 + 「更换壁纸」= 换壁纸并同步</span>
            <span style={{ marginLeft: 'auto' }}><Send size={10} /> Enter 发送</span>
          </div>
        </div>
      </div>
    </div>
  )
}
