import { useEffect } from 'react'
import { useShell } from '../store/shell'

const LIMIT = 3

/**
 * AC-18: the enhanced (refraction) glass tier is reserved for at most three
 * hero surfaces. We never silently downgrade all glass; we warn loudly.
 */
export function useEnhancedSurfaceGuard() {
  useEffect(() => {
    let lastReported = 0
    const check = () => {
      const count = document.querySelectorAll('.glass--enhanced').length
      if (count > LIMIT && count !== lastReported) {
        lastReported = count
        useShell.getState().toast(
          `增强档玻璃 surface 数量为 ${count}，超过上限 ${LIMIT}：请降低 hero surface 数量`, 'bad')
        console.warn(`[aurora] enhanced glass surfaces: ${count} (limit ${LIMIT})`)
      }
    }
    const observer = new MutationObserver(check)
    observer.observe(document.body, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['class']
    })
    const timer = setTimeout(check, 800)
    return () => { observer.disconnect(); clearTimeout(timer) }
  }, [])
}
