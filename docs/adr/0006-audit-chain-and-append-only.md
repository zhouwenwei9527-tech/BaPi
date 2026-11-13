# ADR-0006: 审计哈希链、fail-closed 与数据库只追加

- 状态：已接受（2026-11-11）
- 关联：INV-6、AC-09、AC-16

## 背景
审计必须可检出篡改，写类 Tool 在审计不可用时不得执行。

## 决定
1. `audit_event` 使用 `prev_hash + 规范化事件内容` 的 SHA-256 链；`verifyChain()` 全量复算。
2. 写类 Tool 在执行前先落一条 `EXECUTING` 审计；失败立即返回 `AUDIT_UNAVAILABLE` 且不执行。
3. 应用代码没有对 `audit_event` / `trace_event` 的 UPDATE/DELETE 方法。
4. MySQL 迁移 `V5__append_only_audit.sql` 创建 BEFORE UPDATE/DELETE 触发器，数据库层拒绝改写。
5. 审批状态使用 CAS 迁移，执行前复验发起人权限、Tool 激活状态与 `argsHash`。

## 后果
- 篡改、重放参数、并发双批都被测试覆盖。
- 生产建议进一步用独立数据库账号仅授予 `SELECT, INSERT`；见 `docker/mysql-harden.sql`。
