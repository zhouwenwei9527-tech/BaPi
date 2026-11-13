import { useEffect, useMemo, useState } from 'react'
import {
  ExternalLink, FileText, FolderOpen, Landmark, Loader2, Lock, RefreshCw, Search,
  ShieldCheck, Upload
} from '../components/icons'
import { api, errorMessage } from '../lib/api'
import { humanSize, visibilityLabel } from '../lib/format'
import { can } from '../lib/permissions'
import { useAuth } from '../store/auth'
import { useShell } from '../store/shell'
import type { DocumentDetail, DocumentView } from '../types'

type View = 'files' | 'finance' | 'owner' | 'public'

const VIEWS: { id: View; name: string; icon: typeof FolderOpen; permission: string }[] = [
  { id: 'files', name: '全部文件', icon: FolderOpen, permission: 'file:read' },
  { id: 'finance', name: '财务文件', icon: Landmark, permission: 'file:finance' },
  { id: 'owner', name: '仅本人', icon: Lock, permission: 'file:read' },
  { id: 'public', name: '公开知识', icon: FileText, permission: 'knowledge:public' }
]

function docColor(doc: DocumentView): number {
  if (doc.requiredPermission === 'file:finance') return 35
  if (doc.objectType === 'research') return 160
  if (doc.objectType === 'finance') return 25
  if (doc.requiredPermission === 'knowledge:hr') return 310
  if (doc.visibility === 'OWNER') return 265
  return 212
}

