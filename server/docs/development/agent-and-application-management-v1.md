# Agent 与 AI Application 配置管理 V1 开发设计

> 历史设计说明：本文的 Application 内嵌 Workflow 方案已被独立 Workflow 资源和
> Application 意图叶子映射取代，仅保留用于追溯迁移前设计。
> Agent 执行期合同也已由
> [Agent Runtime 内核收束设计](./agent-runtime-core-convergence-v1.md) 直接替换；本文不再作为运行时实现依据。
>
> 状态：Agent V1 已实现；Application 部分已由
> [Application Workflow 配置管理 V1](./application-workflow-management-v1.md) 更新  
> 首个场景：Application 绑定一个 RAG Agent Version 并提供 Chat  
> 核心原则：Agent 是独立能力，Application 负责选择和组合 Agent 能力

## 1. 架构结论

Agent 和 AI Application 分开管理：

```text
Agent
├── 独立配置
├── 独立调试
├── 独立版本
└── 可被多个 Application 复用

AI Application
├── 产品信息
├── 访问权限
├── Agent Version 绑定
├── Workflow
├── 会话与运行策略
├── 发布版本
├── 监控
└── 评测
```

运行关系：

```text
单 Agent Application
Application Version
→ Agent Version

多 Agent Application
Application Version
→ Workflow Definition
   ├── Agent Version A
   ├── Agent Version B
   └── Agent Version C
```

关键约束：

- Agent 不保存 `application_id`；
- Application 不复制 Agent 内部配置；
- Application Draft 绑定确定的 Agent Version；
- Workflow Agent 节点引用确定的 Agent Version；
- Agent 发布新版本不会自动改变已发布 Application；
- Application 必须创建新版本才能启用新的 Agent Version；
- V1 不允许 Application 覆盖 Agent 内部模型、Prompt、知识库和 RAG 策略。

## 2. 为什么分开

Agent 回答“这个智能能力怎么工作”：

```text
使用哪个模型
使用什么角色 Prompt
使用哪个知识库
如何加载记忆
是否改写问题
如何召回
有哪些工具
运行预算是多少
```

Application 回答“这些能力怎么形成一个产品”：

```text
应用叫什么
谁可以访问
入口使用哪个 Agent 或 Workflow
欢迎语和建议问题是什么
如何发布和回滚
会话保留多久
如何限流
如何监控和评测
```

两者生命周期不同，因此分别具有草稿和不可变版本。

## 3. V1 范围

### 3.1 必须完成

1. Agent 创建、草稿、校验、发布和版本切换；
2. Agent 配置模型、Prompt、知识库、记忆、改写、召回和生成策略；
3. Application 创建、草稿、校验、发布和版本切换；
4. Application Draft 绑定一个已发布 Agent Version；
5. 用户通过 Application Code 发起 Chat；
6. application 服务解析 Application Version 和 Agent Version；
7. 现有 RAG Pipeline 使用 Agent Version 配置；
8. 每次 Chat 保存 Application Version、Agent Version 和配置校验值；
9. Agent 新版本不影响旧 Application Version；
10. Agent、Application 的关键操作写审计。

### 3.2 V1 不实现

- 多 Agent；
- 可视化 Workflow；
- Agent Tool Calling 和 MCP；
- Agent 自主循环；
- 环境 Deployment 和灰度流量；
- Application 对 Agent 配置的节点级覆盖；
- 完整 Run Runtime；
- 完整评测中心；
- 跨 Agent 的共享记忆；
- 任意动态配置表达式。

## 4. 配置存储边界

### YAML / 环境变量

```text
数据库、Redis、对象存储地址
服务端口和连接池
加密主密钥引用
平台最大超时
平台最大 Token
平台最大模型调用次数
日志和 OpenTelemetry 地址
```

### 数据库

```text
Agent 和 Agent Version
Application 和 Application Version
模型、Prompt、知识库和索引引用
RAG 策略
Application 权限和会话策略
Workflow Definition
监控与评测配置
```

### Secret

```text
模型 API Key
MCP Token
OAuth Credential
第三方工具密钥
```

Secret 不进入 Agent 或 Application Version Snapshot。

## 5. Agent 领域模型

### 5.1 Agent

稳定资源身份：

```text
Agent
├── id
├── name
├── code
├── description
├── agentType
├── status
├── publishedVersionId
├── createdBy / updatedBy
└── createdAt / updatedAt
```

