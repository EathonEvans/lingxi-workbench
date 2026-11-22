# 意图树独立管理与 Application 路由 V1 开发计划

> 历史设计档案：强制 `intentBinding`、固定兜底和内嵌 Workflow 已删除，当前运行时不兼容本协议。
> 当前设计见 [Application 执行大脑 V1 开发计划](application-execution-brain-v1.md) 和
> [Application 执行大脑 V1 代码设计](application-execution-brain-code-design-v1.md)。
> 状态：M1、M2 与可运行的 M4 主链路已实现；候选 LLM 复判和评测门禁待后续迭代  
> 更新时间：2026-08-05  
> 目标：以向量语义召回为候选入口，完成“识别、澄清、兜底、执行、评测”的最小闭环

## 1. 架构结论

意图树是独立管理资源，拥有自己的草稿、校验、发布版本和向量索引生命周期。它只描述“用户想做什么”，不绑定 Agent 或 Workflow。Application 选择一棵已发布意图树，并维护“叶子意图编码 → 执行目标”的路由映射。

```text
IntentTreeVersion              ApplicationVersion
├── 节点与层级                 ├── 锁定 intentTreeVersionId
├── 描述、正例与反例           ├── 意图到执行目标的映射
├── 槽位与澄清元数据           ├── 分类模型与兜底策略
└── Embedding 配置与向量       └── 锁定 Agent/Workflow 版本
```

Application 发布时解析 `intentTreeId` 当前生效版本并锁定 `intentTreeVersionId`。之后意图树发布新版本，不影响已有 ApplicationVersion。

```text
Intent Router
├── AGENT            普通或 Supervisor Agent；ReAct 由 Agent 配置决定
├── WORKFLOW         确定性业务流程
└── FIXED_RESPONSE   固定引导或能力说明
```

V1 采用以下路由链：

```text
用户当前请求向量化
→ 叶子意图向量 Top-K 召回
→ 后端召回阈值、Top-1/Top-2 分差和槽位门控
→ 执行 / 澄清 / 闲聊 / 超范围兜底
```

关键词不作为主要分类手段；仅在已进入澄清会话后识别取消等控制指令。向量相似度是候选召回分数，不等同于模型置信度。候选限定的 LLM 复判保留为后续增强项，不阻塞当前向量门控闭环。

## 2. V1 范围

### 2.1 必须完成

1. 意图树独立维护最多三层的节点结构，并支持草稿、校验、发布和版本回看；
2. 中间节点只做分类，叶子节点只描述语义，不直接绑定执行目标；
3. 叶子节点支持描述、正例、反例、必需槽位和澄清问题；
4. 意图树配置 Embedding 模型、Top-K、召回阈值和歧义分差；
5. 发布意图树时为每个叶子生成一个原型向量和多个正例向量；
6. 运行时通过 pgvector 召回候选叶子，并以阈值和候选分差进行确定性门控；
7. 支持命中、歧义、缺少槽位、未命中和超范围结果；
8. 澄清状态可以跨消息恢复，完成后继续原任务；
9. 闲聊是显式叶子意图，默认禁止 Tool 和副作用操作；
10. Application 独立配置叶子意图到 Agent、Workflow 或固定响应的映射；Supervisor 作为一种 Agent 类型被选择；
11. 管理端提供独立意图树管理，以及 Application 绑定与映射配置；
12. 建立第一批意图路由固定评测集。

### 2.2 本轮不实现

- 跨租户共享、公共市场或自动合并意图树；
- 超过三层的任意意图图或 DAG；
- 自动从线上会话挖掘和发布新意图；
- 在线学习、自动调阈值和自动修改生产树；
- 将 fallback 建模为可向量召回的普通意图；
- 将关键词命中数包装成模型置信度；
- 大规模 ANN/HNSW 索引；V1 按 IntentTreeVersion 小集合精确余弦检索；
- 未澄清意图直接执行写操作或高风险 Tool。

## 3. 配置模型

新增独立的意图树聚合：

