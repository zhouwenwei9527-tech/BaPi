import type {
  AgentMessage, AgentReply, AgentSession, ApprovalView, AuditEvent, ChainVerification,
  DecisionOutcome, DesktopData, DockView, DocumentDetail, DocumentView, GatewayOutcome, MatrixView,
  PluginView,
  MenuNode, PermissionRequestView, PermissionRoleOption, ReplayView, ResourceAdminView,
  RoleAdminView, RoleView, SearchResult, SessionView, ToolView, TraceStep, TraceSummary,
  UploadResult, UserMeta, UserPage, UserView, WallpaperHistoryView, WallpaperView
} from '../types'

const TOKEN_KEY = 'bapi.token'

export class ApiError extends Error {
  code: string
  status: number
  constructor(code: string, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Storage can be unavailable in private mode; the in-memory store still works.
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(options.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(path, { ...options, headers })
  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent('aurora:unauthorized'))
  }
  const text = await response.text()
  let body: any = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    throw new ApiError('INTERNAL_ERROR', '服务端返回了无法解析的响应', response.status)
  }
  if (!response.ok || body?.success === false) {
    const code = body?.error?.code ?? 'INTERNAL_ERROR'
    const message = body?.error?.message ?? `请求失败（${response.status}）`
    throw new ApiError(code, message, response.status)
  }
  return body.data as T
}

const json = (body: unknown) => JSON.stringify(body)
const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : json(body) })

