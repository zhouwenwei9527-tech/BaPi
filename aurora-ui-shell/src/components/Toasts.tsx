import { CheckCircle2, Info, XCircle } from './icons'
import { useShell } from '../store/shell'

export function Toasts() {
  const toasts = useShell((s) => s.toasts)
  const dismiss = useShell((s) => s.dismissToast)
  if (!toasts.length) return null
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <button
          key={toast.id}
          className={`toast glass glass-opaque toast--${toast.tone}`}
          onClick={() => dismiss(toast.id)}
        >
          {toast.tone === 'ok' ? <CheckCircle2 size={15} />
            : toast.tone === 'bad' ? <XCircle size={15} /> : <Info size={15} />}
          {toast.message}
        </button>
      ))}
    </div>
  )
}