V1：

```text
agentType = RAG_CHAT
status = ACTIVE / DISABLED
```

### 5.2 AgentDraft

每个 Agent 最多一个可编辑草稿：

```text
AgentDraft
├── id
├── agentId
├── baseVersionId
├── schemaVersion
├── chatModelInstanceId
├── rewriteModelInstanceId
├── answerPromptVersionId
├── rewritePromptVersionId
├── memoryPolicy
├── rewritePolicy
├── retrievalPolicy
├── generationPolicy
├── runtimePolicy
├── revision
├── changeNote
├── createdBy / updatedBy
└── createdAt / updatedAt
```

### 5.3 AgentKnowledgeBinding

```text
agentDraftId
knowledgeBaseId
embeddingModelInstanceId
bindingMode = LATEST
priority
```

V1 一个 RAG Agent 最多绑定一个知识库，显式采用 `LATEST` 模式读取知识库当前生效的文档分块，
并冻结发布时的 Embedding 模型契约。知识库级 Index Version 建立后再增加 `FIXED` 模式，
不在当前阶段伪造运行时无法识别的索引版本。

### 5.4 AgentVersion

发布版本不可变：

```text
AgentVersion
├── id
├── agentId
├── versionNo
├── versionName
├── schemaVersion
├── definitionSnapshot
├── snapshotChecksum
├── changeNote
├── publishedBy
├── publishedAt
└── createdAt
```

`definitionSnapshot` 冻结：

```text
Agent 基本语义
模型身份
Prompt Version
Knowledge Base、Embedding Contract 与 Binding Mode
Memory Policy
Rewrite Policy
Retrieval Policy
Generation Policy
Runtime Policy
```

### 5.5 Agent 策略

#### AgentMemoryPolicy

```text
enabled
maxRounds
maxTokens
```

#### QueryRewritePolicy

```text
enabled
maxHistoryRounds
maxHistoryTokens
maxOutputTokens
maxOutputCharacters
timeoutMillis
fallbackToOriginal
```

#### RetrievalPolicy

```text
topK
similarityThreshold
maxContextTokens
emptyResultStrategy
failureStrategy
```

#### GenerationPolicy

```text
temperature
topP
maxOutputTokens
timeoutSeconds
maxRetries
```

#### AgentRuntimePolicy

```text
totalTimeoutSeconds
maxModelCalls
maxInputTokens
maxOutputTokens
```

策略全部使用强类型 Java class 和 JSONB，不使用裸 `Map<String, Object>`。

## 6. Application 领域模型

### 6.1 Application

```text
Application
├── id
├── name
├── code
├── description
├── icon
├── status
├── publishedVersionId
├── createdBy / updatedBy
└── createdAt / updatedAt
```

状态：

```text
ACTIVE
DISABLED
```

### 6.2 ApplicationDraft

```text
ApplicationDraft
├── id
├── applicationId
├── baseVersionId
├── schemaVersion
├── entryType
├── entryAgentVersionId
├── accessPolicy
├── conversationPolicy
├── runtimePolicy
├── observabilityPolicy
├── revision
├── changeNote
├── createdBy / updatedBy
└── createdAt / updatedAt
```

V1：

```text
entryType = AGENT
entryAgentVersionId = 已发布的 RAG Agent Version
```

进入 Workflow 阶段后：

```text
entryType = WORKFLOW
workflowDefinition = Application Draft 内部 Workflow
```

### 6.3 ApplicationAccessPolicy

```text
visibility
allowAnonymous
allowedUserIds
```

V1 默认：

```text
visibility = PRIVATE
allowAnonymous = false
```

### 6.4 ConversationPolicy

```text
maxConcurrentGenerationsPerConversation
retentionDays
```

Agent Memory Policy 决定模型使用多少历史；Application Conversation Policy 决定产品级会话并发和保留。

### 6.5 ApplicationRuntimePolicy

```text
requestTimeoutSeconds
requestsPerMinutePerUser
maxConcurrentRuns
```

Application 只能收紧 Agent Runtime Policy，不能放宽 Agent 自身限制：

```text
effectiveLimit = min(applicationLimit, agentLimit, platformLimit)
```

### 6.6 ObservabilityPolicy

```text
traceEnabled
contentCaptureEnabled
sampleRate
retentionDays
```

