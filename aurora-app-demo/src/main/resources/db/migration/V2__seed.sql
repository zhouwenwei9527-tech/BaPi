-- Departments
INSERT INTO sys_department (id, name, parent_id, sort_order) VALUES
 (1, '总部', NULL, 0),
 (2, '运营部', 1, 1),
 (3, '财务部', 1, 2),
 (4, '研发部', 1, 3),
 (5, '运营一组', 2, 4),
 (6, '运营二组', 2, 5);

-- Users (password: admin123 / ops123 / finance123 / guest123)
INSERT INTO sys_user (id, username, display_name, password_hash, dept_id, status, title, avatar_hue) VALUES
 (1, 'admin', '林深', '$2b$12$HSWxJcvZrhj7ztjP1nryEeXYFwdv/UHQte28wsyIw2zm1TIC2k9oi', 1, 'ACTIVE', '系统管理员', 210),
 (2, 'ops', '陈曦', '$2b$12$8r2H5qMyTuT4Gwwna2k5q.gKU28yQLlENYPdieSjEPhMR3HhBqBm.', 2, 'ACTIVE', '运营负责人', 265),
 (3, 'finance', '王珂', '$2b$12$s50EFDcVWDSq.uor79OHl.wtsZqcKtaiENoMD.QR/puXPjPDvzUIu', 3, 'ACTIVE', '财务分析师', 150),
 (4, 'guest', '访客', '$2b$12$K8cgy6CAjDP7.F8iWlJnOefx4cWFnhBVJtc0ebP/5Z/UX5b5D/7jC', 4, 'ACTIVE', '只读访客', 200),
 (5, 'zhangsan', '张三', '$2b$12$K8cgy6CAjDP7.F8iWlJnOefx4cWFnhBVJtc0ebP/5Z/UX5b5D/7jC', 4, 'ACTIVE', '研究员', 25),
 (6, 'lisi', '李四', '$2b$12$K8cgy6CAjDP7.F8iWlJnOefx4cWFnhBVJtc0ebP/5Z/UX5b5D/7jC', 3, 'ACTIVE', '财务专员', 320),
 (7, 'wangwu', '王五', '$2b$12$K8cgy6CAjDP7.F8iWlJnOefx4cWFnhBVJtc0ebP/5Z/UX5b5D/7jC', 5, 'ACTIVE', '运营专员', 190),
 (8, 'zhaoliu', '赵六', '$2b$12$K8cgy6CAjDP7.F8iWlJnOefx4cWFnhBVJtc0ebP/5Z/UX5b5D/7jC', 6, 'ACTIVE', '运营专员', 45),
 (9, 'sunqi', '孙七', '$2b$12$K8cgy6CAjDP7.F8iWlJnOefx4cWFnhBVJtc0ebP/5Z/UX5b5D/7jC', 4, 'ACTIVE', '实验员', 280);

-- Roles
INSERT INTO sys_role (id, code, name, description) VALUES
 (1, 'admin', '系统管理员', '全量权限，ALL 数据范围'),
 (2, 'ops', '运营', '用户与运营数据，部门及子部门'),
 (3, 'finance', '财务', '财务数据，本部门'),
 (4, 'guest', '只读访客', '仅公共知识与本人数据');

INSERT INTO sys_data_scope_rule (role_id, type, custom_dept_ids) VALUES
 (1, 'ALL', ''),
 (2, 'DEPT_AND_CHILD', ''),
 (3, 'DEPT', ''),
 (4, 'SELF', '');

