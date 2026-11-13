import { useMemo, useState } from 'react'
import { ArrowRight, Check, Eye, EyeOff, Loader2, Moon, ShieldCheck, Sparkles, Sun } from './icons'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'

const DEMO = [
  { username: 'admin', password: 'admin123', name: '林深', role: '系统管理员', hue: 210 },
  { username: 'ops', password: 'ops123', name: '陈曦', role: '运营', hue: 265 },
  { username: 'auditor', password: 'audit123', name: '周衡', role: '安全审计员', hue: 15 },
  { username: 'finance', password: 'finance123', name: '王珂', role: '财务', hue: 150 },
  { username: 'guest', password: 'guest123', name: '访客', role: '只读访客', hue: 200 }
]

export function Login() {
  const login = useAuth((s) => s.login)
  const loading = useAuth((s) => s.loading)
  const error = useAuth((s) => s.error)
  const theme = useShell((s) => s.theme)
  const setTheme = useShell((s) => s.setTheme)
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [showPassword, setShowPassword] = useState(false)

  const initials = useMemo(() => username.slice(0, 1).toUpperCase(), [username])

  const submit = async (event?: React.FormEvent) => {
    event?.preventDefault()
    if (loading) return
    try {
      await login(username.trim(), password)
    } catch {
      // Error is rendered from the store.
    }
  }

  const quick = (account: typeof DEMO[number]) => {
    setUsername(account.username)
    setPassword(account.password)
    login(account.username, account.password).catch(() => undefined)
  }

  return (
    <div className="login">
      <div className="login__switch">
        <button className="icon-btn" aria-label="切换主题"
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
          {theme === 'dark' ? <Sun size={15} /> : <Moon size={15} />}
        </button>
      </div>

      <section className="login__hero">
        <span className="login__badge">
          <Sparkles size={13} /> AI 原生 · 权限先于智能
        </span>
        <img className="login__logo" src="/bapi-logo.png" alt="BaPi" />
        <h1 className="login__title">BaPi</h1>
        <p className="login__sub">
          macOS 范式的 AI 原生工作台。权限先于智能，所有执行走统一通道，
          <br className="login__br" />每次执行都留痕；高风险操作先确认，提权操作双人复核。
        </p>
        <div className="login__features">
          <span className="login__feature"><Check size={14} /> 权限前置过滤</span>
          <span className="login__feature"><Check size={14} /> 审批绑定参数哈希</span>
          <span className="login__feature"><Check size={14} /> 哈希链审计</span>
          <span className="login__feature"><Check size={14} /> 轨迹可回放</span>
        </div>
      </section>

      <form className="login__card glass glass-opaque" onSubmit={submit}>
        <h2>进入工作台</h2>
        <p>使用你的组织账号登录。Agent 的权限不会超过你本人。</p>

        <div className="login__field">
          <label htmlFor="login-user">用户名</label>
          <input id="login-user" className="input" autoComplete="username" value={username}
            onChange={(e) => setUsername(e.target.value)} placeholder="admin" />
        </div>
        <div className="login__field">
          <label htmlFor="login-pass">密码</label>
          <div style={{ position: 'relative' }}>
            <input id="login-pass" className="input" style={{ paddingRight: 38 }}
              type={showPassword ? 'text' : 'password'} autoComplete="current-password"
              value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
            <button type="button" className="icon-btn" aria-label={showPassword ? '隐藏密码' : '显示密码'}
              style={{ position: 'absolute', right: 3, top: 3 }}
              onClick={() => setShowPassword((v) => !v)}>
              {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
            </button>
          </div>
        </div>

        <button className="btn btn--primary login__submit" type="submit" disabled={loading}>
          {loading ? <Loader2 size={15} className="spin" /> : <ShieldCheck size={15} />}
          {loading ? '正在验证…' : `以 ${username.trim() || '账号'} 登录`}
          {!loading && <ArrowRight size={14} style={{ marginLeft: 'auto' }} />}
        </button>

        {error && <div className="login__error" role="alert">{error}</div>}

        <div className="login__demo">
          <div className="login__demo-label">演示账号 · 点击直接登录</div>
          <div className="login__accounts">
            {DEMO.map((account) => (
              <button type="button" key={account.username} className="login__account"
                onClick={() => quick(account)} disabled={loading}>
                <span className="avatar" style={{ width: 24, height: 24, fontSize: 10, ['--hue' as string]: account.hue }}>
                  {account.name.slice(0, 1)}
                </span>
                <span>
                  <b>{account.name}</b>
                  <small>{account.role} · {account.username}</small>
                </span>
              </button>
            ))}
          </div>
        </div>

        <div className="login__foot">
          <span>权限先行 · 审批可控 · 全程审计</span>
          <span className="mono">{initials}@bapi</span>
        </div>
      </form>
    </div>
  )
}
