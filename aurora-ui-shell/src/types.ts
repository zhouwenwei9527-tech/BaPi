export interface DataScope {
  type: 'ALL' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM'
  deptIds: number[]
  all?: boolean
  selfOnly?: boolean
}

export interface Principal {
  id: number
  username: string
  displayName: string
  title: string
  roles: string[]
  permissions: string[]
  dataScope: DataScope
  avatarHue: number
}

export interface RoleView {
  id: number
  code: string
  name: string
  description: string
  scopeType: string
}

export interface SessionView {
  principal: Principal
  availableRoles: RoleView[]
  expiresAt: string
  activeRole: string
}

export interface WallpaperView {
  kind: 'PRESET' | 'IMAGE'
  presetId?: string
  name: string
  url?: string
  provider: string
  storedAt?: string
  storageAvailable: boolean
}

export interface WallpaperHistoryView {
  id: number
  kind: 'PRESET' | 'IMAGE'
  presetId?: string
  name: string
  url?: string
  storedAt?: string
}

export interface DockView {
  apps: string[]
}

export interface MenuItem {
  code: string
  name: string
  icon: string
  children: MenuItem[]
}

export interface WidgetMount {
  mountPoint: string
  dataContract: string
  requiredPermission: string
  render: Record<string, unknown>
}

export interface Consistency {
  documents: number
  vectorChunks: number
  deviation: number
  alert: boolean
  message: string
}

export interface DesktopStats {
  pendingApprovals: number
  tools: number
  auditEvents: number
  knowledgeChunks: number
  documents: number
  activePlugins: number
  plugins: number
}

export interface DesktopData {
  principal: Principal
  activeRole: string
  roles: RoleView[]
  menu: MenuItem[]
  mounts: WidgetMount[]
  stats: DesktopStats
  consistency: Consistency
  version: string
}

export type ResultKind = 'ACTION' | 'ENTITY' | 'KNOWLEDGE'

export interface SourceRef {
  objectType: string
  objectId: string
  page?: number
  paragraph?: number
}

export interface UnifiedResult {
  type: ResultKind
  title: string
  subtitle?: string
  score: number
  requiredPermission?: string
  sourceRef?: SourceRef
  appId?: string
  data?: Record<string, unknown>
}

export interface SearchResult {
  results: UnifiedResult[]
  trimmed: number
  imageQuery: boolean
  embeddingProvider: string
  vectorChunks: number
  documents: number
  consistency: Consistency
}

export type SideEffect = 'READ' | 'WRITE' | 'DESTRUCTIVE'
export type ApprovalPolicy = 'AUTO' | 'ASK' | 'DENY_BY_DEFAULT'

export interface ToolView {
  name: string
  version: string
  description: string
  sideEffect: SideEffect
  approvalPolicy: ApprovalPolicy
  requiredPermissions: string[]
  source: string
  active: boolean
  idempotent: boolean
  compensation: string
  inputSchema?: Record<string, unknown>
}

export interface PipelineStep {
  stage: string
  status: 'ok' | 'warn' | 'bad'
  detail: string
}

export interface DecisionOutcome {
  approval: ApprovalView
  execution?: GatewayOutcome
  errorCode?: string
  error?: string
}

export interface GatewayOutcome {
  status: 'EXECUTED' | 'PENDING' | 'CONFIRMATION_REQUIRED' | 'DENIED' | 'FAILED'
  errorCode?: string
  message: string
  data?: unknown
  summary?: string
  affectedCount: number
  approvalId?: string
  traceId: string
  sessionId: string
  auditSeq?: number
  latencyMs: number
  steps: PipelineStep[]
  confirmation?: {
    intent: string
    operability: number
    riskLevel: string
    riskReasons: string[]
    requiresConfirmation: boolean
  }
}

export interface ApprovalView {
  id: string
  toolName: string
  toolVersion: string
  sideEffect: SideEffect
  argsSummary: string
  intentSummary: string
  operability: number
  riskLevel: string
  riskReasons: string
  requiresConfirmation: boolean
  argsHash: string
  affectedObjects?: string
  affectedCount: number
  reversible: string
  requesterUserId: number
  requesterName: string
  actorType: string
  agentId?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED' | 'EXECUTED' | 'FAILED'
  createdAt: string
  expiresAt: string
  decidedBy?: string
  decidedAt?: string
  executedAt?: string
  resultSummary?: string
  errorCode?: string
  note?: string
  sessionId?: string
  traceId?: string
  expired?: boolean
}