Application 不能关闭平台强制审计和安全日志。

## 7. Application Version

ApplicationVersion 冻结产品和编排配置，但不复制 Agent Definition：

```text
ApplicationVersion
├── id
├── applicationId
├── versionNo
├── versionName
├── schemaVersion
├── entryType
├── agentVersionIds
├── workflowDefinition
├── accessPolicy
├── conversationPolicy
├── runtimePolicy
├── observabilityPolicy
├── definitionSnapshot
├── snapshotChecksum
├── changeNote
├── publishedBy
├── publishedAt
└── createdAt
```

V1 `agentVersionIds` 只有一个元素。

不可变依赖图：

```text
Application Version 1
→ Agent Version 3

Agent 发布 Version 4
→ Application Version 1 仍然引用 Agent Version 3

Application 发布 Version 2
→ 显式改为 Agent Version 4
```

## 8. Workflow 演进

Workflow 属于 Application，不作为 V1 独立平台资源。

V1：

```text
Application Draft
→ Agent Version
```

Workflow V1：

```text
Application Draft
→ Workflow Draft
   ├── START
   ├── AGENT
   ├── CONDITION
   └── END
```

Workflow Agent Node：

```text
nodeId
agentVersionId
inputMapping
outputMapping
failurePolicy
timeout
```

发布 Application Version 时冻结整个 Workflow Definition 以及其中所有 Agent Version ID。

## 9. 版本与发布规则

Agent 和 Application 都采用：

```text
Resource
├── Draft
├── Version 1
├── Version 2
└── publishedVersionId
```

规则：

- Draft 可修改；
- Version 发布后不可修改；
- 修改发布配置只能编辑 Draft；
- 普通运行只使用当前 publishedVersionId；
- 调试允许指定确切 Version；
- 历史 Run 绑定确切 Version；
- 切换历史 Version 只移动 publishedVersionId；
- 已被 Application Version 引用的 Agent Version 永远不能删除。

草稿使用 `revision BIGINT` 乐观锁。

Snapshot：

```text
SHA-256(canonical definition JSON)
```

Snapshot 不包含 Secret 和健康状态。

## 10. 发布校验

### 10.1 Agent 发布

- Agent 为 ACTIVE；
- Code 全局唯一；
- Chat 模型存在、启用并支持 Streaming；
- Rewrite 开启时 Rewrite 模型和 Prompt 已配置；
- Answer Prompt Version 已发布；
- Knowledge Base 已启用并配置可用的 Embedding 模型；
- Query Embedding 与索引契约一致；
- Token、TopK、Threshold、Timeout、Retry 合法；
- Context 与 Output Token 不超过模型窗口；
- 配置不超过平台硬限制；
- 不包含 Secret；
- Draft revision 未冲突。

### 10.2 Application 发布

- Application 为 ACTIVE；
- 当前用户具有发布权限；
- V1 entryType 为 AGENT；
- entryAgentVersionId 存在且已发布；
- Agent 为 ACTIVE；
- Agent Version 配置兼容当前运行环境；
- Access、Conversation、Runtime Policy 合法；
- Application 限制没有放宽 Agent 或平台限制；
- Draft revision 未冲突。

发布事务只进行数据库操作，不调用模型、向量库或外部 HTTP。

## 11. 数据库设计草案

### 11.1 Agent

```text
ai_agent
ai_agent_draft
ai_agent_draft_knowledge
ai_agent_version
ai_agent_version_knowledge
```

#### `ai_agent`

```text
id
name
code UNIQUE
description
agent_type
status
published_version_id
created_by / updated_by
created_at / updated_at
```

#### `ai_agent_draft`

```text
id
agent_id UNIQUE
base_version_id
schema_version
chat_model_instance_id
rewrite_model_instance_id
answer_prompt_version_id
rewrite_prompt_version_id
memory_policy JSONB
rewrite_policy JSONB
retrieval_policy JSONB
generation_policy JSONB
runtime_policy JSONB
revision
change_note
created_by / updated_by
created_at / updated_at
```

#### `ai_agent_version`

```text
id
agent_id
version_no
version_name
schema_version
definition_snapshot JSONB
snapshot_checksum
change_note
published_by
published_at
created_at
```

约束：

```text
UNIQUE(agent_id, version_no)
UNIQUE(agent_id, snapshot_checksum)
```

### 11.2 Application