-- Permission resources (resource:action). MENU rows drive the app/menu surface.
INSERT INTO sys_resource (id, code, name, type, parent_code, sort_order, icon, description) VALUES
 (1, 'menu:workspace', '工作台', 'MENU', NULL, 0, 'layout-grid', '高频工作入口'),
 (2, 'menu:governance', '治理与审计', 'MENU', NULL, 10, 'shield', '权限、审批与轨迹'),
 (3, 'menu:system', '系统', 'MENU', NULL, 20, 'settings', '框架自身的管理面'),
 (4, 'file:read', '访达', 'MENU', 'menu:workspace', 1, 'folder', '对象浏览与上下文构造'),
 (5, 'agent:use', 'Agent 助手', 'MENU', 'menu:workspace', 2, 'sparkles', '以本人权限运行的执行引擎'),
 (6, 'knowledge:public', '公共知识', 'DATA', 'menu:workspace', 3, 'book-open', '公开知识库切片'),
 (7, 'knowledge:internal', '内部知识', 'DATA', 'menu:workspace', 4, 'file-text', '内部流程与白皮书'),
 (8, 'knowledge:hr', '人事知识', 'DATA', NULL, 30, 'users', '薪酬与人事制度'),
 (9, 'file:finance', '财务文件', 'DATA', NULL, 31, 'landmark', '仅财务可见的文件'),
 (10, 'user:read', '用户目录', 'API', 'menu:governance', 11, 'users', '按数据范围读取用户'),
 (11, 'user:update', '调整用户角色', 'TOOL', NULL, 32, 'user-cog', '写操作，进入审批'),
 (12, 'user:delete', '删除用户', 'TOOL', NULL, 33, 'user-minus', '破坏性操作，不可自动执行'),
 (13, 'file:share', '对外共享文件', 'TOOL', NULL, 34, 'share-2', '生成短时效外链'),
 (14, 'approval:read', '审批队列', 'MENU', 'menu:governance', 12, 'check-square', '查看待处理审批'),
 (15, 'approval:decide', '审批决策', 'API', NULL, 35, 'gavel', '批准或拒绝审批'),
 (16, 'audit:read', '审计回放', 'MENU', 'menu:governance', 13, 'history', '哈希链与轨迹回放'),
 (17, 'audit:verify', '校验审计链', 'TOOL', NULL, 36, 'git-branch', '校验只追加日志'),
 (18, 'tool:read', 'Tool 注册表', 'MENU', 'menu:system', 21, 'wrench', '所有可被 AI 调用的能力'),
 (19, 'plugin:manage', '插件', 'MENU', 'menu:system', 22, 'puzzle', '受控扩展点与生命周期'),
 (20, 'rbac:read', '权限中心', 'MENU', 'menu:system', 23, 'key-round', '角色与资源矩阵'),
 (21, 'glass:use', '外观与玻璃', 'MENU', 'menu:system', 24, 'droplet', '液态玻璃 token'),
 (22, 'knowledge:manage', '知识 ACL 管理', 'API', NULL, 37, 'lock', '修改文档访问控制');

INSERT INTO sys_role_resource (role_id, resource_id)
SELECT 1, id FROM sys_resource;

INSERT INTO sys_role_resource (role_id, resource_id) VALUES
 (2, 1), (2, 4), (2, 5), (2, 6), (2, 7), (2, 10), (2, 11), (2, 14), (2, 21),
 (3, 1), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8), (3, 9), (3, 10), (3, 21),
 (4, 1), (4, 4), (4, 6), (4, 21);

INSERT INTO sys_user_role (user_id, role_id) VALUES
 (1, 1), (2, 2), (3, 3), (4, 4), (5, 2), (6, 3), (7, 2), (8, 2), (9, 4);

