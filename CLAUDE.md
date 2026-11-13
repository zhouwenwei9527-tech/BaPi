# CLAUDE.md

BaPi（代号 Aurora）：macOS 范式的 AI 原生后台框架。完整需求在 `docs/PRD.md`，视觉原型在
`docs/prototype/`，实现说明在 `README.md`，决策在 `docs/adr/`。

## 当前状态（2026-11-12）
- Phase 0-6 的主要交付已完成：多模块后端、RBAC、Tool Gateway、审批、审计哈希链、检索 ACL、
  Agent 轨迹/预算/录制重放、插件生命周期、React 桌面壳层、Docker 中间件。
- 已移除/降级：Qdrant 用 pgvector 替代（PRD 允许）；插件动态 JAR 未实现（见 open-questions）。
- 验收：后端 45 个测试、Vitest 9 个、Playwright 21 个（含 axe 深浅主题、数据入口、权限治理、风险确认与个性化）。
- 壁纸：预设/元数据存 MySQL `user_preference`，图片压缩后存 MinIO，随账号跨设备恢复；
  保留最近 3 张可切换，无需审批。程序坞顺序也写入 MySQL；桌面图标入口已移除。
- 权限内写操作（用户新增/编辑/删除、文件分享、数据集导入）由本人确认风险后直接执行，
  低可操作度需二次确认；角色变更仍走双人复核。本人资料直接生效。
- 治理：admin 通配 `*`；权限中心可编辑角色/菜单；用户列表服务端分页；普通用户可申请角色，其他角色审批。
- 数据入口：`用户管理`（create/update/delete 本人确认后直接生效，assignRole 双人复核）、`访达` 上传文件、`数据流` 导览；独立复核人 auditor/audit123。

## 常用命令
- 中间件：`docker compose -f docker/compose.yml up -d`
- 后端全量：`mvn -pl aurora-app-demo test`
- 单模块：`mvn -pl aurora-rbac-core -am test`
- 启动后端：`cd aurora-app-demo && mvn spring-boot:run`
- 前端：`cd aurora-ui-shell && pnpm dev`
- 前端测试：`pnpm test`、`pnpm e2e`、`pnpm build`
- 一键：`./scripts/dev.sh`、`./scripts/stop.sh`、`./scripts/smoke.sh`、`./scripts/test.sh`
- Java：`JAVA_HOME=.../corretto-17.0.17/Contents/Home`

## 架构红线（不可违反）
- Agent 只能通过 `ToolInvocationGateway` 调 Tool；不得调用 Controller / Service。
- 未注册、未激活的 Tool 一律拒绝；`DESTRUCTIVE` 工具不能设为 `AUTO`。
- 权限在发起时和执行时都要校验；`SELF_CONFIRM` 不绕过权限，`ASK` 审批仍绑定 `argsHash`。
- 审计只追加；写审计失败则写类 Tool 不执行。
- 检索 ACL filter 在向量库查询时前置；窗口期用 MySQL 当前 ACL 二次校验。
- `rbac-core` 不得依赖任何 AI/Tool/Retrieval 类；Tool 内不得手写数据范围 SQL。
- 插件不能声明不存在的权限码，装载后默认 `PENDING_AUTH`。
- 桌面、菜单、Palette ACTION 必须共享 `src/lib/permissions.ts` 的 `guard()`。

## 测试约定
- 安全集成测试不 mock RBAC/Gateway/审批/ACL；只能 mock 模型与外部网络。
- 验收测试在 `aurora-app-demo/src/test/java/io/aurora/app/acceptance/`，每个 AC 都有 `@DisplayName`。
- 前端 E2E 使用 Playwright + 本地 Chrome for Testing，禁止 `my-computer-use` / 系统权限面板。

## 代码约定
- 包名前缀 `io.aurora`；资源码 `resource:action`。
- 数据库变更只能走 Flyway；MySQL 专属迁移放 `db/migration-mysql`。
- 错误码在 `aurora-contract`；日志中的 Tool 参数只记录摘要 `argsDigest`。
- 插件只提交声明式描述，不提供任意 JS。
