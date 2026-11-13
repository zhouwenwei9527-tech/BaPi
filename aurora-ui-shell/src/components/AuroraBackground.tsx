import { useShell } from '../store/shell'

export function AuroraBackground() {
  const wallpaper = useShell((s) => s.wallpaper)
  return (
    <div className="aurora-bg" aria-hidden="true">
      {wallpaper.kind === 'image' && (wallpaper.url || wallpaper.dataUrl) ? (
        <div className="wallpaper-custom"
          style={{ backgroundImage: `url(${wallpaper.url ?? wallpaper.dataUrl})` }} />
      ) : (
        <>
          <div className="aurora-bg__blob aurora-bg__blob--a" />
          <div className="aurora-bg__blob aurora-bg__blob--b" />
          <div className="aurora-bg__blob aurora-bg__blob--c" />
        </>
      )}
    </div>
  )
}