export const api = {
  login: (username: string, password: string) =>
    post<{ token: string; expiresAt: string; principal: DesktopData['principal']; roles: RoleView[] }>(
      '/api/auth/login', { username, password }),
  me: () => request<SessionView>('/api/auth/me'),
  logout: () => post<boolean>('/api/auth/logout'),
  switchRole: (roleCode: string) => post<SessionView>('/api/auth/switch-role', { roleCode }),
  desktop: () => request<DesktopData>('/api/desktop'),

  wallpaper: () => request<WallpaperView>('/api/preferences/wallpaper'),
  setWallpaperPreset: (presetId: string) =>
    request<WallpaperView>('/api/preferences/wallpaper', {
      method: 'PUT', body: json({ presetId })
    }),
  uploadWallpaper: (file: Blob, fileName: string) => {
    const form = new FormData()
    form.append('file', file, fileName)
    return request<WallpaperView>('/api/preferences/wallpaper', { method: 'POST', body: form })
  },
  resetWallpaper: () =>
    request<WallpaperView>('/api/preferences/wallpaper', { method: 'DELETE' }),
  wallpaperHistory: () =>
    request<WallpaperHistoryView[]>('/api/preferences/wallpaper/history'),
  applyWallpaperHistory: (id: number) =>
    request<WallpaperView>(`/api/preferences/wallpaper/history/${id}`, { method: 'POST' }),
  dockPreference: () => request<DockView>('/api/preferences/dock'),
  saveDockPreference: (apps: string[]) =>
    request<DockView>('/api/preferences/dock', { method: 'PUT', body: json({ apps }) }),

  search: (q: string, limit = 8) =>
    request<SearchResult>(`/api/search?q=${encodeURIComponent(q)}&limit=${limit}`),
  imageSearch: (file: File, caption: string, q = '', limit = 8) => {
    const form = new FormData()
    form.append('file', file)
    form.append('caption', caption)
    form.append('q', q)
    form.append('limit', String(limit))
    return request<SearchResult>('/api/search/image', { method: 'POST', body: form })
  },

  tools: () => request<ToolView[]>('/api/tools'),
  invokeTool: (name: string, args: unknown, sessionId?: string, confirm = false) =>
    post<GatewayOutcome>(`/api/tools/${encodeURIComponent(name)}/invoke`,
      { args, sessionId, confirm }),

  approvals: (status?: string) =>
    request<ApprovalView[]>(`/api/approvals${status ? `?status=${status}` : ''}`),
  decideApproval: (id: string, approve: boolean, note?: string, confirmRisk = false) =>
    post<DecisionOutcome>(`/api/approvals/${id}/decide`, { approve, note, confirmRisk }),
  batchDecide: (ids: string[], approve: boolean, note?: string, confirmRisk = false) =>
    post<DecisionOutcome[]>('/api/approvals/batch-decide', { ids, approve, note, confirmRisk }),

  audit: (limit = 80) => request<AuditEvent[]>(`/api/audit?limit=${limit}`),
  verifyAudit: () => request<ChainVerification>('/api/audit/verify'),
  replay: (traceId: string) => request<ReplayView>(`/api/audit/replay/${encodeURIComponent(traceId)}`),
  traces: (limit = 20) => request<TraceSummary[]>(`/api/audit/traces?limit=${limit}`),
  trace: (traceId: string) => request<TraceStep[]>(`/api/audit/trace/${encodeURIComponent(traceId)}`),

  plugins: () => request<PluginView[]>('/api/plugins'),
  activatePlugin: (id: string) => post<PluginView>(`/api/plugins/${id}/activate`),
  disablePlugin: (id: string) => post<PluginView>(`/api/plugins/${id}/disable`),
  uninstallPlugin: (id: string) => request<boolean>(`/api/plugins/${id}`, { method: 'DELETE' }),
  installUnknownPermission: () => post<PluginView>('/api/plugins/demo/unknown-permission'),
  installIncompatible: () => post<PluginView>('/api/plugins/demo/incompatible'),

  rbacMatrix: () => request<MatrixView>('/api/rbac/matrix'),
  rbacResources: () => request<{ code: string; name: string; type: string }[]>('/api/rbac/resources'),
  rbacAdminRoles: () => request<RoleAdminView[]>('/api/rbac/admin/roles'),
  createRbacRole: (body: { code: string; name: string; description: string; scopeType: string }) =>
    post<RoleAdminView>('/api/rbac/admin/roles', body),
  updateRbacRole: (id: number, body: { name: string; description: string; scopeType: string }) =>
    request<RoleAdminView>(`/api/rbac/admin/roles/${id}`, { method: 'PATCH', body: json(body) }),
  setRolePermissions: (id: number, permissions: string[]) =>
    request<RoleAdminView>(`/api/rbac/admin/roles/${id}/permissions`, {
      method: 'PUT', body: json({ permissions })
    }),
  toggleRolePermission: (id: number, code: string, enabled: boolean) =>
    post<RoleAdminView>(`/api/rbac/admin/roles/${id}/permissions/${encodeURIComponent(code)}/toggle?enabled=${enabled}`),
  rbacAdminResources: () => request<ResourceAdminView[]>('/api/rbac/admin/resources'),
  rbacMenu: () => request<MenuNode[]>('/api/rbac/admin/menu'),
  createMenuGroup: (body: { name: string; parentCode?: string; sortOrder: number; icon: string; description: string }) =>
    post<ResourceAdminView>('/api/rbac/admin/menu', body),
  updateMenuResource: (code: string, body: { name: string; parentCode?: string; sortOrder: number; icon: string; description: string }) =>
    request<ResourceAdminView>(`/api/rbac/admin/menu/${encodeURIComponent(code)}`, {
      method: 'PATCH', body: json(body)
    }),
  deleteMenuGroup: (code: string) =>
    request<boolean>(`/api/rbac/admin/menu/${encodeURIComponent(code)}`, { method: 'DELETE' }),

  permissionRoles: () => request<PermissionRoleOption[]>('/api/permission-requests/roles'),
  permissionRequests: () => request<PermissionRequestView[]>('/api/permission-requests'),
  createPermissionRequest: (roleCode: string, reason: string) =>
    post<PermissionRequestView>('/api/permission-requests', { roleCode, reason }),
  decidePermissionRequest: (id: string, approve: boolean, note?: string) =>
    post<{ request: PermissionRequestView }>(`/api/permission-requests/${id}/decide`, { approve, note }),

  knowledge: () => request<DocumentView[]>('/api/knowledge'),
  document: (id: string) => request<DocumentDetail>(`/api/knowledge/${id}`),
  updateAcl: (id: string, body: unknown) =>
    request<{ docId: string; aclVersion: number; note: string }>(`/api/knowledge/${id}/acl`, {
      method: 'PATCH', body: json(body)
    }),
  reindex: () => post<{ documents: number }>('/api/knowledge/reindex'),
  users: (q = '', page = 0, size = 20) => request<UserPage>(`/api/users?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  userMeta: () => request<UserMeta>('/api/users/meta'),
  updateMyProfile: (displayName: string, title: string) =>
    request<UserView>('/api/users/me/profile', {
      method: 'PATCH', body: json({ displayName, title })
    }),
  uploadKnowledge: (form: FormData) =>
    request<UploadResult>('/api/knowledge/upload', { method: 'POST', body: form }),

  agentChat: (message: string, sessionId?: string, confirm = false) =>
    post<AgentReply>('/api/agent/chat', { message, sessionId, confirm }),
  agentSessions: () => request<AgentSession[]>('/api/agent/sessions'),
  createAgentSession: (title?: string) => post<AgentSession>('/api/agent/sessions', { title }),
  agentMessages: (id: string) => request<AgentMessage[]>(`/api/agent/sessions/${id}/messages`),
  agentReplay: (traceId: string) => request<ReplayView>(`/api/agent/replay/${traceId}`),
  agentCalls: () => request<unknown[]>('/api/agent/calls'),

  overview: () => request<Record<string, unknown>>('/api/system/overview')
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}：${error.message}`
  if (error instanceof Error) return error.message
  return '未知错误'
}
