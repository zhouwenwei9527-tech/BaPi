import { useState } from 'react'
import { Droplet, Gauge, History, Moon, RotateCcw, Sun, Upload } from '../components/icons'
import { WALLPAPER_PRESETS } from '../lib/wallpaper'
import { useShell } from '../store/shell'

export function GlassSettings() {
  const theme = useShell((s) => s.theme)
  const setTheme = useShell((s) => s.setTheme)
  const mode = useShell((s) => s.glassMode)
  const setMode = useShell((s) => s.setGlassMode)
  const glass = useShell((s) => s.glass)
  const setToken = useShell((s) => s.setGlassToken)
  const reset = useShell((s) => s.resetGlass)
  const fps = useShell((s) => s.fps)
  const wallpaper = useShell((s) => s.wallpaper)
  const wallpaperHistory = useShell((s) => s.wallpaperHistory)
  const setWallpaperPreset = useShell((s) => s.setWallpaperPreset)
  const setCustomWallpaper = useShell((s) => s.setCustomWallpaper)
  const applyWallpaperHistory = useShell((s) => s.applyWallpaperHistory)
  const clearWallpaper = useShell((s) => s.clearWallpaper)
  const toast = useShell((s) => s.toast)
  const [uploading, setUploading] = useState(false)

  const sliders = [
    ['blur', '模糊', 8, 60, 'px'],
    ['saturation', '饱和度', 100, 260, '%'],
    ['opacity', '不透明度', 20, 92, '%'],
    ['specular', '高光强度', 0, 80, '%'],
    ['border', '边框不透明度', 0, 60, '%']
  ] as const

  return (
    <div className="glass-app">
      <div className="glass-controls">
        <h3>壁纸</h3>
        <p>壁纸跟随账号同步：上传图片后重新登录或换设备会自动恢复，无需审批；最近 3 张可随时切回。</p>
        <div className="wallpaper-picker">
          {WALLPAPER_PRESETS.map((preset) => (
            <button key={preset.id} className="wallpaper-swatch" data-preset={preset.id}
              data-active={wallpaper.kind === 'preset' && wallpaper.presetId === preset.id}
              onClick={() => void setWallpaperPreset(preset.id, preset.name)}>
              <span>{preset.name}</span>
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
          <label className="btn btn--ghost" style={{ flex: 1, cursor: 'pointer' }}>
            {uploading ? '上传中…' : <><Upload size={12} /> 上传壁纸</>}
            <input type="file" accept="image/*" hidden disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (!file) return
                setUploading(true)
                setCustomWallpaper(file)
                  .then(() => toast('壁纸已更换，并同步到你的账号', 'ok'))
                  .catch(() => {
                    // The store already surfaced the upload error.
                  })
                  .finally(() => setUploading(false))
              }} />
          </label>
          <button className="btn btn--ghost" disabled={uploading}
            onClick={() => void clearWallpaper()} title="恢复极光壁纸">
            <RotateCcw size={12} /> 默认
          </button>
        </div>
        <div className="muted" style={{ fontSize: 10, marginTop: 5 }}>
          当前：{wallpaper.name}{wallpaper.kind === 'image' ? '（自定义图片）' : ''}
          {wallpaper.source === 'server' ? ' · 已同步到账号' : ' · 本机缓存，登录后同步'}
        </div>

        <div className="wallpaper-history">
          <div className="wallpaper-history__title">
            <History size={11} /> 最近使用
            <span className="muted">最多保留 3 张</span>
          </div>
          {wallpaperHistory.length === 0 ? (
            <div className="muted" style={{ fontSize: 10, marginTop: 4 }}>
              更换壁纸后，最近 3 张会出现在这里，可一键切回。
            </div>
          ) : (
            <div className="wallpaper-history__grid">
              {wallpaperHistory.map((entry) => (
                <button key={entry.id}
                  className="wallpaper-swatch wallpaper-history__item"
                  data-preset={entry.kind === 'PRESET' ? entry.presetId : undefined}
                  style={entry.kind === 'IMAGE' && entry.url
                    ? { backgroundImage: `url(${entry.url})` } : undefined}
                  onClick={() => void applyWallpaperHistory(entry.id)}
                  title={`切换到 ${entry.name}`}>
                  <span>{entry.name}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="divider" style={{ margin: '14px 0' }} />
        <h3><Droplet size={14} style={{ verticalAlign: -2 }} /> 液态玻璃</h3>
        <p>基础档使用 blur + saturate + 半透明 + 内阴影高光，所有浏览器可用。增强档增加折射高光，最多 3 个 hero surface。</p>

        <div className="segmented" style={{ width: '100%', marginBottom: 13 }}>
          <button style={{ flex: 1 }} aria-pressed={mode === 'base'} onClick={() => setMode('base')}>基础档</button>
          <button style={{ flex: 1 }} aria-pressed={mode === 'enhanced'} onClick={() => setMode('enhanced')}>增强档</button>
        </div>

        {sliders.map(([key, label, min, max, unit]) => (
          <label className="slider-row" key={key}>
            <span>{label}</span>
            <input type="range" min={min} max={max} value={glass[key]}
              onChange={(e) => setToken(key, Number(e.target.value))} />
            <span>{glass[key]}{unit}</span>
          </label>
        ))}

        <div className="cc-theme" style={{ marginTop: 12 }}>
          <button aria-pressed={theme === 'dark'} onClick={() => setTheme('dark')}><Moon size={12} /> 深色</button>
          <button aria-pressed={theme === 'light'} onClick={() => setTheme('light')}><Sun size={12} /> 浅色</button>
        </div>

        <button className="btn btn--ghost" style={{ width: '100%' }} onClick={reset}>
          <RotateCcw size={12} /> 恢复默认外观
        </button>
      </div>

      <div className="glass-preview">
        <div className="glass-preview__hero glass glass--enhanced">
          <span className="chip chip--info" style={{ alignSelf: 'flex-start', marginBottom: 6 }}>
            Hero surface · 1 / 3
          </span>
          <h2>折射、层次、克制</h2>
          <p>玻璃是信息层次的一部分，不是装饰。文字对比度在深浅主题下均达到 WCAG AA。</p>
        </div>
        <div className="glass-preview__row">
          <div className="glass-preview__card glass">
            <div className="eyebrow">基础档</div>
            <b style={{ display: 'block', marginTop: 5 }}>blur + saturate</b>
            <div className="muted" style={{ fontSize: 10.5, marginTop: 3 }}>全浏览器降级安全</div>
          </div>
          <div className="glass-preview__card glass glass--enhanced">
            <div className="eyebrow">增强档</div>
            <b style={{ display: 'block', marginTop: 5 }}>specular + refraction</b>
            <div className="muted" style={{ fontSize: 10.5, marginTop: 3 }}>仅 hero surface</div>
          </div>
        </div>
        <div className="glass-preview__card glass" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Gauge size={18} style={{ color: fps < 40 ? 'var(--warn)' : 'var(--ok)' }} />
          <div>
            <b style={{ fontSize: 12 }}>FPSGuard · {fps}fps</b>
            <div className="muted" style={{ fontSize: 10.5 }}>
              3 秒滑动平均低于 40fps 时自动降级为基础档，恢复需要手动。
            </div>
          </div>
          <span className="chip chip--ok" style={{ marginLeft: 'auto' }}>运行中</span>
        </div>
      </div>
    </div>
  )
}
