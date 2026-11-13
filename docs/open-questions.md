# 待确认问题与已采用假设

| 编号 | 问题 | 当前采用 | 影响 |
| --- | --- | --- | --- |
| Q1 | 产品正式名称？ | 产品名 **BaPi**，代号 **Aurora**，包名 `io.aurora` | 可全局替换 |
| Q2 | `tenant_id` 是否固定 0？ | 固定 0，所有表保留列 | 多租户前需迁移 |
| Q3 | 前端是否接受 React？ | 已采用 React 18 + TS + Vite | — |
| Q4 | 模型与多模态 Embedding 供应商？ | 演示使用确定性 `local-hash-256`；真实供应商待定 | 语义质量受限于本地实现 |
| Q5 | 登录方式？ | 自建账号 + 不透明 Token，预留 OIDC | SSO 需新增适配器 |
| Q6 | WRITE 是否允许自批？ | 不允许；单级审批 | 高安全场景可增加多级 |
| Q7 | 部署形态？ | 单体 + docker compose | K8s 清单未提供 |
| Q8 | 审计与记忆留存？ | 审计 ≥ 1 年（触发器保护），记忆由用户删除 | 需归档任务 |
| Q9 | Qdrant 镜像不可用？ | 使用 PRD 允许的 pgvector 降级；`VectorStorePort` 可替换 | 压测参数需按 pgvector 校准 |
| Q10 | 插件 JAR 热加载？ | 当前为声明式描述 + 宿主绑定执行器；动态 ClassLoader 未实现 | AC-22 ClassLoader 部分延期 |
| Q11 | 审计数据库账号隔离？ | 已加 MySQL 只追加触发器；独立账号脚本见 `docker/mysql-harden.sql` | 生产应切换独立账号 |
| Q12 | 真实模型密钥与路由？ | 未配置；`ModelPort` 已预留替换点 | 需要供应商与密钥管理方案 |
| Q13 | 增强档折射如何实现？ | 当前用高光/饱和度近似 SVG 折射；runtime 上限 3 个 surface | 真实折射需 `backdrop-filter: url()` 能力探测 |
| Q14 | 浏览器矩阵？ | E2E 覆盖 Chromium；Firefox/Safari 只提供 CSS 降级 | 需补齐多浏览器 CI |
| Q15 | 登录限流/CSP？ | 未启用；内网演示 | 上线前需网关限流与 CSP |
| Q16 | 文件解析范围？ | 文本类抽取正文；PDF/DOCX 先用标题/摘要/文件名检索 | 接入 Tika/OCR 后扩展 |
| Q17 | 个性化设置存储？ | 壁纸/Dock/桌面布局存在浏览器 localStorage | 跨设备需服务端用户偏好表 |
| Q18 | 风险评分依据？ | Tool 描述策略：资料 99、角色提权 5-10、删除 15、共享 70 | 可接策略引擎/历史行为模型 |