```text
IntentTree
├── id / name / code / status
├── publishedVersionId
└── draftId

IntentTreeDraft
├── intentTreeId / revision
├── nodes / routingPolicy
└── embeddingModelInstanceId

IntentTreeVersion
├── intentTreeId / version / checksum
├── definitionSnapshot
└── publishedBy / publishedAt
```

节点字段：

```text
id / code / parentId / name / description
nodeType = ROUTER / LEAF
positiveExamples / negativeExamples
matchRules / slotSchema / clarificationTemplate
priority / enabled
```

在共享 `ApplicationDefinition` 中增加 `intentBinding`。草稿引用稳定资源 ID，发布快照锁定具体版本：

```text
ApplicationDefinition
├── schemaVersion
├── workflow
├── collaboration
└── intentBinding
    ├── enabled
    ├── intentTreeId
    ├── intentTreeVersionId
├── fallbackPolicy
    └── mappings[]
        ├── intentCode
        ├── targetType = AGENT / WORKFLOW / FIXED_RESPONSE
        └── targetId
```

约束：

- 节点编码在单个 IntentTree 内唯一；已发布版本不可修改，语义不兼容时使用新编码；
- 根节点唯一，所有节点从根可达，不允许环；
- 最大深度为三；
- ROUTER 不允许绑定目标；
- LEAF 只维护语义、槽位和澄清元数据；
- Application 启用的叶子必须存在目标映射，或显式交由 fallback 处理；
- AGENT 目标在草稿中保存稳定 `agentId`，发布时解析并锁定合法 AgentVersion；
- WORKFLOW 目标在草稿中保存独立 `workflowId`，Application 发布时锁定合法 WorkflowVersion；
- fallback、未识别和超范围属于路由策略，不进入向量索引。

## 4. 向量化与索引

### 4.1 向量化对象

只向量化已启用的叶子意图：

```text
PROTOTYPE = 名称 + 描述 + 适用范围 + 槽位说明
EXAMPLE   = 每条正例单独生成一个向量
```

反例不混入正向量。反例用于 LLM 候选判定和离线混淆评测。Agent ID、Workflow ID、版本号、权限和 Tool 参数不进入语义文本。

候选分数 V1 采用：

```text
intentScore = max(prototypeSimilarity, max(exampleSimilarity))
```

先保持计算可解释；权重融合必须经过评测数据验证后再增加。

### 4.2 数据表

新增 `lingxi_vector.intent_tree_vector`：

```text
id
intent_tree_id
intent_tree_version_id
intent_node_id
intent_code
vector_kind = PROTOTYPE / EXAMPLE
source_ordinal
source_hash
embedding_model_instance_id
embedding_dimension
embedding
created_at
```

唯一约束覆盖 `intent_tree_version_id + intent_node_id + vector_kind + source_ordinal`。检索必须限定 `intent_tree_version_id`、Embedding 模型和维度，使用余弦距离。IntentTreeVersion 与向量均遵循 append-only 原则，不得被草稿更新覆盖。

### 4.3 发布流程

Embedding 是外部调用，不能放进长数据库事务：

```text
读取并校验 IntentTreeDraft revision
→ 生成规范化意图语义文本和 sourceHash
→ 事务外批量调用 EmbeddingGateway
→ 开启发布事务并锁定 IntentTree/Draft
→ 再次校验 revision 和 sourceHash
→ 原子写入 IntentTreeVersion、意图向量和 publishedVersionId
```

Embedding 失败时意图树发布失败，旧正式版本继续生效；禁止发布成功后依赖运行时静默补索引。

Application 发布时不重新生成向量，而是解析并锁定意图树当前正式版本，校验叶子映射完整性，再解析并锁定 AgentVersion 或独立 WorkflowVersion。

## 5. 运行时设计

### 5.1 核心职责

```text
IntentRoutingService
├── IntentCandidateRetriever
├── IntentDecisionPolicy
├── ClarificationSession
└── IntentExecutionDispatcher
```

- `IntentCandidateRetriever`：对当前消息和必要的对话摘要向量化，按 IntentTreeVersion 召回 Top-K；
- `IntentDecisionPolicy`：确定性检查召回阈值、Top-1/Top-2 分差、必需槽位、权限和风险；
- `IntentExecutionDispatcher`：根据已发布目标调用普通/Supervisor Agent、独立 WorkflowVersion 或固定响应；
- 未完成澄清或确认前，不得执行带副作用的 Tool。