export function Finder() {
  const principal = useAuth((s) => s.desktop?.principal)
  const permissions = principal?.permissions ?? []
  const toast = useShell((s) => s.toast)
  const [view, setView] = useState<View>('files')
  const [documents, setDocuments] = useState<DocumentView[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<DocumentView | null>(null)
  const [detail, setDetail] = useState<DocumentDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadForm, setUploadForm] = useState({
    title: '', summary: '', visibility: 'OWNER', requiredPermission: 'knowledge:internal',
    visibleRoles: '', visibleDepts: '', file: null as File | null
  })

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const docs = await api.knowledge()
      setDocuments(docs)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])
  useEffect(() => {
    if (!selected) { setDetail(null); return }
    let alive = true
    setDetailLoading(true)
    api.document(selected.id)
      .then((data) => alive && setDetail(data))
      .catch(() => alive && setDetail(null))
      .finally(() => alive && setDetailLoading(false))
    return () => { alive = false }
  }, [selected?.id])

  const filteredDocs = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return documents.filter((doc) => {
      if (view === 'finance' && doc.requiredPermission !== 'file:finance') return false
      if (view === 'owner' && doc.visibility !== 'OWNER') return false
      if (view === 'public' && doc.visibility !== 'PUBLIC') return false
      if (!needle) return true
      return `${doc.title} ${doc.summary} ${doc.fileName}`.toLowerCase().includes(needle)
    })
  }, [documents, view, query])

  const manage = can(permissions, 'knowledge:manage')

  return (
    <div className="app finder">
      <aside className="app-side finder__side">
        <div style={{ padding: '2px 8px 8px' }}>
          <input className="input finder__search" placeholder="搜索对象…" value={query}
            onChange={(e) => setQuery(e.target.value)} style={{ margin: 0 }} />
        </div>
        <div className="app-side__label">位置</div>
        {VIEWS.filter((v) => can(permissions, v.permission)).map((item) => {
          const Icon = item.icon
          return (
            <button key={item.id} className="app-side__item" aria-current={view === item.id}
              onClick={() => { setView(item.id); setSelected(null) }}>
              <Icon /> {item.name}
              <span className="app-side__count">
                {documents.filter((doc) =>
                  item.id === 'files' ? true
                    : item.id === 'finance' ? doc.requiredPermission === 'file:finance'
                      : item.id === 'owner' ? doc.visibility === 'OWNER'
                        : doc.visibility === 'PUBLIC').length}
              </span>
            </button>
          )
        })}
        <div className="app-side__label" style={{ marginTop: 10 }}>服务端裁剪</div>
        <div className="muted" style={{ fontSize: 10.5, padding: '2px 9px', lineHeight: 1.5 }}>
          只展示你当前数据范围内可见的对象。无权限对象不会下发到前端。
        </div>
      </aside>

      <div className="app-main">
        <div className="app-toolbar">
          <span className="app-toolbar__title">文件</span>
          <span className="chip chip--info" style={{ fontSize: 10 }}>
            {filteredDocs.length} 个对象
          </span>
          <div className="spacer" />
          {manage && (
            <button className="btn btn--primary" style={{ height: 28 }} onClick={() => setUploadOpen(true)}>
              <Upload size={13} /> 上传文件
            </button>
          )}
          <button className="icon-btn" onClick={() => void load()} aria-label="刷新">
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
          </button>
        </div>

        {error && <div className="app-empty" style={{ color: 'var(--bad)' }}>{error}</div>}

        <div className="finder__main">
          <div className="finder__list">
            {loading && <div className="app-loading"><Loader2 size={15} className="spin" /> 正在读取可访问对象…</div>}
            {!loading && filteredDocs.map((doc) => (
              <button key={doc.id} className="finder__row" aria-pressed={selected?.id === doc.id}
                onClick={() => setSelected(doc)}>
                <span className="file-type" style={{
                  background: `linear-gradient(145deg, hsl(${docColor(doc)} 72% 60%), hsl(${docColor(doc) + 28} 66% 44%))`
                }}>
                  {doc.fileName.split('.').pop()?.slice(0, 3).toUpperCase() ?? 'DOC'}
                </span>
                <span className="finder__row-text">
                  <b>{doc.fileName}</b>
                  <small>{doc.title} · {doc.objectType}</small>
                </span>
                <span className="finder__row-meta">
                  <span className="muted" style={{ fontSize: 10.5 }}>{humanSize(doc.fileSize)}</span>
                  <span className={`chip ${doc.visibility === 'PUBLIC' ? 'chip--ok' : doc.visibility === 'OWNER' ? 'chip--violet' : 'chip--warn'}`}>
                    {visibilityLabel(doc.visibility)}
                  </span>
                </span>
              </button>
            ))}
            {!loading && filteredDocs.length === 0 && (
              <div className="app-empty">
                <span className="app-empty__icon"><FolderOpen size={20} /></span>
                <b>没有匹配的对象</b>
                <small>当前视图下没有你可访问的文件，或搜索词过窄。</small>
              </div>
            )}
          </div>

          {selected && (
            <aside className="finder__detail">
              <div className="preview" style={{ ['--doc-hue' as string]: docColor(selected) }}>
                <FileText size={30} />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                <span className="chip chip--info">{visibilityLabel(selected.visibility)}</span>
                <span className="chip">访问范围 v{selected.aclVersion}</span>
                {selected.requiredPermission && <span className="chip chip--violet mono">{selected.requiredPermission}</span>}
              </div>
              <h3>{selected.title}</h3>
              <p className="muted" style={{ fontSize: 11.5, lineHeight: 1.6 }}>{selected.summary}</p>
              <dl className="kv-list">
                <dt>文件</dt><dd>{selected.fileName}</dd>
                <dt>大小</dt><dd>{humanSize(selected.fileSize)}</dd>
                <dt>来源</dt><dd>{selected.objectType} · 第 {selected.page} 页第 {selected.paragraph} 段</dd>
                <dt>权限码</dt><dd className="mono">{selected.requiredPermission ?? '—'}</dd>
              </dl>
              {detailLoading && <div className="app-loading"><Loader2 size={13} className="spin" /> 读取内容…</div>}
              {detail?.content && <div className="finder__content-preview">{detail.content}</div>}
              <div style={{ display: 'flex', gap: 6, marginTop: 12, flexWrap: 'wrap' }}>
                {detail?.previewUrl ? (
                  <a className="btn btn--primary" href={detail.previewUrl} target="_blank" rel="noreferrer">
                    <ExternalLink size={13} /> 打开文件（5 分钟有效）
                  </a>
                ) : (
                  <span className="chip">文件服务未启用</span>
                )}
                <button className="btn btn--ghost" onClick={() => {
                  navigator.clipboard?.writeText(selected.id)
                  toast('文档 ID 已复制', 'ok')
                }}>复制 ID</button>
              </div>
              {manage && (
                <AclEditor doc={selected} onSaved={() => { void load(); setSelected({ ...selected, aclVersion: selected.aclVersion + 1 }) }} />
              )}
            </aside>
          )}
        </div>
      </div>

      {uploadOpen && (
        <div className="modal-backdrop" onClick={() => !uploading && setUploadOpen(false)}>
          <div className="modal glass glass-opaque" onClick={(e) => e.stopPropagation()}>
            <h3>上传文件到知识库</h3>
            <p>文件内容写入文件服务，元数据写入业务数据，并立即重建检索索引；检索与访达会马上看到它。</p>
            <div className="form-grid">
              <label>文件</label>
              <input className="input" aria-label="文件" type="file" accept=".pdf,.docx,.xlsx,.txt,.md,.csv,.json,.png,.jpg,.jpeg,.webp"
                onChange={(e) => setUploadForm({ ...uploadForm, file: e.target.files?.[0] ?? null })} />
              <label>标题</label>
              <input className="input" aria-label="标题" value={uploadForm.title} placeholder="留空则使用文件名"
                onChange={(e) => setUploadForm({ ...uploadForm, title: e.target.value })} />
              <label>摘要</label>
              <textarea className="code-input" aria-label="摘要" style={{ minHeight: 58, fontFamily: 'var(--font-sans)' }}
                value={uploadForm.summary} placeholder="一句话说明这份文档是什么"
                onChange={(e) => setUploadForm({ ...uploadForm, summary: e.target.value })} />
              <label>可见性</label>
              <select className="input" aria-label="可见性" value={uploadForm.visibility}
                onChange={(e) => setUploadForm({ ...uploadForm, visibility: e.target.value })}>
                <option value="OWNER">OWNER · 仅上传者</option>
                <option value="ROLES">ROLES · 指定角色</option>
                <option value="DEPTS">DEPTS · 指定部门</option>
                <option value="PUBLIC">PUBLIC · 公开</option>
              </select>
              {uploadForm.visibility === 'ROLES' && (
                <>
                  <label>可见角色</label>
                  <input className="input" value={uploadForm.visibleRoles} placeholder="finance,admin"
                    onChange={(e) => setUploadForm({ ...uploadForm, visibleRoles: e.target.value })} />
                </>
              )}
              {uploadForm.visibility === 'DEPTS' && (
                <>
                  <label>可见部门 ID</label>
                  <input className="input" value={uploadForm.visibleDepts} placeholder="2,5,6"
                    onChange={(e) => setUploadForm({ ...uploadForm, visibleDepts: e.target.value })} />
                </>
              )}
              <label>所需权限</label>
              <select className="input" aria-label="所需权限" value={uploadForm.requiredPermission}
                onChange={(e) => setUploadForm({ ...uploadForm, requiredPermission: e.target.value })}>
                <option value="knowledge:internal">knowledge:internal</option>
                <option value="knowledge:public">knowledge:public</option>
                <option value="knowledge:hr">knowledge:hr</option>
                <option value="file:finance">file:finance</option>
                <option value="file:read">file:read</option>
              </select>
            </div>
            <div className="flow-notice" style={{ marginTop: 12 }}>
              <ShieldCheck size={14} />
              <div>
                <b>数据管线</b>
                <div>文件服务 → 业务数据 → 检索索引 → 下一次检索立即可见</div>
              </div>
            </div>
            <div className="modal__actions">
              <button className="btn btn--ghost" disabled={uploading} onClick={() => setUploadOpen(false)}>取消</button>
              <button className="btn btn--primary" disabled={uploading || !uploadForm.file}
                onClick={() => {
                  const file = uploadForm.file
                  if (!file) return
                  setUploading(true)
                  const form = new FormData()
                  form.append('file', file)
                  form.append('title', uploadForm.title)
                  form.append('summary', uploadForm.summary)
                  form.append('visibility', uploadForm.visibility)
                  form.append('visibleRoles', uploadForm.visibleRoles)
                  form.append('visibleDepts', uploadForm.visibleDepts)
                  form.append('requiredPermission', uploadForm.requiredPermission)
                  api.uploadKnowledge(form)
                    .then((result) => {
                      toast(result.message, 'ok')
                      setUploadOpen(false)
                      setUploadForm({ title: '', summary: '', visibility: 'OWNER', requiredPermission: 'knowledge:internal', visibleRoles: '', visibleDepts: '', file: null })
                      setView('files')
                      setSelected(result.document)
                      void load()
                    })
                    .catch((err) => toast(errorMessage(err), 'bad'))
                    .finally(() => setUploading(false))
                }}>
                {uploading ? <Loader2 size={13} className="spin" /> : <Upload size={13} />}
                {uploading ? '上传并索引…' : '上传并重建索引'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function AclEditor({ doc, onSaved }: { doc: DocumentView; onSaved: () => void }) {
  const toast = useShell((s) => s.toast)
  const [visibility, setVisibility] = useState(doc.visibility)
  const [permission, setPermission] = useState(doc.requiredPermission ?? 'knowledge:public')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    setSaving(true)
    try {
      const result = await api.updateAcl(doc.id, {
        visibility,
        visibleRoles: visibility === 'ROLES' ? ['finance', 'admin'] : [],
        visibleDepts: visibility === 'DEPTS' ? [3] : [],
        requiredPermission: permission
      })
      toast(`访问范围已更新至 v${result.aclVersion}，下一次检索立即生效`, 'ok')
      onSaved()
    } catch (error) {
      toast(errorMessage(error), 'bad')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid var(--line-soft)' }}>
      <div className="eyebrow" style={{ marginBottom: 7 }}><ShieldCheck size={11} style={{ verticalAlign: -1 }} /> 访问范围管理</div>
      <div style={{ display: 'flex', gap: 6 }}>
        <select className="input" value={visibility} onChange={(e) => setVisibility(e.target.value as DocumentView['visibility'])}>
          <option value="PUBLIC">PUBLIC</option>
          <option value="ROLES">ROLES</option>
          <option value="DEPTS">DEPTS</option>
          <option value="OWNER">OWNER</option>
        </select>
        <select className="input" value={permission} onChange={(e) => setPermission(e.target.value)}>
          <option value="knowledge:public">knowledge:public</option>
          <option value="knowledge:internal">knowledge:internal</option>
          <option value="knowledge:hr">knowledge:hr</option>
          <option value="file:finance">file:finance</option>
          <option value="file:read">file:read</option>
        </select>
      </div>
      <button className="btn btn--ghost" style={{ width: '100%', marginTop: 6 }} onClick={() => void save()} disabled={saving}>
        {saving ? <Loader2 size={12} className="spin" /> : <ShieldCheck size={12} />} 保存并同步检索索引
      </button>
    </div>
  )
}