export interface AuditEvent {
  seq: number
  eventId: string
  ts: string
  traceId?: string
  sessionId?: string
  type: string
  actorType: string
  agentId?: string
  userId?: number
  username?: string
  roles: string[]
  toolName?: string
  argsDigest?: string
  decision?: string
  approvalId?: string
  latencyMs?: number
  status: string
  prevHash?: string
  hash: string
}

export interface ChainVerification {
  valid: boolean
  checked: number
  brokenAt?: number
  message: string
}

export interface TraceStep {
  seq: number
  traceId: string
  ts: string
  stage: string
  status: string
  decision?: string
  detail?: Record<string, unknown>
}

export interface TraceSummary {
  traceId: string
  startedAt: string
  endedAt: string
  steps: number
  sampleDetail?: string
}

export interface ReplayView {
  traceId: string
  trace: TraceStep[]
  audit: AuditEvent[]
  modelCalls: { seq: number; provider: string; createdAt: string }[]
  toolSequence: string[]
}

export interface PluginView {
  pluginId: string
  name: string
  version: string
  status: 'INSTALLED' | 'PENDING_AUTH' | 'ACTIVE' | 'DISABLED'
  reason: string
  description: string
  vendor: string
  tools: string[]
  mounts: WidgetMount[]
  minFrameworkVersion: string
}

export interface DepartmentOption {
  id: number
  name: string
  parentId?: number
}

export interface RoleOption {
  code: string
  name: string
  description: string
  grantable: boolean
}

export interface UserPage {
  items: UserView[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface UserMeta {
  departments: DepartmentOption[]
  roles: RoleOption[]
  scopeType: string
}

export interface UserView {
  id: number
  username: string
  displayName: string
  title: string
  deptId: number
  deptName: string
  status: string
  roles: string[]
  avatarHue: number
}

export interface DocumentView {
  id: string
  title: string
  summary: string
  objectType: string
  objectId: string
  visibility: 'PUBLIC' | 'ROLES' | 'DEPTS' | 'OWNER'
  aclVersion: number
  page: number
  paragraph: number
  requiredPermission?: string
  fileName: string
  fileSize: number
  mimeType: string
  createdAt: string
}

export interface DocumentDetail {
  document: DocumentView
  content: string
  previewUrl?: string
  storageProvider: string
}

export interface AgentStep {
  label: string
  status: 'ok' | 'warn' | 'bad'
  detail: string
}

export interface AgentReply {
  sessionId: string
  traceId: string
  message: string
  status: 'OK' | 'PENDING' | 'CONFIRMATION_REQUIRED' | 'DENIED' | 'FAILED' | 'BUDGET_EXCEEDED' | 'CIRCUIT_OPEN'
  approvalId?: string
  errorCode?: string
  steps: AgentStep[]
  toolCalls: number
  budget: {
    toolCalls: number
    maxToolCalls: number
    tokens: number
    maxTokens: number
    wallClockMs: number
    maxWallClockMs: number
    consecutiveFailures: number
    maxConsecutiveFailures: number
    exhausted: boolean
  }
  suggestions: string[]
  confirmation?: {
    toolName: string
    intent: string
    operability: number
    riskLevel: string
    riskReasons: string[]
  }
}

export interface UploadResult {
  document: DocumentView
  indexed: boolean
  storageProvider: string
  message: string
}

export interface AgentSession {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messages: number
}

export interface AgentMessage {
  id: number
  role: string
  content: string
  toolCallsJson?: string
  createdAt: string
}

export interface RoleAdminView {
  id: number
  code: string
  name: string
  description: string
  scopeType: string
  permissions: string[]
  builtin: boolean
}

export interface ResourceAdminView {
  id: number
  code: string
  name: string
  type: string
  parentCode?: string
  sortOrder: number
  icon: string
  description: string
}

export interface MenuNode {
  code: string
  name: string
  icon: string
  sortOrder: number
  parentCode?: string
  children: MenuNode[]
}

export interface PermissionRoleOption {
  code: string
  name: string
  description: string
  alreadyAssigned: boolean
}

export interface PermissionRequestView {
  id: string
  requesterUserId: number
  requesterName: string
  roleCode: string
  roleName: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'FAILED'
  decidedBy?: string
  decidedAt?: string
  note?: string
  createdAt: string
}

export interface MatrixView {
  roles: { id: number; code: string; name: string; description: string; scopeType: string }[]
  rows: { code: string; name: string; type: string; roles: string[] }[]
}

export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: { code: string; message: string }
}
