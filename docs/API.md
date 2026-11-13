# API 概览

所有响应使用统一信封：

```json
{ "success": true, "data": {}, "error": null }
```

认证：`Authorization: Bearer <token>`。错误码在 `error.code`，HTTP 状态见
`ApiExceptionHandler`。

## 认证 / 桌面

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | public | `{username,password}` |
| GET | `/api/auth/me` | token | 当前会话与可切换角色 |
| POST | `/api/auth/switch-role` | token | `{roleCode}`，写审计 |
| POST | `/api/auth/logout` | token | 吊销会话 |
| GET | `/api/desktop` | token | principal / menu / stats / mounts / consistency |
| GET | `/api/health` | public | 版本、向量库、存储状态 |

## 检索

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/search?q=&limit=` | 三类结果，ACL 已在检索阶段过滤 |
| POST | `/api/search/image` | multipart `file` + `caption` + `q` |
| GET | `/api/knowledge` | 当前可见文档 |
| GET | `/api/knowledge/{id}` | 详情、内容摘要、5 分钟预签名 URL |
| PATCH | `/api/knowledge/{id}/acl` | 需 `knowledge:manage`，同步重写向量 payload |
| POST | `/api/knowledge/reindex` | 全量重建向量 |
| GET | `/api/users?q=` | 数据范围内的用户（管理者包含停用） |
| GET | `/api/users?page=&size=` | 服务端分页；管理者包含停用用户 |
| GET | `/api/users/meta` | 可创建部门 + 可授予角色（grantable） |
| PATCH | `/api/users/me/profile` | 本人姓名/职位直接生效，无需审批 |
| POST | `/api/knowledge/upload` | multipart：file/title/summary/visibility/requiredPermission → MinIO + MySQL + pgvector |
| GET/PUT/POST/DELETE | `/api/preferences/wallpaper` | 壁纸偏好；预设写 MySQL，上传图片写 MinIO，返回 7 天预签名 URL |

## Tool / 审批

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/tools` | 需 `tool:read`；含 inputSchema |
| POST | `/api/tools/{name}/invoke` | `{args,sessionId,idempotencyKey?,confirm?}`，经 Gateway；权限内写操作可 `confirm=true` 本人确认后执行，`user.assignRole` 仍生成双人复核审批 |
| GET | `/api/approvals?status=` | 需 `approval:read` |
| POST | `/api/approvals/{id}/decide` | `{approve,note,confirmRisk}`，需 `approval:decide`；低可操作度必须二次确认 |
| POST | `/api/approvals/batch-decide` | `{ids,approve,note}` |

## 审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/audit?limit=` | 最近事件 |
| GET | `/api/audit/verify` | 全量哈希链校验 |
| GET | `/api/audit/trace/{traceId}` | 轨迹步骤 |
| GET | `/api/audit/replay/{traceId}` | 轨迹 + 审核事件 + 模型调用摘要 |
| GET | `/api/audit/traces` | 最近轨迹 |

## 插件 / RBAC / Agent

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/plugins` | 插件列表 |
| POST | `/api/plugins/{id}/activate` / `disable` | 需 `plugin:manage` |
| DELETE | `/api/plugins/{id}` | 卸载并移除 Tool/挂载点 |
| POST | `/api/plugins/demo/unknown-permission` | 预期返回 `PLUGIN_UNKNOWN_PERMISSION` |
| POST | `/api/plugins/demo/incompatible` | 安装后保持 `DISABLED` |
| GET | `/api/rbac/matrix` | 需 `rbac:read` |
| GET/POST/PATCH | `/api/rbac/admin/roles` | 需 `rbac:manage`：角色、数据范围、授权 |
| POST | `/api/rbac/admin/roles/{id}/permissions/{code}/toggle` | 超级管理员逐项授权 |
| GET/POST/PATCH/DELETE | `/api/rbac/admin/menu` | 菜单改名/换父级/排序/新建组 |
| GET/POST | `/api/permission-requests` / `/roles` | 权限申请与可申请角色 |
| POST | `/api/permission-requests/{id}/decide` | 其他角色审批 |
| GET | `/api/rbac/resources` | 资源码清单 |
| POST | `/api/agent/chat` | `{sessionId?,message}` |
| GET | `/api/agent/sessions` / `/{id}/messages` | 会话与消息 |
| GET | `/api/agent/replay/{traceId}` | 用户自有轨迹回放 |
| GET/DELETE | `/api/agent/memories` | 长期记忆查看/删除 |
