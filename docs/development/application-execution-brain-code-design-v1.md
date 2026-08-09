# Application 执行大脑 V1 代码设计

> 状态：目标代码设计
> 更新时间：2026-08-08
> 关联计划：`docs/development/application-execution-brain-v1.md`

## 1. 模块边界

```text
framework
  通用响应、异常、认证、数据库和基础工程能力

infra-ai
  模型、Embedding、Tool、Prompt 变量等跨服务稳定契约

workflow-core
  Application 发布快照、能力目录、Workflow 定义和执行原语

admin
  草稿维护、资源校验、版本锁定、发布和历史版本管理

application
  上下文准备、快速路由、Planner、能力执行、交互会话和运行记录
```

`admin` 与 `application` 不共享 Entity、Repo、Mapper 或 Service。两个服务只通过数据库中
的已发布 JSONB 契约和 `infra-ai`、`workflow-core` 中的稳定模型协作。

## 2. 发布快照

`ApplicationDefinition` 是运行时唯一配置入口：

```text
ApplicationDefinition
├── schemaVersion
├── defaultAgent
│   ├── agentId
│   └── agentVersionId
├── optionalFastRoutes
├── contextPolicy
│   ├── contextualization
│   └── budget
├── memory
│   ├── profile
│   ├── interactionRecord
│   └── summary
├── plannerPolicy
├── capabilityCatalog
│   └── capabilities[]
└── runtimePolicy
```

发布时锁定所有资源版本。运行时不得读取草稿，也不得根据资源当前生效版本重新解析目标。
所有引用使用逻辑外键，数据库不创建物理外键；服务层校验存在性、Project Scope、归属、
状态和生命周期，引用列建立必要索引。

## 3. 主调用链

```text
ChatStreamServiceImpl
-> ApplicationRuntimeService.resolvePublished
-> ApplicationChatExecutionService.execute
   -> ConversationContextService.prepare
   -> PlannerRuntimeCoordinator.resolveInteraction
   -> FastRouteService.route（可选）
   -> PlannerRuntimeCoordinator.execute 或默认 ReAct Agent
      -> PlannerService.decide
      -> ExecutionDecisionParser.parse
      -> ExecutionDecisionValidator.validate
      -> PlanExecutionService.execute
         -> CapabilityExecutionService.execute
         -> FinalSynthesisService.synthesize
-> 保存 Assistant 消息和 Token 用量
-> 发布 SSE 终态
```

`ApplicationChatExecutionServiceImpl` 是执行大脑的用例编排入口，不负责模型协议解析、
资源查询细节或 SQL。当前公开方法参数超过五个，应收敛为具名
`ApplicationChatExecutionRequest`，避免调用方参数错位。

## 4. 上下文设计

`ConversationContextServiceImpl.prepare` 按固定顺序执行：

1. 读取 ApplicationVersion 中的 Context 与 Memory 配置。
2. 校验用户输入 Token 上限。
3. 加载最近历史和可选会话摘要。
4. 在剩余记忆预算内加载画像与相关交互记录。
5. 初始化用户、会话、时间、历史、摘要和长期记忆运行时变量。
6. 生成 `contextualizedQuery`，仅供快速路由和 Planner 使用。
7. 返回不可跨用户复用的 `ConversationContext`。

核心字段：

```text
originalQuery          原始问题，用于回答、审计和持久化
contextualizedQuery    上下文化问题，用于路由与规划
recentMessages         有界历史窗口
summary                有界会话摘要
longTermMemory         画像与相关交互记录的有界组合
runtimeVariables       本轮统一变量容器
budget                 各上下文分区预算
preparationTokenUsage  上下文化模型消耗
```

知识库内容不属于预加载上下文。AgentVersion 可以冻结 `KnowledgeSearchCapability`，
`KnowledgeSearchAgentToolFactory` 据此创建 `knowledge_search` Tool，模型需要时才调用。

## 5. 快速路由设计

意图树只提供可选快速路由。目标代码将 `IntentRoutingService` 收口为 `FastRouteService`，
并使用显式枚举表达所有状态，禁止用 `null` 表示未配置或未命中：

```text
SKIPPED
NO_MATCH
FIXED_RESPONSE
CLARIFY
EXECUTE_AGENT
EXECUTE_WORKFLOW
```

优先级固定为：

```text
恢复待处理交互
-> 快速路由固定回复/澄清/执行目标
-> 快速路由未命中
-> Planner（启用时）
-> 默认 ReAct Agent（Planner 未启用时）
```

快速路由不能选择 Tool、Plan 或 Supervisor 成员；Supervisor 作为锁定 AgentVersion 执行。

## 6. Planner 决策契约

`ExecutionDecision` 使用枚举 `ExecutionDecisionAction`：

```text
EXECUTE_REACT     capabilityCode 必填，目标必须是 REACT_AGENT
EXECUTE_WORKFLOW  capabilityCode 必填，目标必须是 WORKFLOW
EXECUTE_PLAN      plan 必填，顶层 capabilityCode 为空
CLARIFY           clarification 必填，不允许携带 plan
```

Planner 看到的是裁剪后的 `PlannerCapabilityDescriptor`，只包含编码、类型、名称、描述、
输入输出 Schema 和风险等级。以下字段永不暴露给模型决定：

- `targetId`、`targetVersionId`；
- `implementationType`；
- `configurationChecksum`；
- 超时、调用次数和结果 Token 上限；
- 权限、Project Scope 和用户身份判断结果。

`ExecutionDecisionParser` 负责 JSON 语法和字段协议；`ExecutionDecisionValidator` 负责业务门禁。
未知动作、未知能力、类型不匹配、重复步骤、向后依赖、越界字面量和未确认风险都必须拒绝。