```text
ai_application
ai_application_draft
ai_application_version
ai_application_member
```

#### `ai_application_draft`

```text
id
application_id UNIQUE
base_version_id
schema_version
entry_type
entry_agent_version_id
access_policy JSONB
conversation_policy JSONB
runtime_policy JSONB
observability_policy JSONB
revision
change_note
created_by / updated_by
created_at / updated_at
```

#### `ai_application_version`

```text
id
application_id
version_no
version_name
schema_version
entry_type
definition_snapshot JSONB
snapshot_checksum
change_note
published_by
published_at
created_at
```

约束：

```text
UNIQUE(application_id, version_no)
UNIQUE(application_id, snapshot_checksum)
```

#### `ai_application_member`

```text
application_id
user_id
role
created_by
created_at
```

## 12. 管理端 API

### Agent

```text
GET  /api/v1/agents/list
GET  /api/v1/agents/detail/{id}
GET  /api/v1/agents/versions/{id}
GET  /api/v1/agents/version-detail/{id}/{versionId}
POST /api/v1/agents/create
POST /api/v1/agents/update/{id}
POST /api/v1/agents/save-draft/{id}
POST /api/v1/agents/validate/{id}
POST /api/v1/agents/publish/{id}
POST /api/v1/agents/activate-version/{id}/{versionId}
POST /api/v1/agents/change-status/{id}
```

### Application

```text
GET  /api/v1/applications/list
GET  /api/v1/applications/detail/{id}
GET  /api/v1/applications/versions/{id}
GET  /api/v1/applications/version-detail/{id}/{versionId}
POST /api/v1/applications/create
POST /api/v1/applications/update/{id}
POST /api/v1/applications/save-draft/{id}
POST /api/v1/applications/validate/{id}
POST /api/v1/applications/publish/{id}
POST /api/v1/applications/activate-version/{id}/{versionId}
POST /api/v1/applications/change-status/{id}
```

Application Draft 页面从已发布 Agent Version 列表中选择入口 Agent。

## 13. 用户运行入口

```json
{
  "applicationCode": "lingxi-knowledge-assistant",
  "conversationId": "10",
  "clientMessageId": "client-001",
  "query": "支持哪些文档格式？"
}
```

服务端：

```text
解析 ACTIVE Application
→ 校验用户访问权限
→ 加载 Application publishedVersionId
→ 读取 Application Version 中的 Agent Version ID
→ 加载不可变 Agent Version
→ 解析模型 Credential 等运行事实
→ 创建消息和最小 Run Snapshot
→ 执行 Chat Pipeline
```

用户不能传：

```text
agentId
agentVersionId
modelInstanceId
knowledgeBaseId
promptVersionId
topK
threshold
```

## 14. 运行时投影

```text
ApplicationRuntimeConfig
├── applicationId
├── applicationVersionId
├── applicationCode
├── snapshotChecksum
├── accessPolicy
├── conversationPolicy
├── runtimePolicy
├── observabilityPolicy
└── entryAgent
    └── AgentRuntimeConfig
        ├── agentId
        ├── agentVersionId
        ├── agentCode
        ├── snapshotChecksum
        ├── chatModelConfig
        ├── rewriteModelConfig
        ├── answerPrompt
        ├── rewritePrompt
        ├── knowledgeIndexBinding
        ├── memoryPolicy
        ├── rewritePolicy
        ├── retrievalPolicy
        ├── generationPolicy
        └── runtimePolicy
```

admin 和 application 不互相依赖业务类。application 定义自己的只读 Runtime BO。

## 15. Chat Pipeline 改造

`ModelChatContext` 增加：

```text
ApplicationRuntimeConfig applicationConfig
AgentRuntimeConfig agentConfig
```

节点消费：

| 节点 | 配置 |
| --- | --- |
| ConversationHistoryNode | Agent Memory Policy |
| QueryRewriteNode | Agent Rewrite Model、Prompt、Policy |
| KnowledgeRetrievalNode | Agent Knowledge Binding、Retrieval Policy |
| PromptBuildNode | Agent Answer Prompt |
| Streaming Generation | Agent Chat Model、Generation Policy |

Application 只负责选择 Agent Version 和施加产品级更严格限制，不改变节点内部 AI 策略。

## 16. Run 最小快照

完整 Run 域实现前，Assistant 消息增加：

