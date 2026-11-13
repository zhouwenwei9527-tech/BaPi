import { useEffect, useMemo, useRef, useState } from 'react'
import {
  BookOpen, Boxes, Command, FileText, ImagePlus, Loader2, Search, Sparkles,
  UserRound, X, Zap
} from './icons'
import { appDef, type AppId } from '../data/apps'
import { AppGlyph } from './AppGlyph'
import { api, errorMessage } from '../lib/api'
import { useShell } from '../store/shell'
import type { SearchResult, UnifiedResult } from '../types'

const TYPE_META = {
  ACTION: { label: '操作', icon: Zap },
  ENTITY: { label: '实体', icon: Boxes },
  KNOWLEDGE: { label: '知识', icon: BookOpen }
} as const

function ResultIcon({ result }: { result: UnifiedResult }) {
  if (result.type === 'ACTION' && result.appId) {
    const app = appDef(result.appId)
    if (app) {
      return <span className="palette__icon palette__icon--app"><AppGlyph app={app} size={26} /></span>
    }
  }
  if (result.type === 'KNOWLEDGE') {
    return <span className="palette__icon" style={{ color: 'var(--ok)' }}><BookOpen size={16} /></span>
  }
  if (result.sourceRef?.objectType === 'user' || result.subtitle?.startsWith('用户')) {
    return <span className="palette__icon" style={{ color: 'var(--warn)' }}><UserRound size={16} /></span>
  }
  if (result.sourceRef?.objectType === 'file' || result.sourceRef?.objectType === 'finance') {
    return <span className="palette__icon" style={{ color: 'var(--accent)' }}><FileText size={16} /></span>
  }
  const Meta = TYPE_META[result.type]
  return <span className="palette__icon"><Meta.icon size={16} /></span>
}

