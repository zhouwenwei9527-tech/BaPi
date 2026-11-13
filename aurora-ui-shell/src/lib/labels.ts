/**
 * One place for every user-facing enum label. API codes stay stable and
 * English; the UI always renders Chinese.
 */
export function userStatusLabel(status?: string): string {
  if (status === 'ACTIVE') return '启用'
  if (status === 'DISABLED') return '停用'
  return status ?? '—'
}

export function approvalStatusLabel(status?: string): string {
  switch (status) {
    case 'PENDING': return '待审批'
    case 'APPROVED': return '已批准'
    case 'EXECUTED': return '已执行'
    case 'REJECTED': return '已拒绝'
    case 'FAILED': return '失败'
    case 'EXPIRED': return '已过期'
    case 'CANCELLED': return '已取消'
    default: return status ?? '—'
  }
}

export function requestStatusLabel(status?: string): string {
  switch (status) {
    case 'PENDING': return '待审批'
    case 'APPROVED': return '已批准'
    case 'REJECTED': return '已拒绝'
    case 'FAILED': return '失败'
    default: return status ?? '—'
  }
}

export function sideEffectLabel(effect?: string): string {
  switch (effect) {
    case 'READ': return '只读'
    case 'WRITE': return '写入'
    case 'DESTRUCTIVE': return '破坏性'
    default: return effect ?? '—'
  }
}

export function policyLabel(policy?: string): string {
  switch (policy) {
    case 'AUTO': return '自动执行'
    case 'SELF_CONFIRM': return '本人确认'
    case 'ASK': return '需复核'
    case 'DENY_BY_DEFAULT': return '默认拒绝'
    default: return policy ?? '—'
  }
}

export function toolStatusLabel(status?: string): string {
  switch (status) {
    case 'EXECUTED': return '已执行'
    case 'PENDING': return '待审批'
    case 'CONFIRMATION_REQUIRED': return '待确认'
    case 'DENIED': return '已拒绝'
    case 'FAILED': return '失败'
    case 'SUCCESS': return '成功'
    default: return status ?? '—'
  }
}

export function pluginStatusLabel(status?: string): string {
  switch (status) {
    case 'INSTALLED': return '已安装'
    case 'PENDING_AUTH': return '待授权'
    case 'ACTIVE': return '已激活'
    case 'DISABLED': return '已停用'
    default: return status ?? '—'
  }
}

export function riskLevelLabel(level?: string, operability?: number): string {
  if (level === 'LOW') return '低风险'
  if (level === 'MEDIUM') return '中风险'
  if (level === 'HIGH') return '高风险'
  if (level === 'CRITICAL') return '极高风险'
  if (operability === undefined) return '—'
  if (operability >= 85) return '低风险'
  if (operability >= 60) return '中风险'
  if (operability >= 30) return '高风险'
  return '极高风险'
}

export function resourceTypeLabel(type?: string): string {
  switch (type) {
    case 'MENU': return '菜单'
    case 'API': return '接口'
    case 'TOOL': return '工具'
    case 'DATA': return '数据'
    default: return type ?? '—'
  }
}

export function actorTypeLabel(type?: string): string {
  return type === 'AGENT' ? 'Agent' : type === 'USER' ? '用户' : (type ?? '—')
}

export function agentStatusLabel(status?: string): string {
  switch (status) {
    case 'OK': return '完成'
    case 'PENDING': return '待审批'
    case 'CONFIRMATION_REQUIRED': return '待确认'
    case 'DENIED': return '已拒绝'
    case 'FAILED': return '失败'
    case 'BUDGET_EXCEEDED': return '预算耗尽'
    case 'CIRCUIT_OPEN': return '熔断保护'
    default: return status ?? '—'
  }
}

export function decisionLabel(decision?: string | null): string {
  if (!decision) return '—'
  switch (decision) {
    case 'OK': return '正常'
    case 'ERROR': return '异常'
    case 'EXECUTED': return '已执行'
    case 'EXECUTING': return '执行中'
    case 'PENDING': return '待审批'
    case 'AUTO': return '自动执行'
    case 'APPROVED': return '已批准'
    case 'REJECTED': return '已拒绝'
    case 'FAILED': return '失败'
    case 'EXPIRED': return '已过期'
    case 'DENIED': return '已拒绝'
    case 'PERMISSION_DENIED': return '权限不足'
    case 'TOOL_NOT_REGISTERED': return '工具未注册'
    case 'TOOL_NOT_ACTIVE': return '工具未激活'
    case 'DENIED_BY_POLICY': return '策略拒绝'
    case 'SCHEMA_INVALID': return '参数不合法'
    case 'BUDGET_EXCEEDED': return '预算耗尽'
    case 'CIRCUIT_OPEN': return '熔断保护'
    case 'TOOL_TIMEOUT': return '执行超时'
    case 'TOOL_FAILED': return '执行失败'
    case 'AUDIT_UNAVAILABLE': return '审计不可用'
    case 'TEXT_QUERY': return '文本检索'
    case 'IMAGE_QUERY': return '图片检索'
    case 'UPLOAD': return '上传入库'
    case 'SWITCH_ROLE': return '切换角色'
    case 'PROFILE_UPDATED': return '资料更新'
    case 'PERMISSION_REQUESTED': return '权限申请'
    case 'PERMISSION_GRANTED': return '权限已授予'
    case 'PERMISSION_REJECTED': return '权限已拒绝'
    case 'PERMISSION_GRANT_FAILED': return '授权失败'
    case 'ACTIVATE_PLUGIN': return '启用插件'
    case 'DISABLE_PLUGIN': return '停用插件'
    case 'UNINSTALL_PLUGIN': return '卸载插件'
    case 'PLUGIN_INSTALL_REJECTED': return '插件安装被拒绝'
    case 'INSTALL_INCOMPATIBLE_PLUGIN': return '安装不兼容插件'
    case 'CREATE_ROLE': return '创建角色'
    case 'UPDATE_ROLE': return '更新角色'
    case 'SET_ROLE_PERMISSIONS': return '设置角色权限'
    case 'GRANT_PERMISSION': return '授予权限'
    case 'REVOKE_PERMISSION': return '撤销权限'
    case 'CREATE_MENU': return '创建菜单'
    case 'UPDATE_MENU': return '更新菜单'
    case 'DELETE_MENU': return '删除菜单'
    case 'CHAT': return '对话'
    default: return decision
  }
}

export function sourceLabel(source?: string): string {
  if (!source) return '—'
  if (source === 'core') return '内核'
  if (source.startsWith('plugin:')) return '插件'
  return source
}

export function mountPointLabel(point?: string): string {
  switch (point) {
    case 'desktop.icons': return '桌面图标'
    case 'palette.results': return '搜索结果'
    case 'finder.sidebar': return '访达侧边栏'
    case 'session.context': return '会话上下文'
    default: return point ?? '—'
  }
}
