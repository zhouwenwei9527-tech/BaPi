export function formatTime(iso?: string, withSeconds = false): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: withSeconds ? '2-digit' : undefined,
    hour12: false
  })
}

export function formatDateTime(iso?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false
  })
}

export function relativeTime(iso?: string): string {
  if (!iso) return '—'
  const delta = Date.now() - new Date(iso).getTime()
  if (delta < 60_000) return '刚刚'
  if (delta < 3_600_000) return `${Math.floor(delta / 60_000)} 分钟前`
  if (delta < 86_400_000) return `${Math.floor(delta / 3_600_000)} 小时前`
  return `${Math.floor(delta / 86_400_000)} 天前`
}

export function humanSize(bytes?: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function shortId(id?: string, length = 8): string {
  return id ? id.slice(0, length) : '—'
}

export function decisionTone(decision?: string): 'ok' | 'warn' | 'bad' | 'info' | '' {
  if (!decision) return ''
  if (['EXECUTED', 'AUTO', 'OK', 'APPROVED'].includes(decision)) return 'ok'
  if (['PENDING', 'EXECUTING', 'ASK'].includes(decision)) return 'warn'
  if (['DENIED', 'REJECTED', 'FAILED', 'ERROR', 'TOOL_TIMEOUT', 'TOOL_FAILED',
    'PERMISSION_DENIED', 'AUDIT_UNAVAILABLE', 'SELF_APPROVAL_FORBIDDEN'].includes(decision)) return 'bad'
  return 'info'
}

export function sideEffectTone(effect?: string): 'ok' | 'warn' | 'bad' | '' {
  if (effect === 'READ') return 'ok'
  if (effect === 'WRITE') return 'warn'
  if (effect === 'DESTRUCTIVE') return 'bad'
  return ''
}

export function clockText(now = new Date()): string {
  return now.toLocaleString('zh-CN', {
    month: 'numeric', day: 'numeric', weekday: 'short',
    hour: '2-digit', minute: '2-digit', hour12: false
  })
}

export function scopeLabel(type?: string): string {
  switch (type) {
    case 'ALL': return '全部数据'
    case 'DEPT': return '本部门'
    case 'DEPT_AND_CHILD': return '部门及子部门'
    case 'SELF': return '仅本人'
    case 'CUSTOM': return '自定义部门'
    default: return type ?? '—'
  }
}

export function visibilityLabel(v?: string): string {
  switch (v) {
    case 'PUBLIC': return '公开'
    case 'ROLES': return '角色可见'
    case 'DEPTS': return '部门可见'
    case 'OWNER': return '仅本人'
    default: return v ?? '—'
  }
}
