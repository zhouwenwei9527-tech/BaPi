# ADR-0003: 向量库采用 pgvector 降级实现，保持 VectorStorePort 可替换

- 状态：已接受（2026-11-11）
- 关联：PRD §3.1、§5.6、AC-10~13

## 背景
PRD 默认 Qdrant，但本机 Docker registry 镜像源无法拉取 `qdrant/qdrant`，而本地已有 pgvector 镜像。
PRD 明确允许 pgvector 作为降级实现。

## 决定
1. `VectorStorePort` 只接受 `VectorQuery(tenantId, embedding, limit, Principal)`；
   ACL 谓词由适配器内部生成，调用方无法传入或绕过。
2. 生产适配器 `PgVectorStore` 在 SQL `WHERE` 中直接写入
   `visibility=PUBLIC / ROLES 交集 / DEPTS 交集 / OWNER=本人`，并在向量查询上附带权限码过滤。
3. 测试/离线使用 `InMemoryVectorStore`，谓词逐条镜像 SQL 语义。
4. 检索服务按 `overFetch=2` 召回，再用 MySQL 当前 ACL 二次校验；ACL 变更 API 同步重写 payload，
   窗口期仍由二次校验兜底（AC-11 测试覆盖）。
5. Qdrant 适配器只需实现同一端口；`docker/compose.yml` 注释说明替换方式。

## 后果
- 本地一键启动可用；语义与 Qdrant payload filter 等价。
- pgvector 的 HNSW/IVF 参数需在生产压测后校准。
