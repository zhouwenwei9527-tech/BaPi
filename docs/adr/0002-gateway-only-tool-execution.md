# ADR-0002: 模型只提议，Gateway 独占 Tool 执行

- 状态：已接受（2026-11-11）
- 关联：INV-1、INV-9、AC-01、AC-25、AC-26

## 背景
Spring AI 等框架默认允许模型直接触发工具执行，这与“所有 Tool 调用必须经过唯一入口”冲突。
同时需要一个可离线、可录制回放的模型边界。

## 决定
1. 定义 `ModelPort`，只负责返回文本或 `ToolCall`，**绝不执行工具**。
2. `ToolInvocationGateway` 是唯一调用 `ToolExecutor.execute` 的类；固定九步管线，任意出口写审计。
3. 默认实现 `RuleBasedModelPort`（确定性、可测试），生产可替换为任意模型适配器。
4. `ModelCallRecord` 记录每次模型请求/响应；`ReplayModelPort` 可重放录制结果，复现 Agent 决策序列。
5. Agent 循环在 Gateway 之外只做预算、轨迹和消息编排。

## 后果
- 恶意模型输出无法绕过 RBAC、审批或审计（AC-26 有测试）。
- 接入真实模型需要实现一个 `ModelPort` 适配器。
