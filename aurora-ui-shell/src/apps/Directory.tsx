import { useEffect, useState } from 'react'
import {
  CheckCircle2, Loader2, Pencil, Plus, RefreshCw, Search, ShieldCheck, Trash2, UserRound, Users
} from '../components/icons'
import { decisionLabel, riskLevelLabel, userStatusLabel } from '../lib/labels'
import { scopeLabel } from '../lib/format'
import { api, errorMessage } from '../lib/api'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { GatewayOutcome, UserMeta, UserView } from '../types'

type Modal = 'create' | 'edit' | 'role' | 'delete' | null

interface FormState {
  username: string
  displayName: string
  title: string
  deptId: number
  password: string
  roleCode: string
  status: 'ACTIVE' | 'DISABLED'
}

const EMPTY: FormState = {
  username: '', displayName: '', title: '成员', deptId: 0, password: '', roleCode: '', status: 'ACTIVE'
}

export function Directory() {
  const principal = useAuth((s) => s.desktop?.principal)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const refreshSession = useAuth((s) => s.refreshSession)
  const toast = useShell((s) => s.toast)
  const openApp = useShell((s) => s.openAppGuarded)
  const permissions = principal?.permissions ?? []
  const canWrite = can(permissions, 'user:update')
  const [users, setUsers] = useState<UserView[]>([])
  const [meta, setMeta] = useState<UserMeta | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [modal, setModal] = useState<Modal>(null)
  const [form, setForm] = useState<FormState>(EMPTY)
  const [submitting, setSubmitting] = useState(false)
  const [savingSelf, setSavingSelf] = useState(false)
  const [notice, setNotice] = useState<{ tool: string; approvalId: string; message: string } | null>(null)
  const [confirmPrompt, setConfirmPrompt] = useState<{
    tool: string
    args: Record<string, unknown>
    outcome: GatewayOutcome
  } | null>(null)
  const [riskChecked, setRiskChecked] = useState(false)

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true)
    try {
      const [pageData, userMeta] = await Promise.all([
        api.users(query, page, size), api.userMeta()
      ])
      if (pageData.items.length === 0 && page > 0 && pageData.totalPages > 0) {
        setPage(pageData.totalPages - 1)
        return
      }
      const list = pageData.items
      setUsers(list)
      setTotal(pageData.total)
      setTotalPages(pageData.totalPages)
      setMeta(userMeta)
      setSelectedId((current) => current != null && list.some((u) => u.id === current)
        ? current : list.find((u) => u.id === principal?.id)?.id ?? list[0]?.id ?? null)
      setError(null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const timer = setTimeout(() => { void load() }, query.trim() ? 220 : 0)
    return () => clearTimeout(timer)
  }, [query, page, size])
  useEffect(() => {
    const timer = setInterval(() => { void load(true) }, 15_000)
    return () => clearInterval(timer)
  }, [query, page, size])

  const selected = users.find((u) => u.id === selectedId) ?? null
  const isSelf = selected?.id === principal?.id

  const grantableRoles = meta?.roles.filter((r) => r.grantable) ?? []
  const departments = meta?.departments ?? []

  const resetForm = (user?: UserView) => {
    if (user) {
      setForm({
        username: user.username,
        displayName: user.displayName,
        title: user.title,
        deptId: user.deptId,
        password: '',
        roleCode: '',
        status: user.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE'
      })
    } else {
      setForm({
        ...EMPTY,
        deptId: departments[0]?.id ?? 0,
        roleCode: grantableRoles[0]?.code ?? ''
      })
    }
  }

  const invoke = async (tool: string, args: Record<string, unknown>, confirm = false) => {
    setSubmitting(true)
    try {
      const outcome: GatewayOutcome = await api.invokeTool(tool, args,
        `directory-${Date.now()}`, confirm)
      if (outcome.status === 'CONFIRMATION_REQUIRED') {
        setRiskChecked(false)
        setConfirmPrompt({ tool, args, outcome })
        setModal(null)
      } else if (outcome.status === 'PENDING' && outcome.approvalId) {
        setNotice({
          tool,
          approvalId: outcome.approvalId,
          message: '涉及角色变更的操作需要另一位成员复核，业务数据尚未变化。'
        })
        setModal(null)
        toast(`已提交审批 ${outcome.approvalId.slice(0, 8)}`, 'info')
        void refreshDesktop()
      } else if (outcome.status === 'EXECUTED') {
        toast(outcome.summary ?? '操作完成', 'ok')
        setModal(null)
        await load(true)
        void refreshDesktop()
      } else {
        toast(`${decisionLabel(outcome.errorCode ?? outcome.status)}：${outcome.message}`, 'bad')
      }
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setSubmitting(false)
    }
  }

  const confirmRisk = async () => {
    if (!confirmPrompt || !riskChecked) return
    const prompt = confirmPrompt
    setConfirmPrompt(null)
    setRiskChecked(false)
    await invoke(prompt.tool, prompt.args, true)
  }

  const submit = async () => {
    if (!form.displayName.trim()) {
      toast('显示名称不能为空', 'bad')
      return
    }
    if (modal === 'create') {
      if (!/^[a-z0-9_.-]{3,32}$/.test(form.username)) {
        toast('username 需为 3-32 位小写字母、数字、点、下划线或短横线', 'bad')
        return
      }
      if (form.password.length < 6) {
        toast('初始密码至少 6 位', 'bad')
        return
      }
      const args: Record<string, unknown> = {
        username: form.username,
        displayName: form.displayName.trim(),
        title: form.title.trim() || '成员',
        deptId: form.deptId,
        password: form.password
      }
      if (form.roleCode) args.roleCode = form.roleCode
      await invoke('user.create', args)
    } else if (modal === 'edit' && selected && isSelf) {
      setSavingSelf(true)
      try {
        await api.updateMyProfile(form.displayName.trim(), form.title.trim())
        toast('个人资料已更新（无需审批）', 'ok')
        setModal(null)
        await load(true)
        await refreshSession()
      } catch (err) {
        toast(errorMessage(err), 'bad')
      } finally {
        setSavingSelf(false)
      }
    } else if (modal === 'edit' && selected) {
      await invoke('user.update', {
        username: selected.username,
        displayName: form.displayName.trim(),
        title: form.title.trim(),
        deptId: form.deptId,
        status: form.status
      })
    } else if (modal === 'role' && selected) {
      if (!form.roleCode) {
        toast('请选择要授予的角色', 'bad')
        return
      }
      await invoke('user.assignRole', { username: selected.username, roleCode: form.roleCode })
    } else if (modal === 'delete' && selected) {
      await invoke('user.delete', { username: selected.username })
    }
  }

  return (
    <div className="app directory-app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">用户管理</span>
        <span className="chip chip--info">{total} 人</span>
        {!canWrite && <span className="chip chip--warn">只读：缺少 user:update</span>}
        <div className="spacer" />
        <div style={{ position: 'relative', width: 210 }}>
          <Search size={13} style={{ position: 'absolute', left: 9, top: 9, color: 'var(--muted)' }} />
          <input className="input" style={{ paddingLeft: 28, height: 30 }} placeholder="搜索姓名 / username / 职位…"
            value={query} onChange={(e) => { setQuery(e.target.value); setPage(0) }} />
        </div>
        {canWrite && (
          <button className="btn btn--primary" disabled={!meta || departments.length === 0}
            onClick={() => { resetForm(); setModal('create') }}>
            <Plus size={13} /> 新建用户
          </button>
        )}
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      {notice && (
        <div className="flow-notice">
          <ShieldCheck size={15} />
          <div style={{ flex: 1 }}>
            <b>已提交审批 {notice.approvalId.slice(0, 8)}</b>
            <div>{notice.message}</div>
            <div className="muted" style={{ fontSize: 10.5 }}>
              演示审批人：<b>auditor / audit123</b>（安全审计员）——退出当前账号后登录即可批准。
            </div>
          </div>
          <button className="btn btn--ghost" onClick={() => openApp('approvals')}>打开审批队列</button>
          <button className="icon-btn" aria-label="关闭提示" onClick={() => setNotice(null)}>×</button>
        </div>
      )}

      {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}

      <div className="directory">
        <div className="directory__list">
          {loading && !users.length && <div className="app-loading"><Loader2 size={15} className="spin" /> 读取用户…</div>}
          <div className="directory__list-head">
            <span>成员</span><span>{users.length} / {total}</span>
          </div>
          {users.map((user) => (
            <button key={user.id} className="directory__row" aria-pressed={selectedId === user.id}
              onClick={() => setSelectedId(user.id)}>
              <span className="avatar" style={{ width: 34, height: 34, fontSize: 12, ['--hue' as string]: user.avatarHue }}>
                {user.displayName.slice(0, 1)}
              </span>
              <span className="directory__row-text">
                <b>{user.displayName} <span className="muted">@{user.username}</span></b>
                <small>{user.deptName} · {user.title}</small>
              </span>
              <span className="directory__row-meta">
                {user.roles.slice(0, 2).map((role) => <span className="chip" key={role}>{role}</span>)}
                <span className={`chip ${user.status === 'ACTIVE' ? 'chip--ok' : 'chip--bad'}`}>{userStatusLabel(user.status)}</span>
              </span>
            </button>
          ))}
          {!loading && users.length === 0 && (
            <div className="app-empty">
              <span className="app-empty__icon"><Users size={20} /></span>
              <b>没有匹配成员</b>
              <small>调整搜索，或新建一个用户。</small>
            </div>
          )}
        </div>

        <div className="directory__detail">
          {selected ? (
            <>
              <div className="directory__profile">
                <span className="avatar" style={{ width: 54, height: 54, fontSize: 19, ['--hue' as string]: selected.avatarHue }}>
                  {selected.displayName.slice(0, 1)}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <h3>{selected.displayName}</h3>
                  <div className="muted">@{selected.username} · {selected.deptName}</div>
                  <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', marginTop: 5 }}>
                    <span className={`chip ${selected.status === 'ACTIVE' ? 'chip--ok' : 'chip--bad'}`}>{userStatusLabel(selected.status)}</span>
                    {selected.roles.map((role) => <span className="chip chip--violet" key={role}>{role}</span>)}
                  </div>
                </div>
              </div>

              <dl className="kv-list">
                <dt>职位</dt><dd>{selected.title}</dd>
                <dt>部门</dt><dd>{selected.deptName}（#{selected.deptId}）</dd>
                <dt>数据范围</dt><dd>{meta ? scopeLabel(meta.scopeType) : '—'}</dd>
              </dl>

              <div className="directory__actions">
                {isSelf ? (
                  <button className="btn btn--primary" onClick={() => { resetForm(selected); setModal('edit') }}>
                    <Pencil size={12} /> 编辑我的资料
                  </button>
                ) : canWrite ? (
                  <>
                    <button className="btn btn--primary" disabled={!meta}
                      onClick={() => { resetForm(selected); setModal('edit') }}>
                      <Pencil size={12} /> 编辑资料
                    </button>
                    <button className="btn" disabled={!meta} onClick={() => {
                      const role = grantableRoles.find((r) => !selected.roles.includes(r.name))?.code
                        ?? grantableRoles[0]?.code ?? ''
                      setForm({ ...form, roleCode: role })
                      setModal('role')
                    }}>
                      <ShieldCheck size={12} /> 调整角色
                    </button>
                    <button className="btn btn--danger"
                      onClick={() => setModal('delete')}>
                      <Trash2 size={12} /> 删除用户
                    </button>
                  </>
                ) : (
                  <span className="chip chip--warn">只读账号，无法修改他人资料</span>
                )}
              </div>

              <div className="directory__hint">
                <CheckCircle2 size={13} />
                <div>
                  <b>所有写操作都经过统一执行通道</b>
                  <div>新增 / 编辑 / 调角色 / 删除会生成审批；另一位成员批准后才执行，并写入审计哈希链。</div>
                </div>
              </div>
            </>
          ) : (
            <div className="app-empty">
              <span className="app-empty__icon"><UserRound size={20} /></span>
              <b>选择一位成员</b>
              <small>右侧会显示资料、角色与可执行的写操作。</small>
            </div>
          )}
        </div>
      </div>

      <div className="directory__pager">
        <span className="muted">
          第 {totalPages === 0 ? 0 : page + 1} / {totalPages} 页 · 共 {total} 人
        </span>
        <div className="spacer" />
        <label className="muted" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          每页
          <select className="input" style={{ width: 64, height: 25, padding: '0 6px' }} value={size}
            onChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}>
            <option value={5}>5</option>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
          </select>
        </label>
        <button className="btn btn--ghost" disabled={page === 0}
          onClick={() => setPage((p) => Math.max(0, p - 1))}>上一页</button>
        <button className="btn btn--ghost" disabled={page + 1 >= totalPages}
          onClick={() => setPage((p) => p + 1)}>下一页</button>
      </div>

      {modal && (
        <div className="modal-backdrop" onClick={() => !submitting && setModal(null)}>
          <div className="modal glass glass-opaque directory-modal" onClick={(e) => e.stopPropagation()}>
            <h3>
              {modal === 'create' ? '新建用户'
                : modal === 'edit' ? (isSelf ? '编辑我的资料' : `编辑 ${selected?.displayName}`)
                  : modal === 'role' ? `调整 ${selected?.displayName} 的角色`
                    : `删除 ${selected?.displayName}？`}
            </h3>
            <p>
              {modal === 'delete'
                ? '删除是破坏性操作。确认后会先让你核对风险，然后以你的权限直接执行，不需要其他人审批。'
                : modal === 'role'
                  ? '角色变更属于提权操作，提交后由另一位成员复核；批准前不会改变数据库。'
                  : modal === 'edit' && isSelf
                    ? '修改本人姓名或职位会直接生效，无需审批。'
                    : '保存后直接生效；低可操作度修改会先让你确认风险。'}
            </p>

            {modal === 'create' && (
              <div className="form-grid">
                <label>username</label>
                <input className="input" aria-label="username" value={form.username} placeholder="例如 chenxi"
                  onChange={(e) => setForm({ ...form, username: e.target.value.toLowerCase() })} />
                <label>显示名称</label>
                <input className="input" aria-label="显示名称" value={form.displayName} placeholder="例如 陈曦"
                  onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
                <label>职位</label>
                <input className="input" aria-label="职位" value={form.title} placeholder="例如 运营负责人"
                  onChange={(e) => setForm({ ...form, title: e.target.value })} />
                <label>部门</label>
                <select className="input" aria-label="部门" value={form.deptId}
                  onChange={(e) => setForm({ ...form, deptId: Number(e.target.value) })}>
                  {departments.map((dept) => <option key={dept.id} value={dept.id}>{dept.name}</option>)}
                </select>
                <label>初始密码</label>
                <input className="input" aria-label="初始密码" type="password" value={form.password} placeholder="至少 6 位"
                  onChange={(e) => setForm({ ...form, password: e.target.value })} />
                <label>初始角色</label>
                <select className="input" aria-label="初始角色" value={form.roleCode}
                  onChange={(e) => setForm({ ...form, roleCode: e.target.value })}>
                  <option value="">不授予角色</option>
                  {grantableRoles.map((role) => <option key={role.code} value={role.code}>{role.name}（{role.code}）</option>)}
                </select>
              </div>
            )}

            {modal === 'edit' && (
              <div className="form-grid">
                <label>显示名称</label>
                <input className="input" aria-label="显示名称" value={form.displayName}
                  onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
                <label>职位</label>
                <input className="input" aria-label="职位" value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })} />
                {!isSelf && (
                  <>
                    <label>部门</label>
                    <select className="input" aria-label="部门" value={form.deptId}
                      onChange={(e) => setForm({ ...form, deptId: Number(e.target.value) })}>
                      {departments.map((dept) => <option key={dept.id} value={dept.id}>{dept.name}</option>)}
                    </select>
                    <label>状态</label>
                    <select className="input" aria-label="状态" value={form.status}
                      onChange={(e) => setForm({ ...form, status: e.target.value as 'ACTIVE' | 'DISABLED' })}>
                      <option value="ACTIVE">启用</option>
                      <option value="DISABLED">停用</option>
                    </select>
                  </>
                )}
              </div>
            )}

            {modal === 'role' && (
              <div className="form-grid">
                <label>授予角色</label>
                <select className="input" aria-label="授予角色" value={form.roleCode}
                  onChange={(e) => setForm({ ...form, roleCode: e.target.value })}>
                  {grantableRoles.map((role) => (
                    <option key={role.code} value={role.code}>{role.name}（{role.code}）</option>
                  ))}
                </select>
              </div>
            )}

            {grantableRoles.length === 0 && modal !== 'delete' && (
              <div className="login__error">当前身份没有可授予的角色（不能授予超出自身权限的角色）。</div>
            )}

            <div className="modal__actions">
              <button className="btn btn--ghost" disabled={submitting} onClick={() => setModal(null)}>取消</button>
              <button className={`btn ${modal === 'delete' ? 'btn--danger' : 'btn--primary'}`}
                disabled={submitting || savingSelf} onClick={() => void submit()}>
                {submitting || savingSelf ? <Loader2 size={13} className="spin" />
                  : modal === 'delete' ? <Trash2 size={13} /> : <Plus size={13} />}
                {modal === 'delete' ? '删除…'
                  : modal === 'role' ? '提交角色审批'
                    : modal === 'edit' && isSelf ? '保存（直接生效）' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmPrompt && (
        <div className="modal-backdrop" onClick={() => setConfirmPrompt(null)}>
          <div className="modal glass glass-opaque" onClick={(event) => event.stopPropagation()}>
            <h3>确认这次操作</h3>
            <p>{confirmPrompt.outcome.confirmation?.intent ?? '这次操作风险较高。'}</p>
            <div className="risk-confirm-list">
              <div>
                <b>{riskLevelLabel(confirmPrompt.outcome.confirmation?.riskLevel, confirmPrompt.outcome.confirmation?.operability)} · 可操作度
                  {' '}{confirmPrompt.outcome.confirmation?.operability ?? 50}%</b>
                {confirmPrompt.outcome.confirmation?.riskReasons?.map((reason) => (
                  <small key={reason}>{reason}</small>
                ))}
              </div>
            </div>
            <label className="risk-check">
              <input type="checkbox" checked={riskChecked}
                onChange={(event) => setRiskChecked(event.target.checked)} />
              我已了解风险，确认以我的权限执行
            </label>
            <div className="modal__actions">
              <button className="btn btn--ghost" onClick={() => setConfirmPrompt(null)}>取消</button>
              <button className="btn btn--danger" disabled={!riskChecked || submitting}
                onClick={() => void confirmRisk()}>
                <ShieldCheck size={13} /> 确认执行
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