export function CommandPalette() {
  const open = useShell((s) => s.paletteOpen)
  const setOpen = useShell((s) => s.setPaletteOpen)
  const openApp = useShell((s) => s.openAppGuarded)
  const toast = useShell((s) => s.toast)
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<SearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [active, setActive] = useState(0)
  const [image, setImage] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return
    setActive(0)
    const timer = setTimeout(() => inputRef.current?.focus(), 30)
    return () => clearTimeout(timer)
  }, [open])

  useEffect(() => {
    if (!open) return
    if (image) return
    let alive = true
    setLoading(true)
    setError(null)
    const timer = setTimeout(() => {
      api.search(query, 9)
        .then((data) => {
          if (!alive) return
          setResult(data)
          setActive(0)
        })
        .catch((err) => alive && setError(errorMessage(err)))
        .finally(() => alive && setLoading(false))
    }, query ? 170 : 0)
    return () => { alive = false; clearTimeout(timer) }
  }, [query, open, image])

  useEffect(() => {
    if (!image) { setPreview(null); return }
    const url = URL.createObjectURL(image)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [image])

  const results = result?.results ?? []
  const selected = results[active]

  const runImageSearch = async () => {
    if (!image) return
    setLoading(true)
    setError(null)
    try {
      const data = await api.imageSearch(image, query, '', 9)
      setResult(data)
      setActive(0)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const choose = (item?: UnifiedResult) => {
    const target = item ?? selected
    if (!target) return
    setOpen(false)
    if (target.type === 'ACTION' && target.appId) {
      openApp(target.appId as AppId)
      return
    }
    if (target.type === 'KNOWLEDGE') {
      openApp('finder')
      toast(`已打开访达 · ${target.title}`, 'info')
      return
    }
    if (target.type === 'ENTITY') {
      openApp('finder')
      toast(`已打开访达 · ${target.title}`, 'info')
      return
    }
    if (target.appId) openApp(target.appId as AppId)
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      setOpen(false)
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((i) => Math.min(i + 1, Math.max(0, results.length - 1)))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => Math.max(0, i - 1))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      if (image) void runImageSearch()
      else choose()
    }
  }

  const grouped = useMemo(() => ({
    actions: results.filter((r) => r.type === 'ACTION').length,
    entities: results.filter((r) => r.type === 'ENTITY').length,
    knowledge: results.filter((r) => r.type === 'KNOWLEDGE').length
  }), [results])

  if (!open) return null

  return (
    <div className="palette-backdrop" onPointerDown={(e) => { if (e.target === e.currentTarget) setOpen(false) }}>
      <div className="palette glass glass-opaque" role="dialog" aria-label="命令面板" onKeyDown={onKeyDown}>
        <div className="palette__head">
          {image ? <ImagePlus size={19} style={{ color: 'var(--violet)' }} /> : <Search size={19} style={{ color: 'var(--muted)' }} />}
          <input
            ref={inputRef}
            className="palette__input"
            placeholder={image ? '补充文字描述，然后按 Enter 检索…' : '搜索操作、实体、知识…'}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="搜索"
            spellCheck={false}
          />
          {loading && <Loader2 size={17} className="spin" style={{ color: 'var(--muted)' }} />}
          {image ? (
            <button className="icon-btn" onClick={() => setImage(null)} aria-label="移除图片"><X size={15} /></button>
          ) : (
            <button className="icon-btn" onClick={() => fileRef.current?.click()} aria-label="图片检索" title="图片检索">
              <ImagePlus size={16} />
            </button>
          )}
          <span className="kbd">Esc</span>
        </div>

        {preview && (
          <div className="palette__image-preview">
            <img src={preview} alt="待检索图片" />
            <div style={{ flex: 1 }}>
              <b style={{ fontSize: 11.5 }}>{image?.name}</b>
              <div className="muted" style={{ fontSize: 10.5 }}>
                图片与文字会共同编码；Enter 开始多模态检索
              </div>
            </div>
            <span className="chip chip--violet">IMAGE</span>
          </div>
        )}

        <div className="palette__results">
          {loading && !results.length && (
            <div className="palette__empty"><Loader2 size={18} className="spin" style={{ marginBottom: 6 }} /><div>正在检索可访问的对象…</div></div>
          )}
          {error && (
            <div className="palette__empty" style={{ color: 'var(--bad)' }}>
              {error}
              <div style={{ marginTop: 8 }}>
                <button className="btn btn--ghost" onClick={() => setQuery((q) => q + ' ')}>重试</button>
              </div>
            </div>
          )}
          {!loading && !error && results.length === 0 && (
            <div className="palette__empty">
              <Sparkles size={20} style={{ marginBottom: 8, opacity: 0.6 }} />
              <div>没有可访问的结果</div>
              <small className="muted">权限过滤在检索阶段完成，不会先召回再隐藏。</small>
            </div>
          )}
          {results.map((item, index) => {
            const meta = TYPE_META[item.type]
            return (
              <button
                key={`${item.type}-${item.title}-${index}`}
                className="palette__row"
                data-active={index === active}
                onPointerEnter={() => setActive(index)}
                onClick={() => choose(item)}
              >
                <ResultIcon result={item} />
                <span className="palette__text">
                  <b>{item.title}</b>
                  <small>{item.subtitle}</small>
                </span>
                <span className="palette__type">{meta.label}</span>
                <span className="palette__score">{item.score.toFixed(2)}</span>
              </button>
            )
          })}
        </div>

        <div className="palette__footer">
          <span><span className="kbd">↑</span> <span className="kbd">↓</span> 导航</span>
          <span><span className="kbd">↵</span> 打开</span>
          <span className="palette__hint"><Command size={11} /> 权限守卫与桌面、菜单是同一个函数</span>
          <span className="palette__upload">
            {result
              ? `${grouped.actions} 操作 · ${grouped.entities} 实体 · ${grouped.knowledge} 知识 · 裁剪 ${result.trimmed} 条`
              : '统一检索内核'}
          </span>
        </div>
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) {
              setImage(file)
              setResult(null)
            }
            e.target.value = ''
          }}
        />
      </div>
    </div>
  )
}