运行时固定按以下顺序分发，Workflow 不再是 Chat 的默认执行外壳：

```text
用户消息 -> ApplicationVersion -> 意图识别 -> 命中叶子
  -> WORKFLOW: 加载 targetId + targetVersionId 对应的 WorkflowVersion
  -> AGENT: 直接执行 targetId + targetVersionId 对应的 AgentVersion
  -> FIXED_RESPONSE: 直接返回，不调用执行引擎
  -> CLARIFICATION: 发布澄清事件，等待用户补充
```

### 5.2 路由结果

```text
MATCHED
AMBIGUOUS
NEEDS_CLARIFICATION
UNMATCHED
OUT_OF_SCOPE
```

结构化结果至少包含：

```text
applicationVersionId
intentTreeVersionId
intentCode
candidateScores
classifierConfidence
reasonCode
slots
missingSlots
target
clarificationRequest
```

最终门控阈值不得按主观固定值上线，必须通过评测集校准。模型输出视为不可信输入，后端校验节点存在、目标已锁定、槽位类型合法且用户具备权限。

## 6. 澄清与兜底

新增 PostgreSQL 持久化的 `intent_routing_session`，Redis 仅可缓存，不作为唯一事实来源。会话同时锁定开始路由时的 `versionId` 和 `intentTreeVersionId`，避免澄清过程中任一资源发布新版本造成选项或执行目标漂移。

```text
PENDING
→ RESOLVED / CANCELLED / EXPIRED
→ EXECUTING
→ COMPLETED / FAILED
```

澄清类型：

- `INTENT_DISAMBIGUATION`：多个候选接近；
- `MISSING_SLOT`：目标明确但缺少必需参数；
- `OPERATION_CONFIRMATION`：副作用或高风险操作确认。

下一条用户消息优先交给 `ClarificationService`，支持按钮响应、“第一个/第二个”、自由文本补槽位、“算了”和开启新任务。意图澄清最多两轮，超限后进入能力菜单、受限通用 Agent 或人工处理。

未命中不能自动等同于闲聊。闲聊必须是显式、高置信度叶子；受限通用 Agent 默认只能回答和检索，不能调用写 Tool。

## 7. 接口与事件

### 7.1 意图树管理接口

意图树拥有独立接口和权限资源：

```text
POST /api/v1/intent-trees/create
POST /api/v1/intent-trees/save-draft/{id}
POST /api/v1/intent-trees/validate/{id}
POST /api/v1/intent-trees/publish/{id}
POST /api/v1/intent-trees/activate-version/{id}/{versionId}
POST /api/v1/intent-trees/test/{id}
GET  /api/v1/intent-trees/detail/{id}
GET  /api/v1/intent-trees/versions/{id}
```

测试接口只返回草稿候选结果，不执行目标，不写正式运行事实。Application 草稿、校验、发布和版本详情接口仅扩展 `intentBinding`；发布时检查意图树正式版本和执行映射。

### 7.2 Chat 请求

Chat 请求增加可选结构化澄清响应：

```text
clarificationResponse
├── clarificationId
├── optionCode
└── value
```

纯文本回复仍然支持，结构化选项用于消除“第二个”等表达的歧义。

### 7.3 SSE

新增 `clarification` 事件，载荷只包含问题、展示选项、轮次和过期时间；同时保留普通文本问题，兼容旧客户端。后续可以增加只读 `intent` 事件展示命中路径，但 V1 不向用户暴露内部置信度、Prompt 或模型推理。

## 8. 分阶段交付

### M1：配置协议与发布校验

- 新增 IntentTree、IntentTreeDraft、IntentTreeVersion 聚合及接口；
- 完成树结构、槽位、示例和 Embedding 模型能力校验；
- 管理端新增意图树列表、编辑、测试、发布和版本详情；
- 扩展 ApplicationDefinition、请求和前端类型，配置意图树绑定、执行映射与兜底策略；
- Application 详情页只读展示锁定的意图树版本和执行映射。

