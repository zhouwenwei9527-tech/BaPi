# ADR-0001: 技术栈与模块边界

- 状态：已接受（2026-11-11）
- 关联：PRD §3.1、§4

## 背景
PRD 基线为 Java 17+、Spring Boot 3.x、Maven 多模块、React 18 + TypeScript。需要一套能在单机 Docker
中间件上跑通、又能被 ArchUnit 约束的科研级实现。

## 决定
1. 后端使用 Java 17 目标字节码、Spring Boot 3.5.8、Maven 多模块：
   `contract` / `rbac-core` / `tool-registry` / `agent-runtime` / `retrieval` / `app-demo`。
2. 前端使用 React 18 + TypeScript + Vite 6 + Zustand，自研设计系统（不引入 UI 组件库），
   以便精确控制液态玻璃、窗口与 Dock 的交互。
3. 认证使用 256-bit 随机不透明 Token，数据库只保存 SHA-256 摘要；不引入完整 Spring Security Web 栈，
   密码使用 BCrypt。OIDC 作为后续扩展点。
4. 业务数据 MySQL 8，向量索引 PostgreSQL + pgvector，文件对象 MinIO；Flyway 管理全部 DDL。
5. 所有 HTTP 返回统一 `ApiResponse` 信封；错误码集中在 `aurora-contract`。

## 后果
- 模块依赖可以被 ArchUnit 精确约束（AC-25）。
- 不透明 Token 支持服务端吊销；代价是需要一次数据库查询。
- 前端完全掌控视觉语言；代价是需要自己实现可访问性与响应式细节。