## 7. Plan-and-Execute

V1 计划是有限串行步骤列表：

```text
ExecutionPlan
├── title
└── steps[]
    ├── id
    ├── name
    ├── capabilityCode
    ├── dependsOn[]
    └── inputs[]
        ├── name
        ├── source = USER_QUERY / LITERAL / STEP_OUTPUT
        ├── stepId
        └── value
```

执行规则：

1. 步骤数量不超过 `plannerPolicy.maxSteps`。
2. `STEP_OUTPUT` 只能引用已完成且显式声明依赖的步骤。
3. 单项能力调用不超过能力自己的 `maxCalls`。
4. 全局模型和 Tool 调用不超过 Planner 与 Runtime 双重预算。
5. 非只读能力完成后不得因重规划重复执行。
6. 步骤失败只把安全错误码和有界观察交给 Planner，不传递敏感参数或完整异常。
7. 重规划不超过 `maxReplans`，达到上限后 Run 进入失败终态。
8. 全部步骤成功后由 `FinalSynthesisService` 基于有界步骤结果生成最终回答。

## 8. 能力执行

`ApplicationCapabilityType` 固定为：

```text
REACT_AGENT
WORKFLOW
TOOL
```

`CapabilityExecutionServiceImpl` 在执行前完成：

- 根据能力编码从发布快照取回服务端可信定义；
- 校验目标和版本非空；
- 重新读取目标资源并比对配置校验和；
- 校验超时、调用次数、结果 Token 和风险确认；
- 根据枚举分发到 Agent、Workflow 或 Tool；
- 将安全状态发布为 SSE，并返回统一 `CapabilityExecutionResult`。

ReAct Agent 通过 `AgentChatPreparationStrategyRegistry` 按共享 `AgentType` 选择准备策略。
Supervisor 使用成员 Agent Tool 和 `collaboration.max_delegations` 做有界动态委派，但不生成
可持久化显式计划。需要审计步骤、依赖和重规划的任务必须使用 Plan-and-Execute。

## 9. 交互会话

`InteractionSessionService` 统一承载 Planner 澄清和风险确认：

```text
PENDING
-> RESOLVED / CANCELLED / EXPIRED
-> EXECUTING
-> COMPLETED / FAILED
```

会话必须锁定用户、ApplicationVersion、Run、原始问题、交互类型和允许确认的能力编码。
恢复请求时先校验所有权、状态和过期时间，再将补充文本合并到 `routingQuery`。普通澄清不能
伪造风险确认；确认范围只能覆盖服务端已记录的能力编码。

## 10. 持久化与事务

核心表：

```text
execution_run
execution_plan_snapshot
execution_step_attempt
execution_event
interaction_session
```

Run、Plan、Attempt 和 Event 采用追加写，已发生事实不得覆盖。每个公开持久化用例使用短事务；
模型、Agent、Workflow 和 Tool 调用必须在事务外执行。写入命令携带 `runId`、`planNo`、
`stepId`、`attemptNo` 等幂等键，数据库建立唯一约束，重试不能产生重复事实。

## 11. SSE 事件

对外事件至少包含：

```text
PLANNING
PLAN_CREATED
STEP_STARTED
STEP_COMPLETED
STEP_FAILED
REPLANNING
CLARIFICATION_REQUIRED
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
```

Tool 与 Agent 委派沿用各自安全事件。事件只返回显示名、状态、耗时、错误码和资源标识，
不得返回完整 Prompt、模型思维过程、Tool 参数、原始结果、密钥或私有用户数据。

## 12. 管理端设计约束

Application 编辑器使用分步配置：

1. 基础信息；
2. 默认 Agent 与协作；
3. 能力目录；
4. 上下文与三类记忆；
5. Planner 策略；
6. 可选快速路由；
7. 校验与发布。

每步只展示当前配置需要的字段。长 Prompt、Schema 和描述使用专用编辑区域，普通表单输入设置
合理最大宽度。非必要说明统一放在可键盘聚焦的信息图标 Tooltip 中；错误、风险、发布状态和
必填说明保持可见。版本详情复用同一布局并整体只读。

## 13. 测试设计

### 单元测试

- 上下文预算、历史裁剪、摘要和长期记忆组合；
- 快速路由各动作及未配置状态；
- Planner JSON 严格解析和所有非法动作组合；
- 能力类型、计划依赖、步骤输入、风险确认和预算门禁；
- 能力校验和、锁定版本、超时和结果 Token 限制；
- 重规划上限、写能力去重和最终合成。

### 集成测试

- Application 草稿校验、发布和历史版本激活；
- 发布快照在 `admin` 写入后可被 `application` 严格读取；
- Run、Plan、Attempt、Event 和 InteractionSession 的幂等持久化；
- ReAct Agent、Workflow、Tool、澄清恢复和取消完整链路；
- Project Scope、资源归属和跨用户访问拒绝。

### 前端验收

- 创建、保存、刷新恢复、校验、发布和历史版本回看；
- Planner 开关、模型与 Prompt 选择、能力目录和预算联动校验；
- 桌面与窄屏布局无溢出、遮挡和异常超长输入框；
- Chat 时间线正确展示规划、执行、澄清和终态。

## 14. 实施顺序

```text
发布契约冻结
-> 上下文与记忆闭环
-> 快速路由显式状态收口
-> Planner 决策门禁
-> 能力执行与重规划
-> 持久化和 SSE
-> 管理端配置
-> 全链路验收
```

每个阶段必须先通过对应单元测试再接入下一阶段。Maven 编译成功不等于完成；发布、运行、
澄清、失败恢复和历史版本复现均通过后，才能将执行大脑标记为可用。
