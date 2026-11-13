import { useEffect } from 'react'
import { useShell } from '../store/shell'

/**
 * 3-second sliding average frame rate. Below 40fps the shell downgrades the
 * enhanced glass tier and tells the user; recovery is manual (PRD §7.4).
 */
export function useFpsGuard() {
  useEffect(() => {
    // Headless automation does not drive the compositor at display refresh;
    // measuring it would produce a meaningless FPS and a false downgrade.
    if (typeof navigator !== 'undefined' && navigator.webdriver) return
    let frames = 0
    let windowStart = performance.now()
    let warmupWindows = 0
    let raf = 0
    const tick = (now: number) => {
      frames++
      const elapsed = now - windowStart
      if (elapsed >= 3000) {
        // Ignore the first two windows: initial hydration and tab restore are noisy.
        if (warmupWindows >= 2 && document.visibilityState === 'visible') {
          const fps = (frames * 1000) / elapsed
          useShell.getState().setFps(fps)
        }
        warmupWindows++
        frames = 0
        windowStart = now
      }
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [])
}
