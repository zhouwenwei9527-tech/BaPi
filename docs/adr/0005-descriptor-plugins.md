# ADR-0005: 插件采用声明式描述与状态机，动态 JAR 热加载后移

- 状态：已接受（2026-11-11）
- 关联：INV-8、AC-21~24、PRD §7.3

## 背景
PRD 要求插件不提供任意 JS、不能扩展权限解释权、装载后默认 `PENDING_AUTH`；
Phase 6 还要求验证动态 JAR 的 ClassLoader 回收。

## 决定
1. 插件以 `PluginDescriptor` 声明 Tool、挂载点、版本与来源；壳层用自己的组件渲染。
2. 生命周期 `PENDING_AUTH → ACTIVE ↔ DISABLED`，卸载时 Tool 与挂载点一并注销。
3. 安装时逐项校验权限码必须存在于 `sys_resource`，否则整体拒绝（`PLUGIN_UNKNOWN_PERMISSION`）。
4. 版本不兼容插件保持 `DISABLED` 并给出原因。
5. 动态 JAR 加载与 ClassLoader 回收验证不在 v1.0 实现范围内；插件执行器由宿主在安装时绑定。

## 后果
- 扩展面可控、可审计，前端不需要执行第三方代码。
- AC-22 的“ClassLoader 可回收”部分未实现；记录在 `docs/open-questions.md`。
