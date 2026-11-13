# ADR-0007: 数据入口与闭环

- 状态：已接受（2026-11-11）
- 关联：INV-1、INV-3、PRD §5.3、AC-03

## 背景
壳层最初只有读取与演示数据，用户无法从 UI 新增/修改/删除用户，也无法上传知识文件；
审批看起来“凭空出现”，新使用者无法判断数据来源与下一步。

## 决定
1. **用户写操作走 Tool Gateway**：新增 `user.create`、`user.update`，与既有 `user.assignRole`、
   `user.delete` 一样是 Tool；UI 只提交参数，由 Gateway 做 RBAC、Schema、预算与审批决策。
2. **独立审批人**：种子新增 `security` 角色与 `auditor` 账号，用于 WRITE/DESTRUCTIVE 的职责分离；
   发起人与审批人必须是不同 userId。
3. **授予角色不得提权**：服务端检查“目标角色权限 ⊆ 发起人权限”，UI 只展示 `grantable` 角色。
4. **文件入口走 REST**：二进制无法放进 JSON Tool 参数，因此 `POST /api/knowledge/upload` 负责
   MinIO 入库、MySQL 元数据与 pgvector 重建；该接口仍需 `knowledge:manage`，并写审计事件。
5. **可视化来源**：New `数据流` app 展示 Seed → 人工录入 → 索引 → 检索 → Agent → Gateway →
   审批 → 审计 → 插件，并提供直达入口；审批页明确说明“审批由 Tool 调用产生”。

## 后果
- 数据入口不再绕过权限与审计；演示可以完整走完“提交 → 独立审批 → 生效 → 删除”。
- 文件上传目前只对文本类抽取正文；PDF/DOCX 以标题、摘要、文件名参与检索。