-- Knowledge / file objects. ACL is duplicated into the vector payload by the indexer.
INSERT INTO kb_document (id, title, summary, content, object_type, object_id, owner_id, visibility,
    visible_roles, visible_depts, acl_version, page_no, para_no, required_permission,
    storage_key, mime_type, file_name, file_size) VALUES
 ('d1', '数据范围在 Tool 层如何传递',
  'Principal 携带 DataScope，由框架数据访问层自动追加过滤，Tool 内禁止手写范围 SQL。',
  '数据范围在 Tool 层的传递遵循三条原则。第一，每次调用都携带 Principal，其中包含 userId、roles、dataScope 与 tenantId。第二，Gateway 在执行前把 Principal 注入 ToolExecutionContext，Tool 只能通过框架数据访问层读取数据。第三，框架数据访问层根据 DataScope 自动追加 dept_id 或 owner_id 过滤，开发者不得在 Tool 内手写数据范围 SQL。多角色时数据范围取并集，权限变更后缓存最多五秒失效。',
  'manual', 'f1', 1, 'PUBLIC', '', '', 1, 3, 1, 'knowledge:public',
  'docs/d1.md', 'text/markdown', 'Aurora 权限手册.pdf', 248000),
 ('d2', '写操作为什么默认挂起审批',
  'WRITE 与 DESTRUCTIVE 默认 ASK；审批绑定 argsHash，执行前再次校验发起人权限。',
  '写操作默认挂起审批，是因为模型输出不等于人的意图。审批单保存工具名、版本、argsHash、脱敏参数摘要、影响对象、副作用与可回滚性。批准后执行前还要复验：发起人当前仍拥有权限、Tool 仍为 ACTIVE、argsHash 一致、审批未过期。DESTRUCTIVE 操作永远不能自批，WRITE 默认也不允许自批。并发批准通过 CAS 保证只执行一次。',
  'whitepaper', 'f2', 1, 'PUBLIC', '', '', 1, 7, 2, 'knowledge:internal',
  'docs/d2.md', 'text/markdown', '审批策略白皮书.pdf', 512000),
 ('d3', '岗位薪酬分级办法',
  '薪酬带宽、职级与年度调薪规则，仅财务与管理员可见。',
  '岗位薪酬分为五个带宽。P1 至 P3 为执行层，P4 至 P5 为专业层，P6 以上为专家与管理层。年度调薪结合绩效系数与市场分位。所有薪酬数据不得进入公共检索，向量 payload 标记 visibility=ROLES 且 visibleRoles 包含 finance。审计日志记录每次访问但不记录具体金额。',
  'policy', 'f3', 3, 'ROLES', 'finance,admin', '', 2, 2, 1, 'knowledge:hr',
  'docs/d3.md', 'application/pdf', '薪酬制度.pdf', 730000),
 ('d4', 'Aurora 权限手册：RBAC 与 Tool 的双轨模型',
  '菜单权限决定人能否看到入口，Tool 权限决定 AI 能否执行动作，两者共享资源码但不互相替代。',
  'Aurora 采用双轨权限模型。菜单权限回答人是否能看到入口，Tool 权限回答 AI 是否可以执行动作。两者共享 resource:action 资源码，但检查发生在不同边界。桌面图标、菜单、Command Palette 的 ACTION 结果必须走同一个路由守卫；AI 侧则统一经过 ToolInvocationGateway。任何未注册或未激活的 Tool 默认拒绝。',
  'manual', 'f4', 1, 'PUBLIC', '', '', 1, 1, 1, 'knowledge:public',
  'docs/d4.md', 'text/markdown', 'Aurora 权限手册.pdf', 248000),
 ('d5', '审批绑定参数哈希与执行前复验',
  '批准的是参数，不是工具名。执行前比对 argsHash，任何漂移都返回 ARGS_MISMATCH。',
  '审批绑定参数哈希可以防止批准后被替换参数。argsHash 由规范化 JSON 计算 SHA-256，规范化要求对象键递归排序。执行前重新计算并存比对，不一致直接失败且不执行业务逻辑。执行前复验还包括发起人权限、Tool 激活状态与过期时间。失败时审批状态迁移为 FAILED 并写审计。',
  'whitepaper', 'f5', 1, 'PUBLIC', '', '', 1, 9, 3, 'knowledge:internal',
  'docs/d5.md', 'text/markdown', '审批策略白皮书.pdf', 512000),
 ('d6', '向量检索 ACL filter 设计',
  'Qdrant 或 pgvector 查询自带 ACL 条件，不允许先召回再过滤。',
  '检索端口只接受 Principal，调用方不能传入原始 filter。向量 payload 包含 tenantId、objectType、objectId、ownerId、visibility、visibleRoles、visibleDepts 与 aclVersion。查询 filter 为 tenantId 相等，并且 visibility=PUBLIC 或角色交集或部门交集或 ownerId 等于本人。ACL 变更后 aclVersion 递增，payload 异步重写；窗口期内结果返回前再用 MySQL 当前 ACL 二次校验。为抵消二次校验损失，召回时 over-fetch 两倍。',
  'research', 'f6', 1, 'PUBLIC', '', '', 1, 5, 1, 'knowledge:public',
  'docs/d6.md', 'text/markdown', '多模态检索综述.pdf', 1240000),
 ('d7', '审计哈希链与 fail-closed',
  '审计只追加；写类 Tool 写审计失败时不得执行。',
  '审计事件包含 prevHash 与 hash，hash 等于 SHA-256(prevHash + 规范化事件内容)。任何一条记录被修改都会导致后续链条校验失败。应用账号对审计表只有 INSERT 与 SELECT 权限。写类 Tool 在执行前必须先成功落盘一条执行中事件，否则立即返回 AUDIT_UNAVAILABLE 并且不执行。读类 Tool 执行后审计失败同样报错，但不产生副作用。',
  'whitepaper', 'f7', 1, 'PUBLIC', '', '', 1, 11, 2, 'knowledge:public',
  'docs/d7.md', 'text/markdown', '审计白皮书.pdf', 380000),
 ('d8', '插件生命周期与 PENDING_AUTH',
  '插件装载后默认 PENDING_AUTH；不能声明 RBAC 中不存在的权限码。',
  '插件是受控扩展点，不扩展权限解释权。安装时逐个校验 Tool 声明的权限码必须存在于 sys_resource，任一缺失返回 PLUGIN_UNKNOWN_PERMISSION 并拒绝整个安装。安装成功后状态为 PENDING_AUTH，其 Tool 已注册但不可调用，调用返回 TOOL_NOT_ACTIVE。管理员显式激活后状态为 ACTIVE，Tool 才可执行。插件卸载时其 Tool 与挂载点一并注销。',
  'manual', 'f8', 1, 'PUBLIC', '', '', 1, 13, 1, 'knowledge:internal',
  'docs/d8.md', 'text/markdown', '插件开发指南.pdf', 610000),
 ('d9', '财务月报 2025-10 分析',
  '本月收入与成本结构分析，仅财务角色与管理员可检索。',
  '十月总收入环比增长 8.4%，其中订阅收入增长 11.2%，项目收入下降 2.1%。毛利率维持在 63% 左右。研发投入占收入 22%，销售费用占 15%。应收账款周转天数从 48 天下降到 43 天。风险提示：两家大客户续约待确认，预计影响下季度收入 6%。',
  'finance', 'f9', 3, 'ROLES', 'finance,admin', '', 3, 1, 1, 'file:finance',
  'docs/d9.md', 'text/markdown', '财务月报_10月.xlsx', 980000),
 ('d10', '实验设计：催化剂筛选',
  '研发部内部实验记录，包含样本批次与测试条件。',
  '本轮实验比较三种催化剂在不同温度下的转化率。样本批次 A 在 180 摄氏度转化率达到 92%，批次 B 在 200 摄氏度出现副产物。实验采用随机区组设计，每组三次重复。数据由实验室 LIMS 系统导入，原始记录保存在对象存储中。该文档仅研发部可见。',
  'research', 'f10', 5, 'DEPTS', '', '4', 1, 1, 1, 'knowledge:internal',
  'docs/d10.md', 'text/markdown', '实验设计.docx', 2400000),
 ('d11', '运营 SOP：用户生命周期',
  '新增、激活、留存与流失干预的标准流程，运营部及子部门可见。',
  '用户生命周期分为引入、激活、留存、召回与流失五个阶段。新增用户当周需要完成首次关键行为，否则进入唤醒名单。运营一组负责新用户激活，运营二组负责存量召回。所有批量操作必须通过 Tool 并进入审批，禁止直接修改数据库。指标口径以数据平台为准。',
  'sop', 'f11', 2, 'DEPTS', '', '2,5,6', 2, 2, 2, 'knowledge:internal',
  'docs/d11.md', 'text/markdown', '运营SOP.pdf', 420000),
 ('d12', '多模态检索综述',
  '文字与图片共享向量空间的三种主流路线，以及 ACL 前置过滤的工程取舍。',
  '多模态检索通常采用对比学习把图像与文本映射到同一空间。工程实现上可选择 CLIP 类双塔模型、OCR 后文本检索、或视觉描述模型生成文字再检索。Aurora 在本地开发环境使用确定性的哈希向量作为降级实现，生产环境通过 EmbeddingPort 替换为真实多模态模型。无论使用哪种实现，ACL filter 必须前置到向量库查询。',
  'paper', 'f12', 1, 'PUBLIC', '', '', 1, 6, 1, 'knowledge:public',
  'docs/d12.md', 'text/markdown', '多模态检索综述.pdf', 1240000),
 ('d13', '陈曦的个人工作笔记',
  '仅本人可见的运营复盘与待办。',
  '本周复盘：新用户激活率提升 3.2 个百分点，召回活动触达 1.8 万人。下周计划：完成二组交接文档，梳理审批耗时分布。个人笔记中记录了尚未公开的实验性想法，不应出现在任何其他人的检索结果中。',
  'note', 'f13', 2, 'OWNER', '', '', 1, 1, 1, 'knowledge:internal',
  'docs/d13.md', 'text/markdown', '运营笔记.md', 18000),
 ('d14', '成本中心与预算表',
  '各部门成本中心编码与年度预算，仅财务部可见。',
  '成本中心编码规则为 CC-部门-项目。研发部预算占比 34%，运营部 28%，财务与行政 12%，市场 26%。预算执行按月滚动预测，偏差超过 10% 需要说明。所有金额单位为人民币万元。该表由财务月结流程维护。',
  'finance', 'f14', 3, 'DEPTS', '', '3', 1, 4, 1, 'file:finance',
  'docs/f14.md', 'text/markdown', '预算表_2025.xlsx', 156000);