```text
application_id
application_version_id
application_snapshot_checksum
agent_id
agent_version_id
agent_snapshot_checksum
```

这样可以回答：

```text
用户从哪个 Application 进入
使用哪个 Application Version
执行哪个 Agent Version
两份配置是否与发布时一致
```

## 17. 审计

Agent：

```text
AGENT_CREATE
AGENT_DRAFT_SAVE
AGENT_VALIDATE
AGENT_PUBLISH
AGENT_VERSION_ACTIVATE
AGENT_STATUS_CHANGE
```

Application：

```text
APPLICATION_CREATE
APPLICATION_DRAFT_SAVE
APPLICATION_VALIDATE
APPLICATION_PUBLISH
APPLICATION_VERSION_ACTIVATE
APPLICATION_STATUS_CHANGE
APPLICATION_MEMBER_CHANGE
```

普通日志不记录完整 Prompt、用户问题、知识库 Chunk、Definition Snapshot 和 Secret。

## 18. 测试重点

### Agent

- 草稿 revision 冲突；
- 模型和 Prompt 校验；
- 知识索引契约校验；
- 发布版本不可变；
- 相同 Snapshot 不重复发布；
- 历史 Agent Version 不受草稿修改影响。

### Application

- 只能绑定已发布 Agent Version；
- Agent Draft 不能作为入口；
- Agent 新版本不会自动影响 Application；
- Application 新版本可以显式切换 Agent Version；
- Application 运行限制不能放宽 Agent 限制；
- 非授权用户无法运行或读取配置。

### Runtime

- Application Version 精确解析 Agent Version；
- 两个 Application 可复用同一个 Agent Version；
- 两个 Application 可绑定同一 Agent 的不同 Version；
- 历史 Run 可还原两层版本；
- 用户请求不能覆盖 Agent 配置。

## 19. 开发顺序

### Sprint 1：Agent 控制面

```text
Agent
→ Draft
→ 类型化策略
→ 校验
→ 发布 Agent Version
→ 版本切换
→ 独立调试
```

### Sprint 2：Application 控制面

```text
Application
→ Draft
→ 选择 Agent Version
→ 权限和产品策略
→ 发布 Application Version
→ 版本切换
```

### Sprint 3：运行面

```text
Application Code
→ Application Version
→ Agent Version
→ Chat Pipeline
→ 保存两层 Run Snapshot
```

### Sprint 4：Query Rewrite

```text
非流式 Gateway
→ QueryRewriteService
→ QueryRewriteNode
→ Agent Rewrite Policy
→ Trace
```

### Sprint 5：Workflow 与 Multi-Agent

```text
Application Workflow Draft
→ Workflow Node 引用 Agent Version
→ 服务端编译
→ Application Version 冻结 Workflow
→ Workflow Runtime
→ Multi-Agent
```

### Sprint 6：监控与评测

```text
Application Run
→ Agent Run / Node Run
→ Model Call / Retrieval Hit
→ Dataset
→ Evaluation
→ Version 对比
→ 发布门禁
```

## 20. Sprint 0 评审清单

- [ ] Agent 独立管理；
- [ ] Agent 有独立 Draft 和 Version；
- [ ] Application 独立管理；
- [ ] Application 有独立 Draft 和 Version；
- [ ] Application 绑定确定 Agent Version；
- [ ] Agent 不保存 applicationId；
- [ ] Application 不复制 Agent Definition；
- [ ] V1 Application 只绑定一个 RAG Agent Version；
- [ ] Workflow 属于 Application；
- [ ] Workflow 节点引用确定 Agent Version；
- [ ] Agent 新版本不自动影响已发布 Application；
- [ ] Run 同时记录 Application Version 和 Agent Version；
- [ ] Application 不能放宽 Agent 和平台限制；
- [ ] 用户不能覆盖已发布 Agent 配置。

## 21. 当前前置缺口

1. 知识库尚无聚合级 Index Version，V1 明确使用 `LATEST` 模式；需要严格复现检索数据时再增加 `FIXED` 模式；
2. ChatRequest 仍直接包含 Knowledge Base ID，运行面需要移除；
3. application 仍读取默认 Chat 模型，需要改为 Agent Version Runtime Config；
4. 完整 Run 域未实现，第一版暂存在 Assistant 消息；
5. Mockito 在本机 JDK 21 下存在动态 Agent 挂载限制，实施阶段需要修复。