完成标准：意图树可独立维护和发布；Application 只能绑定已发布树，非法或缺失映射不能发布，正式版本快照可复现。

### M2：向量构建与候选召回

- 新增迁移、实体、Repo 和 pgvector 精确检索；
- 复用 `infra-ai` 的 `EmbeddingGateway`；
- 实现原型/正例语义文本生成、批量向量化和 sourceHash；
- 发布失败不影响旧版本；
- 提供 Top-K 候选召回单元和集成测试。

完成标准：固定查询能够在指定 IntentTreeVersion 下稳定召回预期叶子，且模型、维度和版本隔离有效。

### M3：结构化分类增强与确定性门控

- 后续实现候选限定的 LLM 复判器；
- 校验结构化输出、置信度、分差和槽位；
- 输出 MATCHED、AMBIGUOUS、UNMATCHED 等稳定结果；
- 固定指令规则只在分类器之前处理少量系统命令。

完成标准：分类器不能选择候选外意图，低分和相近候选不会直接执行。

### M4：澄清、闲聊与执行分发

- 建立持久化澄清 Session；
- 完成意图歧义、缺少槽位、取消、过期和操作确认；
- 接入普通/Supervisor Agent、Workflow 和固定响应；
- 闲聊 Agent 禁用写 Tool，未识别请求进入显式 fallback 策略；
- 增加 `clarification` SSE 和用户端选项交互。

完成标准：用户可经过多轮澄清继续原任务，刷新或断线后状态不丢失，未确认写操作不会执行。

### M5：评测与发布门禁

- 建立不少于 100 条的首批意图数据集；
- 覆盖正例、相似意图、否定表达、上下文追问、闲聊、超范围和多意图；
- 统计 Recall@K、Top-1、拒识率、歧义识别率、澄清成功率和误执行率；
- 保存 ApplicationVersion、IntentTreeVersion、候选编码/分数、最终状态、耗时和模型版本，不记录敏感原文；
- 根据数据集校准阈值，形成发布前回归报告。

完成标准：达到约定基线且误执行率为零后，意图路由才允许作为默认生产入口。

## 9. 测试与验收场景

后端必须覆盖：

- 树结构、重复编码、深度、孤立节点和目标版本校验；
- Embedding 数量、维度、模型不一致和外部失败；
- IntentTree 发布并发修改时 sourceHash/revision 冲突；
- Application 发布时意图树无正式版、版本失效或映射缺失；
- 向量 Top-K、版本隔离、阈值和相同分数排序；
- LLM 非法 JSON、候选外编码和伪造槽位；
- 歧义、缺槽位、取消、过期、重复回复和幂等；
- 闲聊、超范围、权限不足和高风险操作未确认；
- 普通 Agent、Supervisor Agent、Workflow 和固定响应目标分发；
- SSE 重放和澄清响应兼容。

端到端至少验证：

1. 明确问题命中单 Agent；
2. 固定流程命中 Workflow；
3. 开放复杂任务命中 Supervisor；
4. 同义表达没有关键词仍被正确召回；
5. 两个相似意图触发选项澄清；
6. 缺少业务参数时只询问缺失槽位；
7. 闲聊进入受限闲聊 Agent；
8. 未命中进入能力引导，不伪装成闲聊；
9. 高风险操作确认前不调用 Tool；
10. 发布新版本不改变已经开始的澄清会话。

## 10. 推荐实施顺序

本轮按 `M1 → M2 → M3 → M4 → M5` 顺序推进。M2 向量召回是本次范围内的正式能力，不作为后续可选优化。M1 合并前先冻结 IntentTree 与 Application Binding 两套边界清晰的协议；M2 通过后再接 LLM 分类，避免把召回、分类和执行问题混在一起调试；M4 完成前不允许意图路由接管默认 Chat 主链路。

新 Application 必须配置启用的 `intentBinding`；未配置或未启用的历史 Application 仅通过已发布快照中的内嵌 Workflow 兼容运行，不再作为新草稿的正常编辑路径。
