import { useEffect, useMemo, useState } from 'react'
import {
  ChevronDown, ChevronRight, Loader2, Pencil, Plus, RefreshCw, ShieldCheck, Trash2, Users
} from '../components/icons'
import { resourceTypeLabel } from '../lib/labels'
import { api, errorMessage } from '../lib/api'
import { scopeLabel } from '../lib/format'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { MenuNode, MatrixView, ResourceAdminView, RoleAdminView } from '../types'

const TYPE_TONE: Record<string, string> = { MENU: 'info', API: '', TOOL: 'warn', DATA: 'ok' }
const SCOPES = ['ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF']

interface RoleForm {
  mode: 'create' | 'edit'
  id?: number
  code: string
  name: string
  description: string
  scopeType: string
}

interface MenuForm {
  mode: 'create' | 'edit'
  code?: string
  name: string
  parentCode: string
  sortOrder: number
  icon: string
  description: string
}

export function Rbac() {
  const principal = useAuth((s) => s.desktop?.principal)
  const refreshDesktop = useAuth((s) => s.refreshDesktop)
  const toast = useShell((s) => s.toast)
  const permissions = principal?.permissions ?? []
  const canManage = can(permissions, 'rbac:manage')
  const [matrix, setMatrix] = useState<MatrixView | null>(null)
  const [adminRoles, setAdminRoles] = useState<RoleAdminView[]>([])
  const [menu, setMenu] = useState<MenuNode[]>([])
  const [resources, setResources] = useState<ResourceAdminView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [roleFocus, setRoleFocus] = useState<string | null>(null)
  const [tab, setTab] = useState<'matrix' | 'menu'>('matrix')
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [roleForm, setRoleForm] = useState<RoleForm | null>(null)
  const [menuForm, setMenuForm] = useState<MenuForm | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const nextMatrix = await api.rbacMatrix()
      setMatrix(nextMatrix)
      if (canManage) {
        const [roles, menuTree, allResources] = await Promise.all([
          api.rbacAdminRoles(), api.rbacMenu(), api.rbacAdminResources()
        ])
        setAdminRoles(roles)
        setMenu(menuTree)
        setResources(allResources)
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const rows = useMemo(() => {
    if (!matrix) return []
    const needle = query.trim().toLowerCase()
    if (!needle) return matrix.rows
    return matrix.rows.filter((row) =>
      `${row.code} ${row.name} ${row.type}`.toLowerCase().includes(needle))
  }, [matrix, query])

  const roles = matrix?.roles ?? []
  const focusRole = roleFocus ?? principal?.roles[0] ?? null
  const focusView = adminRoles.find((r) => r.code === focusRole)
  const focusCount = focusRole ? rows.filter((row) => row.roles.includes(focusRole)).length : 0
  const menuResources = resources.filter((resource) => resource.type === 'MENU')

  const toggle = async (roleCode: string, code: string, enabled: boolean) => {
    const role = roles.find((r) => r.code === roleCode)
    if (!role) return
    setBusy(`${roleCode}-${code}`)
    try {
      await api.toggleRolePermission(role.id, code, enabled)
      toast(`${enabled ? '已授予' : '已撤销'} ${roleCode} → ${code}`, 'ok')
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  const openRoleModal = (mode: 'create' | 'edit') => {
    if (mode === 'create') {
      setRoleForm({ mode, code: '', name: '', description: '', scopeType: 'SELF' })
    } else if (focusView) {
      setRoleForm({
        mode, id: focusView.id, code: focusView.code, name: focusView.name,
        description: focusView.description, scopeType: focusView.scopeType
      })
    }
  }

  const saveRole = async () => {
    if (!roleForm) return
    if (!roleForm.name.trim()) {
      toast('角色名称不能为空', 'bad')
      return
    }
    setSaving(true)
    try {
      if (roleForm.mode === 'create') {
        await api.createRbacRole({
          code: roleForm.code, name: roleForm.name, description: roleForm.description,
          scopeType: roleForm.scopeType
        })
        toast('角色已创建，可在矩阵中授权', 'ok')
      } else if (roleForm.id) {
        await api.updateRbacRole(roleForm.id, {
          name: roleForm.name, description: roleForm.description, scopeType: roleForm.scopeType
        })
        toast('角色信息已更新', 'ok')
      }
      setRoleForm(null)
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setSaving(false)
    }
  }

  const openMenuModal = (mode: 'create' | 'edit', node?: MenuNode) => {
    if (mode === 'create') {
      setMenuForm({ mode, name: '', parentCode: '', sortOrder: 50, icon: 'folder', description: '' })
    } else if (node) {
      const resource = resources.find((item) => item.code === node.code)
      setMenuForm({
        mode, code: node.code, name: node.name, parentCode: node.parentCode ?? '',
        sortOrder: node.sortOrder, icon: node.icon,
        description: resource?.description ?? ''
      })
    }
  }

  const saveMenu = async () => {
    if (!menuForm) return
    if (!menuForm.name.trim()) {
      toast('菜单名称不能为空', 'bad')
      return
    }
    setSaving(true)
    try {
      const body = {
        name: menuForm.name, parentCode: menuForm.parentCode || '',
        sortOrder: menuForm.sortOrder, icon: menuForm.icon,
        description: menuForm.description
      }
      if (menuForm.mode === 'create') {
        await api.createMenuGroup(body)
        toast('菜单组已创建；可在角色矩阵中授权可见性', 'ok')
      } else if (menuForm.code) {
        await api.updateMenuResource(menuForm.code, body)
        toast('菜单已更新，顶部「功能」菜单会同步', 'ok')
      }
      setMenuForm(null)
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setSaving(false)
    }
  }

  const deleteMenu = async (code: string) => {
    setBusy(`menu-${code}`)
    try {
      await api.deleteMenuGroup(code)
      toast('自定义菜单组已删除', 'ok')
      setConfirmDelete(null)
      await load()
      void refreshDesktop()
    } catch (err) {
      toast(errorMessage(err), 'bad')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="app" style={{ flexDirection: 'column' }}>
      <div className="app-toolbar">
        <span className="app-toolbar__title">权限中心</span>
        <span className="chip chip--info">{roles.length} 角色</span>
        <span className="chip">{matrix?.rows.length ?? 0} 资源码</span>
        {focusRole && <span className="chip">{focusRole} · {focusCount} 项</span>}
        {canManage && <span className="chip chip--violet"><ShieldCheck size={10} /> 超级管理员</span>}
        <div className="spacer" />
        {tab === 'matrix' && (
          <input className="input" placeholder="搜索资源码…" value={query}
            onChange={(e) => setQuery(e.target.value)} style={{ width: 180, height: 30 }} />
        )}
        <button className="icon-btn" aria-label="刷新" onClick={() => void load()}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      {canManage && (
        <div className="rbac-subbar">
          <div className="segmented">
            <button aria-pressed={tab === 'matrix'} onClick={() => setTab('matrix')}>角色矩阵</button>
            <button aria-pressed={tab === 'menu'} onClick={() => setTab('menu')}>菜单管理</button>
          </div>
          <div className="spacer" />
          {tab === 'matrix' && (
            <>
              <button className="btn btn--ghost" onClick={() => openRoleModal('create')}>
                <Plus size={12} /> 新建角色
              </button>
              {focusView && (
                <button className="btn btn--ghost" onClick={() => openRoleModal('edit')}>
                  <Pencil size={12} /> 编辑角色
                </button>
              )}
              <button className={`btn ${editing ? 'btn--primary' : 'btn--ghost'}`}
                onClick={() => setEditing((value) => !value)}>
                {editing ? '完成编辑' : '编辑权限'}
              </button>
            </>
          )}
          {tab === 'menu' && (
            <button className="btn btn--primary" onClick={() => openMenuModal('create')}>
              <Plus size={12} /> 新建菜单组
            </button>
          )}
        </div>
      )}

      {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}
      {loading && !matrix && <div className="app-loading"><Loader2 size={15} className="spin" /> 读取权限矩阵…</div>}

      {tab === 'matrix' && (
        <>
          <div className="rbac-role-cards">
            {roles.map((role) => (
              <button key={role.id} className="rbac-role" data-active={role.code === focusRole}
                onClick={() => setRoleFocus(role.code === roleFocus ? null : role.code)}>
                <b>{role.name} <span className="mono" style={{ fontWeight: 400, fontSize: 10 }}>{role.code}</span></b>
                <small>{role.description}</small>
                <small style={{ color: 'var(--accent)' }}>
                  <Users size={9} style={{ verticalAlign: -1 }} /> {scopeLabel(role.scopeType)}
                </small>
              </button>
            ))}
          </div>

          <div className="rbac-matrix-wrap" tabIndex={0} role="region" aria-label="权限矩阵">
            <table className="rbac-table">
              <thead>
                <tr>
                  <th>资源码 / 名称</th>
                  {roles.map((role) => (
                    <th key={role.code} style={{ minWidth: 78, color: role.code === focusRole ? 'var(--accent)' : undefined }}>
                      {role.name}
                      <div className="mono" style={{ fontSize: 8.5, fontWeight: 400, opacity: 0.65 }}>{role.code}</div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.code}>
                    <td>
                      <span className="perm-code">{row.code}</span>
                      <span className={`chip chip--${TYPE_TONE[row.type] ?? ''}`} style={{ marginLeft: 7, fontSize: 8.5 }}>
                        {row.type}
                      </span>
                      <div className="muted" style={{ fontSize: 9.5, marginTop: 1 }}>{row.name}</div>
                    </td>
                    {roles.map((role) => {
                      const enabled = row.roles.includes(role.code)
                      const isAdminWildcard = role.code === 'admin'
                      const key = `${role.code}-${row.code}`
                      return (
                        <td key={role.code} title={isAdminWildcard ? '超级管理员使用通配 *' : role.code}>
                          {editing && canManage && !isAdminWildcard ? (
                            <button className="perm-toggle" aria-pressed={enabled}
                              aria-label={`${enabled ? '撤销' : '授予'} ${role.code} ${row.code}`}
                              disabled={busy === key}
                              onClick={() => void toggle(role.code, row.code, !enabled)}>
                              <span className="perm-dot" data-on={enabled} />
                            </button>
                          ) : (
                            <span className="perm-dot" data-on={enabled}
                              style={{ opacity: focusRole && role.code !== focusRole ? 0.55 : 1 }} />
                          )}
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ padding: '9px 15px', borderTop: '1px solid var(--line-soft)', display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <span className="muted" style={{ fontSize: 10.5 }}>
              资源码格式 <span className="mono">resource:action</span>；菜单权限决定入口可见性，Tool 权限决定 AI 能否执行动作。
            </span>
            <span className="chip chip--ok">桌面 / 菜单 / 全局搜索共用同一权限守卫</span>
            {canManage && <span className="chip chip--violet">超级管理员通配 *，无需逐项授权</span>}
          </div>
        </>
      )}

      {tab === 'menu' && (
        <div className="menu-admin" tabIndex={0} role="region" aria-label="菜单管理">
          <div className="menu-admin__hint">
            <ShieldCheck size={13} />
            <span>菜单来自系统资源中的「菜单」类型。改名、换父级、调整排序后，顶部「功能」菜单与路由守卫会同步。角色能否看到某个菜单，由角色矩阵中的资源授权决定。</span>
          </div>
          {menu.map((node) => (
            <MenuRow key={node.code} node={node} level={0}
              onEdit={(n) => openMenuModal('edit', n)}
              onDelete={(code) => setConfirmDelete(code)}
              confirmDelete={confirmDelete}
              deleteBusy={busy}
              onConfirmDelete={deleteMenu}
              onCancelDelete={() => setConfirmDelete(null)} />
          ))}
          {menu.length === 0 && <div className="app-empty">没有菜单资源</div>}
        </div>
      )}

      {roleForm && (
        <div className="modal-backdrop" onClick={() => !saving && setRoleForm(null)}>
          <div className="modal glass glass-opaque" onClick={(e) => e.stopPropagation()}>
            <h3>{roleForm.mode === 'create' ? '新建角色' : `编辑角色 ${roleForm.code}`}</h3>
            <p>修改权限后缓存立即失效；数据范围决定该角色能看到哪些部门/本人数据。</p>
            <div className="form-grid">
              <label>角色 code</label>
              <input className="input" aria-label="角色 code" value={roleForm.code}
                disabled={roleForm.mode === 'edit'}
                onChange={(e) => setRoleForm({ ...roleForm, code: e.target.value.toLowerCase() })} />
              <label>名称</label>
              <input className="input" aria-label="角色名称" value={roleForm.name}
                onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })} />
              <label>描述</label>
              <input className="input" aria-label="角色描述" value={roleForm.description}
                onChange={(e) => setRoleForm({ ...roleForm, description: e.target.value })} />
              <label>数据范围</label>
              <select className="input" aria-label="数据范围" value={roleForm.scopeType}
                onChange={(e) => setRoleForm({ ...roleForm, scopeType: e.target.value })}>
                {SCOPES.map((scope) => <option key={scope} value={scope}>{scopeLabel(scope)}</option>)}
              </select>
            </div>
            <div className="modal__actions">
              <button className="btn btn--ghost" disabled={saving} onClick={() => setRoleForm(null)}>取消</button>
              <button className="btn btn--primary" disabled={saving} onClick={() => void saveRole()}>
                {saving ? <Loader2 size={13} className="spin" /> : <ShieldCheck size={13} />} 保存
              </button>
            </div>
          </div>
        </div>
      )}

      {menuForm && (
        <div className="modal-backdrop" onClick={() => !saving && setMenuForm(null)}>
          <div className="modal glass glass-opaque" onClick={(e) => e.stopPropagation()}>
            <h3>{menuForm.mode === 'create' ? '新建菜单组' : `编辑菜单 ${menuForm.code}`}</h3>
            <p>自定义菜单组用于组织已有功能入口；子项的 code 仍对应实际资源与路由守卫。</p>
            <div className="form-grid">
              <label>名称</label>
              <input className="input" aria-label="菜单名称" value={menuForm.name}
                onChange={(e) => setMenuForm({ ...menuForm, name: e.target.value })} />
              <label>父级</label>
              <select className="input" aria-label="父级菜单" value={menuForm.parentCode}
                onChange={(e) => setMenuForm({ ...menuForm, parentCode: e.target.value })}>
                <option value="">无（顶层）</option>
                {menuResources.filter((r) => r.code !== menuForm.code).map((resource) => (
                  <option key={resource.code} value={resource.code}>{resource.name} · {resource.code}</option>
                ))}
              </select>
              <label>排序</label>
              <input className="input" aria-label="排序" type="number" value={menuForm.sortOrder}
                onChange={(e) => setMenuForm({ ...menuForm, sortOrder: Number(e.target.value) })} />
              <label>图标</label>
              <input className="input" aria-label="图标" value={menuForm.icon}
                onChange={(e) => setMenuForm({ ...menuForm, icon: e.target.value })} />
              <label>描述</label>
              <input className="input" aria-label="菜单描述" value={menuForm.description}
                onChange={(e) => setMenuForm({ ...menuForm, description: e.target.value })} />
            </div>
            <div className="modal__actions">
              <button className="btn btn--ghost" disabled={saving} onClick={() => setMenuForm(null)}>取消</button>
              <button className="btn btn--primary" disabled={saving} onClick={() => void saveMenu()}>
                {saving ? <Loader2 size={13} className="spin" /> : <Plus size={13} />} 保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function MenuRow({ node, level, onEdit, onDelete, confirmDelete, deleteBusy, onConfirmDelete,
                   onCancelDelete }: {
  node: MenuNode
  level: number
  onEdit: (node: MenuNode) => void
  onDelete: (code: string) => void
  confirmDelete: string | null
  deleteBusy: string | null
  onConfirmDelete: (code: string) => void
  onCancelDelete: () => void
}) {
  const custom = node.code.startsWith('menu:custom-')
  return (
    <>
      <div className="menu-admin__row" style={{ paddingLeft: 14 + level * 18 }}>
        <ChevronRight size={12} style={{ color: 'var(--faint)' }} />
        <b>{node.name}</b>
        <span className="mono muted">{node.code}</span>
        <span className="chip" style={{ fontSize: 8.5 }}>sort {node.sortOrder}</span>
        <div className="spacer" />
        <button className="icon-btn" aria-label={`编辑 ${node.name}`} onClick={() => onEdit(node)}>
          <Pencil size={12} />
        </button>
        {custom && (confirmDelete === node.code ? (
          <>
            <button className="btn btn--danger" style={{ height: 24, fontSize: 10 }}
              disabled={deleteBusy === `menu-${node.code}`}
              onClick={() => onConfirmDelete(node.code)}>
              <Trash2 size={10} /> 确认删除
            </button>
            <button className="btn btn--ghost" style={{ height: 24, fontSize: 10 }}
              onClick={onCancelDelete}>取消</button>
          </>
        ) : (
          <button className="icon-btn" aria-label={`删除 ${node.name}`}
            onClick={() => onDelete(node.code)}>
            <Trash2 size={12} />
          </button>
        ))}
      </div>
      {node.children.map((child) => (
        <MenuRow key={child.code} node={child} level={level + 1} onEdit={onEdit}
          onDelete={onDelete} confirmDelete={confirmDelete} deleteBusy={deleteBusy}
          onConfirmDelete={onConfirmDelete} onCancelDelete={onCancelDelete} />
      ))}
    </>
  )
}
