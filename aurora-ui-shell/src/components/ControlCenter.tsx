import { Gauge, Monitor, Moon, RotateCcw, Sun } from './icons'
import { useShell } from '../store/shell'

export function ControlCenter() {
  const theme = useShell((s) => s.theme)
  const setTheme = useShell((s) => s.setTheme)
  const mode = useShell((s) => s.glassMode)
  const setMode = useShell((s) => s.setGlassMode)
  const glass = useShell((s) => s.glass)
  const setToken = useShell((s) => s.setGlassToken)
  const reset = useShell((s) => s.resetGlass)
  const fps = useShell((s) => s.fps)
  const openApp = useShell((s) => s.openAppGuarded)
  const fullscreen = () => {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => undefined)
    else document.documentElement.requestFullscreen?.().catch(() => undefined)
  }

  return (
    <div className="control-center glass-opaque" role="dialog" aria-label="控制中心">
      <div className="cc-theme">
        <button aria-pressed={theme === 'dark'} onClick={() => setTheme('dark')}><Moon size={12} style={{ verticalAlign: -2 }} /> 深色</button>
        <button aria-pressed={theme === 'light'} onClick={() => setTheme('light')}><Sun size={12} style={{ verticalAlign: -2 }} /> 浅色</button>
      </div>
      <div className="control-center__grid">
        <button className="cc-tile" onClick={fullscreen}>
          <span className="cc-tile__icon"><Monitor size={15} /></span>
          <span><b>全屏</b><small>Fullscreen API</small></span>
        </button>
        <button className="cc-tile" onClick={() => openApp('glass')}>
          <span className="cc-tile__icon"><Gauge size={15} /></span>
          <span><b>玻璃档位</b><small>{mode === 'enhanced' ? '增强档' : '基础档'} · {fps}fps</small></span>
        </button>
      </div>
      <div className="segmented" style={{ width: '100%', marginBottom: 12 }}>
        <button style={{ flex: 1 }} aria-pressed={mode === 'base'} onClick={() => setMode('base')}>基础档</button>
        <button style={{ flex: 1 }} aria-pressed={mode === 'enhanced'} onClick={() => setMode('enhanced')}>增强档 · 折射高光</button>
      </div>
      {([
        ['blur', '模糊', 8, 60, 'px'],
        ['saturation', '饱和度', 100, 260, '%'],
        ['opacity', '不透明度', 20, 92, '%']
      ] as const).map(([key, label, min, max, unit]) => (
        <label className="cc-slider" key={key}>
          <span>{label}</span>
          <input type="range" min={min} max={max} value={glass[key]}
            onChange={(e) => setToken(key, Number(e.target.value))} />
          <span>{glass[key]}{unit}</span>
        </label>
      ))}
      <div className="cc-foot">
        <span><i className={`fps-dot ${fps < 40 ? 'fps-dot--warn' : ''}`} />FPSGuard {fps}fps · 3s 均值</span>
        <button className="btn btn--ghost" style={{ height: 24, padding: '0 8px', fontSize: 10.5 }} onClick={reset}>
          <RotateCcw size={11} /> 默认
        </button>
      </div>
    </div>
  )
}
