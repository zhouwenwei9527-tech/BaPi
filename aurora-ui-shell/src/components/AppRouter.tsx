import type { AppId } from '../data/apps'
import { Finder } from '../apps/Finder'
import { Directory } from '../apps/Directory'
import { Guide } from '../apps/Guide'
import { Access } from '../apps/Access'
import { Agent } from '../apps/Agent'
import { Approvals } from '../apps/Approvals'
import { Audit } from '../apps/Audit'
import { Tools } from '../apps/Tools'
import { Plugins } from '../apps/Plugins'
import { Rbac } from '../apps/Rbac'
import { GlassSettings } from '../apps/GlassSettings'

export function AppRouter({ appId }: { appId: AppId }) {
  switch (appId) {
    case 'finder': return <Finder />
    case 'directory': return <Directory />
    case 'agent': return <Agent />
    case 'approvals': return <Approvals />
    case 'audit': return <Audit />
    case 'tools': return <Tools />
    case 'plugins': return <Plugins />
    case 'rbac': return <Rbac />
    case 'guide': return <Guide />
    case 'access': return <Access />
    case 'glass': return <GlassSettings />
    default: return null
  }
}
