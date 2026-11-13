import { useShell } from '../store/shell'
import { Window } from './Window'

export function WindowLayer() {
  const windows = useShell((s) => s.windows)
  return (
    <div className="window-layer">
      {windows.map((window) => <Window key={window.id} win={window} />)}
    </div>
  )
}
