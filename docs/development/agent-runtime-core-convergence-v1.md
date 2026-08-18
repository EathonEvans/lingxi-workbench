# Agent Runtime 内核收束设计

> 状态：已确认，待按批次实施
> 日期：2026-08-17
> 适用仓库：`lingxi-workbench-server`，同时考虑当前 Admin、Web 的未提交状态
> 目标：借鉴 Spring AI Alibaba Agent Framework 的职责划分，但不引入其依赖；以 Spring AI 2.0 作为模型交互基座
> 实施计划：[Agent Runtime 内核收束开发计划](agent-runtime-core-convergence-development-plan-v1.md)

## 1. 结论

本阶段不并存两个 Maven 模块，将现有 `agent-execute` 原地更名为 `agent-runtime`，
并收束为 Lingxi Agent Runtime 内核。目录、`artifactId` 和 Java 根包同批从
`agent-execute` / `com.lingxi.workbench.agentexecute` 一次性改为
`agent-runtime` / `com.lingxi.workbench.agentruntime`，不保留旧模块或旧包名兼容层。
它不再只是 Spring AI 网关和 Tool Loop，而是统一承载：

- Agent 生命周期和唯一执行入口；
- `ExecutionState`、预算、截止时间、取消、迭代和 Token 事实；
- 统一 `ExecutionEvent`；
- ReAct、Supervisor、Plan-and-Execute；
- Agent Hook、Spring AI Advisor 与受控 `ToolCallback`；
- Skill 激活、Tool 调用、Memory 注入和 HITL 中断/恢复语义。

Application 不再拥有 Agent 的模型循环和计划循环。最终只保留：

1. 加载并校验已发布的 ApplicationVersion、AgentVersion、PromptVersion、SkillVersion、Tool 与 WorkflowVersion；
2. 准备用户、会话、历史、摘要、长期记忆和运行时变量等可信入口上下文；
3. 根据 Application 意图叶子调用固定回复、Agent Runtime 或 Workflow Runtime；
4. 将 Runtime 事件持久化后转发到 SSE；
5. 持久化 Run、Checkpoint、Interaction、消息结果及异步 Memory 任务。

不保留旧运行时 DTO、状态、事件或异常控制流的兼容读取。一个能力迁入内核后，同一合并结果中删除旧路径及其调用方。

## 2. 当前代码与工作区事实

### 2.1 三个仓库

| 仓库                      | 当前状态                                                         | 与本设计的关系                                        |
| ------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------- |
| `lingxi-workbench-server` | `main`，存在 Skill V8、Spring AI `ChatClient` 等未提交变更       | 本设计必须以这些变更为现状，不覆盖、不回退            |
| `lingxi-workbench-admin`  | `main`，存在登录重定向修复、Skill 管理、Agent Skill 绑定 UI 变更 | 控制面契约继续保留；实施期只在 Runtime 契约变化时同步 |
| `lingxi-workbench-web`    | `main`，工作区干净                                               | 已有计划、Tool、HITL 展示协议需要在事件收束时回归     |

当前 Server 未提交变更中的 Skill 事实包括：发布时锁定 `SkillVersion`、Agent V8 绑定、
`ALWAYS_ON` 激活、有效 Tool 交集和 `ai_execution_run_skill` 审计。当前 `EXPLICIT_ONLY`、
`MODEL_ROUTED` 尚未形成完整运行闭环，不能把枚举存在视为已实现。

当前 `agent-execute` 的 `ChatClient` 改造只覆盖非流式 Completion 网关；流式路径仍直接调用
`ChatModel.stream()`。但项目已经使用 Spring AI 2.0.0 的 `ToolCallingManager`、`ToolCallback`、
`FunctionToolCallback`、`SyncMcpToolCallback`、Token 估算和 Observation，收束时应继续向 Spring AI 原生能力收口，
而不是另造一套 Model/Tool SDK。

### 2.2 当前职责倒置

`agent-execute` 生产代码目前只有模型网关、`AgentLoopState`、Tool 账本和有界回调等少量类；
真正的 Agent Runtime 主要位于 Application：

- `ApplicationChatExecutionServiceImpl`：约 600 行，拥有 Run 生命周期、上下文注册、调度和终态；
- `AgentChatServiceImpl`：约 423 行，拥有 Skill 注入、Tool 装配、模型配置和流式生成；
- `CapabilityExecutionServiceImpl`：约 443 行，混合 Agent、Workflow、Tool 三种执行边界；
- `PlanExecutionServiceImpl`：约 720 行，拥有计划、步骤、重规划、终态和事件双写；
- `PlannerServiceImpl`：约 524 行，拥有 Planner Prompt、模型调用、解析和校验；
- `ChatExecutionContext`：约 442 行，承担预算、Token、截止时间、事件、会话、Memory 和取消。

这造成五个直接问题：

1. Application 同时是产品入口、Agent Runtime、Workflow 适配器和持久化服务；
2. `ChatExecutionContext` 与 `AgentLoopState` 分别维护运行级和 ReAct 级计数，收敛条件分散；
3. `ExecutionEventType`、`AgentProgressEvent`、`AgentToolExecutionEvent`、Workflow Event、Chat BO 并存；
4. HITL 通过 `HumanInteractionRequiredException` 穿透 Reactor、Runtime 和 Application，暂停被表达成异常；
5. Workflow 通过进程内 `ChatExecutionContextRegistry` 找回上下文，无法自然支持跨进程恢复。

## 3. 借鉴 Spring AI Alibaba，但不照搬

参考其公开 Agent Framework 与 Graph 设计，只采用以下思想：

- Agent 是可调用、可流式、可组合的独立运行单元；Graph/Workflow 是另一种控制流；
- 状态贯穿模型、Tool、Hook 和中断恢复，Checkpoint 是状态的持久化快照；
- Hook 位于 Agent 生命周期，允许中断和恢复；单次 Model/Tool 拦截职责分别映射为
  Spring AI Advisor 与 `ToolCallback` 装饰，不负责暂停；
- HITL 必须和 Checkpoint 一起设计，恢复同一状态而不是重新路由；
- Supervisor 是集中式循环调度，子 Agent 只返回任务结果，不直接污染最终用户回答；
- Skill 应支持按需披露和按需激活，Skill 的 Tool 声明不能扩大授权；
- Workflow、Agent、Tool 是可组合能力，但不能共享一套含糊的状态机。

Lingxi 不采用以下内容：

- 不引入 `spring-ai-alibaba-agent-framework` 或 Graph 依赖；
- 不复制 `Agent`、`BaseAgent`、`ReactAgent`、`OverAllState`、`RunnableConfig`；
- 不把稳定结构降级为 `Map<String, Object>`；
- 不使用 Redis 作为执行、Checkpoint、会话摘要、用户画像或交互记录的事实源；
- 不为了形式完整一次性创建 Sequential/Parallel/Handoff 等当前没有业务闭环的抽象。

### 3.1 Spring AI First 原则

Spring AI Alibaba 只提供 Agent/Graph 架构思想；代码层面优先采用项目已锁定的 Spring AI 2.0.0。
边界不是“Spring AI 只当模型 SDK”，而是：

- Spring AI 负责 `ChatClient`、Message/Prompt、Advisor 链、结构化输出、Tool 定义与执行协议、MCP 适配、
  Embedding 和 AI Observation；
- Lingxi Runtime 负责已发布版本绑定、Agent 生命周期、`ExecutionState`、迭代与总预算、权限、HITL、
  Checkpoint、幂等、副作用对账和统一事件；
- 已有 Spring AI 抽象能完整表达的职责不再建 Lingxi 并行抽象；Spring AI 无法表达的业务事实
  也不强行塞进 Advisor、`ChatMemory` 或 `ToolCallingAdvisor`。

### 3.2 Spring AI 能力采用矩阵

| Spring AI 2.0 能力                                              | 决策                | Lingxi 用法与边界                                                                                                 |
| --------------------------------------------------------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `ChatClient.call/stream`                                        | 全面采用            | 非流式与流式调用统一进 `ChatClient`，不再一条用 `ChatClient`、一条直调 `ChatModel`                                |
| Message、Prompt、Model Options                                  | 直接采用            | 作为单次模型调用协议；发布 Prompt 和 Runtime 可信变量仍由 Lingxi 校验                                             |
| `CallAdvisor` / `StreamAdvisor`                                 | 全面采用            | 替代自建 `ModelInterceptor`，用于模型次数/预算记账、事件、可信上下文注入和错误映射                                |
| `ToolCallback`、`ToolDefinition`、`ToolMetadata`、`ToolContext` | 全面采用            | Tool Schema、调用协议和可信执行上下文不自研；`ToolContext` 只放服务端生成的标识                                   |
| `ToolCallingManager`                                            | 全面采用            | 执行已授权且已通过 HITL 的 Tool Call，并生成标准 Tool Response Message                                            |
| `ToolCallingAdvisor`                                            | 不接管 Agent 主循环 | Agent 每轮必须更新 State/Event/Checkpoint，因此使用官方 user-controlled 模式；无 Tool 任务不注册它                |
| `FunctionToolCallback` / `MethodToolCallback`                   | 采用                | 内置、知识检索等本地 Tool 优先由 Spring AI 生成 Schema 与参数转换                                                 |
| Spring AI MCP Callback/Provider                                 | 采用                | 保留 Lingxi 动态短连接、别名、密钥与版本边界，底层委托 `SyncMcpToolCallback`                                      |
| typed `entity/responseEntity`、`BeanOutputConverter`            | 全面采用            | Planner/路由等结构化任务不再以 `JSONObject` 作 Runtime 协议                                                       |
| provider structured output                                      | 按模型能力采用      | 供应商确实支持时使用 `useProviderStructuredOutput()`，否则使用 Schema 提示与转换                                  |
| `StructuredOutputValidationAdvisor`                             | 受控采用            | Planner 做 JSON Schema 校验与有界自修复；每次内部重试必须经记账 Advisor 消耗 Runtime 预算                         |
| `ChatMemory` / `MessageChatMemoryAdvisor`                       | 暂不接管会话事实    | 当前 PostgreSQL 消息、滚动摘要和 Checkpoint 语义已明确，且官方 Advisor 暂不保存 Tool 中间消息；避免双写与恢复缺口 |
| `EmbeddingModel`                                                | 继续采用            | 保留当前动态模型配置与维度/索引版本校验                                                                           |
| `VectorStore` / RAG Advisor                                     | 本次不引入 Runtime  | 知识检索是显式 Tool，必须锁定 KnowledgeBase/IndexVersion、保留来源和授权；不做无条件 Prompt 增强                  |
| Spring AI Observation                                           | 全面采用            | 复用 ChatClient/Advisor/ChatModel/Embedding/Tool span；Lingxi 只补 Run/Agent/Plan 父级观测与业务事件              |
| Tool Search Advisor                                             | 达到准入条件后采用  | 仅在授权与 Skill 过滤后的 Tool 目录过大时开启，不得反向扩大可见 Tool 集                                           |

不引入 Spring AI Alibaba 依赖不等于回避 Spring AI。新增模型、Tool、MCP、Embedding 代码必须先证明
Spring AI 现有能力无法承载，才能创建项目自有底层抽象。

官方参考：

- Agent Framework 与 Graph 分层：<https://github.com/alibaba/spring-ai-alibaba>
- Hook 与 Interceptor 边界：<https://java2ai.com/en/docs/frameworks/agent-framework/tutorials/hooks/>
- HITL 与 Checkpoint：<https://java2ai.com/en/docs/frameworks/agent-framework/advanced/human-in-the-loop/>
- Skill 渐进式披露：<https://java2ai.com/docs/frameworks/agent-framework/tutorials/skills/>
- Multi-agent 与 Supervisor：<https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent/>
- Spring AI ChatClient 与 Advisor：<https://docs.spring.io/spring-ai/reference/api/chatclient.html>
- Spring AI Tool Calling：<https://docs.spring.io/spring-ai/reference/api/tools.html>
- Spring AI 结构化输出：<https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html>
- Spring AI Chat Memory：<https://docs.spring.io/spring-ai/reference/api/chat-memory.html>
- Spring AI MCP 适配：<https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html>
- Spring AI Observability：<https://docs.spring.io/spring-ai/reference/observability/index.html>

## 4. 目标模块边界

```text
framework
   ↑
infra-ai                 workflow-core
   ↑                           ↑
   └──────── agent-runtime ─────┘
                    ↑
               application
```

### 4.1 `infra-ai`

只保留跨模块稳定的 AI 与 Tool 契约、供应商无关 Gateway、控制面与运行面共同使用的发布协议。
它不保存 Agent 生命周期、计数器、HITL 控制流或执行事件。

### 4.2 `workflow-core`

保留 Workflow Definition、校验器、执行请求/结果和 `WorkflowExecutor`/节点调用端口。
Workflow 的图状态、节点状态、Run 和 Event 独立于 Agent `ExecutionState`。

为了让 Application 只做适配，现位于 Application 的 Workflow 执行协议应移到 `workflow-core`；
`LangGraphWorkflowExecutor` 也迁入该模块，LangGraph4j 仍然只出现在这一实现中。
Workflow 数据库 Entity、Repo、Mapper 和运行记录监听器继续留在 Application。

### 4.3 `agent-runtime`

依赖方向只允许 `agent-runtime -> workflow-core`，不允许反向依赖。该模块提供唯一的
`AgentRuntime` 入口和必要的扩展端口，内部直接复用现有 Gateway、Tool Loop 和账本。

### 4.4 `application`

Application 对 Runtime 暴露四类适配能力：

- `AgentVersionLoader`：加载已发布且已校验的完整 Agent 快照；
- `RuntimeEventSink`：先持久化 durable event，再转发 SSE；
- `RuntimeCheckpointStore`：短事务保存/消费 Checkpoint 和 Interaction；
- `RuntimeCapabilityInvoker`：调用 Application 已锁定的 Workflow 或平台 Tool。

这些是跨模块真实边界，允许使用接口；其他单实现内部类不机械创建接口。

## 5. 唯一运行入口与生命周期

### 5.1 对外 API

`agent-runtime` 最终只暴露一个运行门面：

```java
AgentRunResult run(AgentRunRequest request);
AgentRunResult resume(AgentResumeRequest request);
```

`AgentRuntime` 使用具体类，不再建立 `AgentRuntime`/`AgentRuntimeImpl` 的一接口一实现。
请求携带已经锁定的 Agent 快照、可信上下文、能力目录、运行策略、事件与 Checkpoint 端口；
不得携带 Application Entity、Repo、HTTP Request、SSE Publisher 或登录线程上下文。

结果只有三种明确语义：

- `Completed`：完整成功；
- `Partial`：时间或预算到界，但存在经过校验的可用结果；
- `Interrupted`：等待人工输入，包含不可伪造的 `interactionId` 和 Checkpoint 引用。

失败继续抛稳定的 Runtime 异常，由 Application 统一映射并落库。HITL 不再使用异常表示正常暂停。

`AgentRuntime` 不暴露 Spring AI 对象，但内部每次 Model Invocation 统一通过 `ChatClient`。
`ChatClient` 是模型调用组合根，`ExecutionState` 是 Agent 运行事实根，两者不互相替代。

### 5.2 生命周期

```text
load published versions
-> create ExecutionState
-> beforeAgent hooks
-> select AgentExecutor by AgentRole
   -> SPECIALIST: model <-> tool ReAct loop
   -> SUPERVISOR: plan -> capability -> observe -> replan/finalize
-> afterAgent hooks
-> Completed / Partial / Interrupted / Failed
```

Run 状态统一为：

```text
RUNNING
  ├─> WAITING_HUMAN -> RESUMING -> RUNNING
  ├─> SUCCEEDED
  ├─> PARTIAL
  ├─> FAILED
  ├─> CANCELLED
  ├─> EXPIRED
  └─> RECONCILIATION_PENDING
```

- `PARTIAL` 专门表达 `TIME_LIMIT_PARTIAL`、Token 边界或其他有可用结果的收口；
- `RECONCILIATION_PENDING` 表达外部副作用已经成功、但本地确认或持久化失败；
- 删除含义模糊的 `INTERRUPTED`，人工暂停使用 `WAITING_HUMAN`；
- Run 从 Application 原子创建后直接进入 `RUNNING`，不增加无实际流程的 `CREATED`。

## 6. ExecutionState

`ExecutionState` 是一次 Run 唯一的可变运行事实，替代 `ChatExecutionContext` 与分散的计划局部变量。
它是普通 `class`，通过 Builder 创建，不使用 record，不暴露任意键写入。

状态分为五组：

| 组       | 内容                                                                              |
| -------- | --------------------------------------------------------------------------------- |
| Identity | runId、Application/Agent 版本引用、用户/会话/消息 ID、当前 Agent 调用栈           |
| Context  | 原始问题、当前任务问题、历史、摘要、长期记忆、受控运行时变量                      |
| Control  | Run 状态、当前阶段、planNo、stepId、iterationNo、取消和 HITL 状态                 |
| Budget   | 模型、Tool、委派、重规划、时间、工作 Token 与最终收口 Token 的已用/剩余值         |
| Facts    | 模型消息、Tool 结果账本、已激活 Skill、计划与已完成步骤、TokenUsage、最后输出时间 |

约束：

1. 所有计数只能通过 `ExecutionState` 的具名占用方法更新；
2. `AgentLoopState` 不再持有另一套全局预算，只保留单个 Agent 调用的 `AgentIterationState`；
3. 子 Agent/计划步骤创建 `AgentInvocationState`，共享 Run 预算和事件序号，但隔离 query、消息与文本输出；
4. Tool 只能读取显式的 `ToolExecutionContext`，不能拿到可任意修改的整个状态；
5. Checkpoint 保存可恢复事实，不保存 Spring Bean、Reactor 对象、回调、线程或明文密钥；
6. Runtime State 不等同于 Conversation Memory；Memory 只是其输入和输出事实之一。

### 6.1 迭代控制

不新建只有计数方法的 `IterationController`。迭代规则由不可变 `IterationPolicy` 描述，
计数和状态转换由 `ExecutionState` 执行：

- `beforeModelCall()`：校验硬截止、工作 Token 和模型调用额度；
- `beforeToolCalls(count)`：同时校验 Run、当前 Agent、单 Tool、单轮 Tool 上限；
- `beforeDelegation()`：校验 Supervisor 委派和调用栈，不允许递归 Supervisor；
- `beforeReplan()`：校验重规划次数和剩余最终回答容量；
- `requestFinalization(reason)`：只记录首个收口原因，后续禁止 Tool、委派和重规划；
- `beforeFinalModelCall()`：只允许使用预留的最终模型与 Token 额度。

当前 `AgentToolExecutionLedger` 的确定性只读 Tool 复用语义保留，并成为 `AgentInvocationState` 的组成部分。

## 7. ExecutionEvent

Runtime 只发布一种 `ExecutionEvent`。事件具有固定 envelope：

- `runId`、`sequenceNo`、`occurredAt`；
- `type`、`stage`；
- 可选 `agentId/agentVersionId`、`planNo/stepId`、`invocationId`；
- 一个与 `type` 匹配的强类型 payload。

事件类型收束为：

```text
RUN_STARTED
AGENT_STARTED / AGENT_COMPLETED
MODEL_STARTED / MODEL_COMPLETED
SKILL_ACTIVATED
TOOL_STARTED / TOOL_REUSED / TOOL_COMPLETED / TOOL_FAILED
PLAN_CREATED / REPLANNING
STEP_STARTED / STEP_COMPLETED / STEP_FAILED / STEP_SKIPPED
HUMAN_REQUIRED / RUN_RESUMED
FINALIZING
RUN_COMPLETED / RUN_PARTIAL / RUN_FAILED / RUN_CANCELLED
```

规则：

1. 文本增量仍走消息生成事件，不把每个 token chunk 落为 `ExecutionEvent`；
2. Runtime 产生单调 `sequenceNo`，Application 以 `(run_id, sequence_no)` 唯一约束幂等落库；
3. Application 的 Event Sink 先落库 durable event，再转成 Chat/SSE VO；
4. Event 不携带模型思维链、完整 Prompt、Tool 参数、Tool 原始结果或隐私数据；
5. Workflow Event 继续使用独立 `WorkflowRunEventType`，仅在 Agent Runtime 边界投影为能力步骤事件；
6. `AgentProgressEvent`、`AgentToolExecutionEvent`、`ChatExecutionTimelineEventBO` 不再作为 Runtime 内部协议。

## 8. Hook、Advisor 与 Tool Callback

### 8.1 Hook

Hook 观察或改变 Agent 生命周期，可以返回 `CONTINUE`、`FINISH` 或 `INTERRUPT`。首期只保留一个
`AgentHook` 扩展接口和显式装配顺序，不为每个阶段创建接口。

生命周期点：

```text
BEFORE_AGENT / AFTER_AGENT
BEFORE_TOOL / AFTER_TOOL
```

首批有真实职责的 Hook：

- `SkillHook`：按激活模式选出 Skill 指令和有效 Tool，写入 State；
- `HumanApprovalHook`：高风险 Tool 前创建 Checkpoint 并返回 `INTERRUPT`；
- `FinalizationHook`：预算进入软边界后禁止探索并切到最终回答。

预算占用和事件序号属于 `ExecutionState` 的强制规则，不做成可被遗漏的 Hook。

### 8.2 Spring AI Advisor

不新建通用 `ModelInterceptor` 体系。单次模型调用的拦截、增强和观测统一使用 Spring AI
`CallAdvisor` / `StreamAdvisor`：

- `RuntimeModelAdvisor`：从 Advisor context 取得当前 `ExecutionState` 引用，每次真实模型请求前占用额度并
  发布 `MODEL_STARTED`，响应后记录累计 Usage 和 `MODEL_COMPLETED`，统一映射供应商错误；
- `RuntimeContextAdvisor`：只把 Hook 已决定的有界 Memory、已激活 Skill 指令和可信 Runtime 变量注入
  Model Invocation，不自行做权限判定或 Skill 激活；
- `StructuredOutputValidationAdvisor`：只挂载在 Planner/路由等完整响应任务，不用于流式文本。

Advisor 顺序必须是显式常量并有单测。`RuntimeModelAdvisor` 必须位于可递归的结构化校验内层，
确保每一次自修复重试都占用模型次数并被 Observation 覆盖，不出现“Spring AI 内部额外调用但
`ExecutionState` 无感知”。

### 8.3 Tool Callback 装饰

不新建通用 `ToolInterceptor` 链。保留一个具体的 `RuntimeToolCallback implements ToolCallback`，装饰已授权
Tool，统一处理参数二次校验、幂等账本、超时、结果大小和业务事件；Schema、参数转换、
Tool Message 与 Tool Observation 仍由 Spring AI 处理。HITL 在调用 `ToolCallingManager` 前由 Hook 决定，
Callback 内不用异常表示暂停。

Tool 重试只能用于明确可重试且无副作用或具备幂等键的调用。外部副作用发生后，本地确认失败必须进入
`RECONCILIATION_PENDING`，不能包装为普通 `FAILED` 后自动重放。Spring AI 的供应商重试只处理模型传输瞬时故障，
不代替 Runtime 语义重试。

## 9. Skill、Tool、Memory 与 HITL

### 9.1 Skill

Skill 激活属于 Agent Runtime，Skill 的版本加载、状态/安全复核属于 Application Loader。

```text
AgentVersion frozen binding
-> Application validates SkillVersion and effectiveToolCodes
-> SkillHook selects activation
-> ExecutionState records activation once
-> Skill instruction enters model context
-> only effectiveToolCodes may become visible
```

- `ALWAYS_ON`：进入 Agent 时激活；
- `EXPLICIT_ONLY`：只接受服务端已验证的显式 Skill code；
- `MODEL_ROUTED`：先只披露 name/description，模型选择后再加载完整指令；
- `requestedToolCodes` 永远只是依赖声明；有效 Tool 是 Skill 请求、AgentVersion 白名单、
  Application/用户权限、平台策略和当前可用状态的交集；
- 同一 Run 使用冻结 SkillVersion，不热更新；
- 在 `MODEL_ROUTED` 真正完成前，不把枚举或 UI 配置宣称为可用功能。

### 9.2 Tool

`AgentToolDefinition` 和 Tool Provider 契约继续留在 `infra-ai`；Tool 的选择、循环、预算、HITL、
幂等和事件迁入 `agent-runtime`。MCP、知识检索、内置 Tool 的构建留在 Application/infra-ai 适配层。

ReAct 调用链固定为：

```text
ChatClient.stream/call
  + runtime ToolCallback list
  + AdvisorParams.toolCallingAdvisorAutoRegister(false)
-> Runtime 检查 ChatResponse.toolCalls
-> ExecutionState 选择可执行调用
-> HumanApprovalHook CONTINUE / INTERRUPT
-> ToolCallingManager.executeToolCalls
-> 标准 Assistant ToolCall + ToolResponse messages 回填
-> 下一轮 ChatClient
```

这是 Spring AI 2.0 官方 user-controlled Tool Execution，不是重写 Tool Calling 协议。禁止直接调用
`ToolCallback.call()` 绕过 `ToolCallingManager`，也禁止在同一 Agent 路径里同时启用自动 `ToolCallingAdvisor`。

平台“单个 Tool 每次 Agent 执行最多调用两次”与 Application 全局 Tool 预算同时生效，发布配置只能收紧。

### 9.3 Memory

Memory 划分为三类，禁止混用：

| 类型                 | 所属                                     | 说明                             |
| -------------------- | ---------------------------------------- | -------------------------------- |
| Conversation Memory  | Application 持久化，Runtime 读取快照     | 窗口消息与滚动摘要               |
| Long-term Memory     | Application 持久化，Runtime 读取有界结果 | 用户画像与相关交互记录           |
| Execution Checkpoint | Runtime 定义，Application 落 PostgreSQL  | 恢复当前 Agent Run，不是用户记忆 |

`RuntimeContextAdvisor` 只负责 Runtime 内已加载 Memory 快照的有界注入。摘要、画像、交互记录仍由 Assistant 完成事件通过
RocketMQ 异步生成并落 PostgreSQL/向量存储，不使用 Redis Stream。

实施时优先重用 Spring AI `Message` 作为 Model Invocation 历史，但不让
`MessageChatMemoryAdvisor` 自动写会话。原因是当前官方实现不保存 Tool 中间消息，且 Lingxi 已有消息顺序、
摘要和异步 Memory 事实链。只有在后续证明 Spring AI `ChatMemoryRepository` 适配器能避免双写、能保存完整
Tool 历史并能按 Checkpoint 恢复时，才能替换当前显式历史装配。

### 9.4 HITL

首期只保留 Lingxi 已有且可审计的 `APPROVE`、`REJECT`；不因参考框架支持就提前加入 `EDIT`。

```text
BEFORE_TOOL HumanApprovalHook
-> persist checkpoint + interaction + pending invocation in short transaction
-> emit HUMAN_REQUIRED
-> return Interrupted
-> user decision transaction
-> MQ locator event
-> Application loads exact checkpoint and frozen versions
-> AgentRuntime.resume
-> approved tool executes once / rejected result returns to model
```

恢复不重新做意图路由、不重新规划、不重新激活到其他版本。Checkpoint 指纹必须覆盖 run、AgentVersion、
Tool 配置、规范化入参和资源范围。消费 Checkpoint、领取 Invocation 与 Run 状态转换必须具备幂等和前置状态校验。

## 10. Supervisor、Plan-and-Execute 与 Workflow

### 10.1 Specialist

`SPECIALIST` 使用统一 ReAct 循环。Prompt、Memory、Skill、Tool 都通过 Runtime State 和扩展点进入，
`AgentChatServiceImpl` 不再作为第二个运行入口。

### 10.2 Supervisor / Plan-and-Execute

`SUPERVISOR` 是一种 `AgentExecutor`，不是 Workflow：

- Planner 只能看到 Application 发布快照中的能力描述；
- Planner 通过 Spring AI typed structured output 输出决策，经 JSON Schema 校验、有界自修复和 Lingxi 业务语义校验后
  写入 `ExecutionState`；
- 每个步骤创建隔离的 `AgentInvocationState`，共享 Run 预算；
- 子 Agent 文本只作为步骤结果，不直接写 Assistant 最终正文；
- 失败只把安全错误码和有界观察交给 Planner；
- 重规划有次数上限；写能力成功后不得因重规划重复调用；
- 最终综合不开放 Tool，只使用预留模型和 Token 额度。

不同时保留“Supervisor 动态委派”和另一套不可观测的 SubAgent Tool 循环。Lingxi 当前以显式
Plan-and-Execute 为 Supervisor 的唯一多步骤语义，后续若需要纯 Agent-as-Tool，必须另立真实用例和审计规则。

### 10.3 Workflow 边界

Workflow 是确定性图执行域，拥有独立的 Workflow State、Run、NodeAttempt 和 Event：

- Application 意图叶子为 `WORKFLOW` 时直接进入 `WorkflowExecutor`，不先进入 Agent Runtime；
- Workflow 的 Agent 节点通过 `AgentNodeInvoker` 调用 `AgentRuntime`；
- Supervisor 计划步骤引用 Workflow 时，通过 `RuntimeCapabilityInvoker` 调用 Workflow；
- Workflow 不读取或修改 Agent `ExecutionState`，只通过显式请求传递 runId、输入和受控上下文；
- 删除 `ChatExecutionContextRegistry`，不再用进程内 Map 侧载可信上下文。

## 11. 现有类处置清单

“迁移”表示职责和实现整体迁往目标模块；“合并”表示行为保留但旧类消失；“删除”表示语义不再存在，
不是保留代理或兼容壳。

### 11.1 当前 `agent-execute` → 目标 `agent-runtime`

| 处置           | 现有类                                                                                                              | 目标                                                                                   |
| -------------- | ------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| 保留并收束     | `SpringAiChatCompletionGateway`、`SpringAiChatClientFactory`、`SpringAiStreamingChatGateway`、`TokenUsageConverter` | 非流式/流式统一进 `ChatClient`；网关不再拥有 Agent Tool Loop                           |
| 保留并替换方法 | `ChatCompletionGateway`                                                                                             | 保留业务网关边界；用 typed structured completion 替换 `completeJson`/`JSONObject` 协议 |
| 保留并改造     | `ToolCallingConfig`                                                                                                 | 统一提供 Spring AI `ToolCallingManager`/resolver 装配，不承载权限、HITL 或预算         |
| 新增具体类     | `RuntimeModelAdvisor`、`RuntimeContextAdvisor`                                                                      | 分别承担模型记账/事件和已决定上下文注入；不建自有 Advisor 顶层接口                     |
| 合并           | `AgentLoopState`                                                                                                    | 拆入 `ExecutionState` 的共享预算和 `AgentInvocationState` 的本次 ReAct 状态            |
| 保留并内聚     | `AgentToolExecutionLedger`                                                                                          | 作为 `AgentInvocationState` 的 Tool 结果账本，不对 Application 暴露                    |
| 保留并更名     | `BoundedToolCallback`                                                                                               | 收为 `RuntimeToolCallback`，继续装饰 Spring AI `ToolCallback`；移出 HITL 异常控制流    |
| 合并           | `AgentLoopLimitException`                                                                                           | 进入统一 `AgentRuntimeException`/失败分类，不保留 Tool 异常继承关系                    |

### 11.2 Application Agent、Plan 与 Capability 服务

| 处置       | 现有类                                                                                                     | 目标                                                                                                                  |
| ---------- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 保留并瘦身 | `ApplicationChatExecutionService`、`ApplicationChatExecutionServiceImpl`                                   | 只做入口调度、创建/恢复 Run、调用 Agent/Workflow、接收结果；不持有循环                                                |
| 保留       | `ApplicationRuntimeService`、`ApplicationRuntimeServiceImpl`                                               | ApplicationVersion Loader                                                                                             |
| 保留并更名 | `AgentRuntimeService`、`AgentRuntimeServiceImpl`                                                           | `AgentVersionLoader`，只加载/校验版本，不叫 Runtime                                                                   |
| 迁移       | `AgentExecutor`、`AgentExecutorRegistry`、`SpecialistAgentExecutor`、`SupervisorAgentExecutor`             | 迁入 `agent-runtime`；注册表保留启动期唯一角色校验                                                                    |
| 合并       | `AgentChatService`、`AgentChatServiceImpl`                                                                 | 行为并入 Specialist executor、Runtime Advisor 和 Tool Loop；删除旧 Service 入口                                       |
| 保留适配   | `ChatAgentNodeInvoker`                                                                                     | Application 的 Workflow -> AgentRuntime 适配器，不读取进程内注册表                                                    |
| 拆分       | `CapabilityExecutionService`、`CapabilityExecutionServiceImpl`                                             | Agent 分支并入 Runtime；Workflow/平台 Tool 分支成为 Application 的 `RuntimeCapabilityInvoker`                         |
| 保留       | `ApplicationCapabilityChecksum`                                                                            | 版本加载期完整性校验，留在 Application                                                                                |
| 迁移       | `PlannerRuntimeCoordinator*`、`PlannerService*`、`PlanExecutionService*`、`FinalSynthesisService*`         | 迁入 `agent-runtime` 的 Supervisor executor 内部                                                                      |
| 合并/迁移  | `ExecutionDecisionParser`、`ExecutionDecisionValidator`、`PlanInputCompiler`、`ExecutionFailureClassifier` | Parser 由 Spring AI typed structured output 替代后删除；业务语义 Validator、Compiler、Classifier 迁入 `agent-runtime` |
| 合并       | `ExecutionDecisionChecksum`                                                                                | 决策快照校验进入 Runtime Checkpoint/State codec，删除单独 Service                                                     |

### 11.3 Application 执行状态、事件与持久化

| 处置       | 现有类                                                                                                                      | 目标                                                               |
| ---------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| 合并       | `ChatExecutionContext`                                                                                                      | 迁入 `agent-runtime.ExecutionState`；去除 Chat、Entity、SSE 依赖   |
| 删除       | `ChatExecutionContextRegistry`                                                                                              | Workflow 改用显式上下文，不保留进程内旁路                          |
| 合并       | `ChatExecutionEventPublisher`                                                                                               | Runtime 使用 `RuntimeEventSink`；Application 实现持久化与 SSE 投影 |
| 保留并瘦身 | `ExecutionPersistenceService`、`ExecutionPersistenceServiceImpl`                                                            | 只做 Runtime 事实落库，不参与计划循环                              |
| 保留       | `ExecutionRunEntity/Repo/Mapper`、`ExecutionEventEntity/Repo/Mapper`、`ExecutionCheckpointEntity/Repo/Mapper`               | Application PostgreSQL 持久化                                      |
| 保留       | `ExecutionPlanSnapshotEntity/Repo/Mapper`、`ExecutionStepAttemptEntity/Repo/Mapper`                                         | 作为可审计投影；数据由 Runtime Event/Checkpoint 产生               |
| 保留       | `ExecutionRunSkillEntity/Repo/Mapper`                                                                                       | Skill 激活审计；由 `SKILL_ACTIVATED` 幂等写入                      |
| 保留       | `CapabilityInvocationEntity/Repo/Mapper`                                                                                    | Tool/外部能力副作用与对账事实                                      |
| 合并       | `ExecutionRunStartCommand`、`ExecutionEventCommand`、`ExecutionPlanSnapshotCommand`、`ExecutionStepAttemptCommand`          | Application Event/Result 持久化模型；不再作为 Runtime 模型         |
| 迁移       | `ExecutionRunStatus`、`ExecutionEventType`、`ExecutionStepStatus`、`ExecutionFailure`、`ExecutionDeadlineExceededException` | 迁入 `agent-runtime` 并按本文状态/事件收束                         |
| 保留       | `ExecutionCheckpointStatus`、`ExecutionCheckpointType`                                                                      | Application 持久化枚举，与 Runtime Checkpoint type 一一映射        |

### 11.4 Runtime 模型

| 处置 | 现有类                                                                                                                                                                                 | 目标                                                                         |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 迁移 | `AgentRuntimeDefinition`、`AgentRuntimeSnapshot`、`AgentExecutionRequest`                                                                                                              | `agent-runtime` 的发布快照与 Agent 调用模型                                  |
| 合并 | `ApplicationChatExecutionRequest`、`ApplicationChatResult`                                                                                                                             | Application 入口模型保留；进入内核时转换为 `AgentRunRequest/Result`          |
| 合并 | `CapabilityExecutionRequest`、`CapabilityExecutionResult`                                                                                                                              | Runtime 内部 `CapabilityCall/Result`；不引用 Workflow trace 类型作为通用结果 |
| 迁移 | `ExecutionDecision`、`ExecutionPlan`、`PlanStep`、`PlanStepResult`、`PlannerCapabilityDescriptor`、`PlannerClarification`                                                              | `agent-runtime` Supervisor 模型                                              |
| 合并 | `PlannerRequest`、`PlannerResult`、`PlannerRuntimeRequest`、`PlanExecutionRequest`、`PlanExecutionResult`、`FinalSynthesisRequest`、`FinalSynthesisResult`、`SupervisorRuntimeContext` | 收为 Runtime 内部请求/结果，删除重复层级和一接口一实现入参                   |
| 迁移 | `ExecutionDecisionValidationContext`                                                                                                                                                   | `agent-runtime` 的 Planner 校验上下文                                        |
| 保留 | `AppRuntimeSnapshot`、`FallbackTarget`                                                                                                                                                 | Application 发布版本与入口目标解析                                           |
| 保留 | `PromptRuntimeSnapshot`、`PromptRuntimeVariableDefinition`                                                                                                                             | Application Loader 输出；Runtime 只读                                        |
| 合并 | `ActivatedSkill`                                                                                                                                                                       | 迁入 Runtime `SkillActivation`，由 `ExecutionState` 记录                     |
| 合并 | `ExecutionCheckpointPayload`、`ExecutionResumeContext`、`HitlApprovalRef`                                                                                                              | Runtime `ExecutionCheckpoint` 与 `AgentResumeRequest`；删除重复恢复对象      |
| 保留 | `HitlDecisionTxBO`、`ToolApprovalAuthorization`、Interaction 与 Intent Routing 模型                                                                                                    | Application 传输、事务和入口路由模型                                         |

### 11.5 Skill 与 Tool

| 处置     | 现有类                                                                                                               | 目标                                                                                 |
| -------- | -------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| 保留     | `AgentSchemaConstants`、`AgentRole`、`PlanPolicy`                                                                    | 控制面与运行面共享发布契约                                                           |
| 合并     | `AgentProgressPublisher`、`AgentProgressEvent`、`AgentProgressPhase`                                                 | `ExecutionEvent`，删除独立进度协议                                                   |
| 保留     | `SkillSchemaConstants`、Skill 四个枚举、`AgentSkillBindingSnapshot`、`SkillVersionSnapshot`、`SkillSnapshotChecksum` | `infra-ai` 共享发布契约                                                              |
| 拆分     | `SkillRuntimeService`                                                                                                | Application 留版本加载/安全复核；激活与 Prompt 注入迁到 `SkillHook`                  |
| 保留     | `SkillRuntimeRepo`、Runtime Skill Entity/Mapper                                                                      | Application 版本加载；属于当前未提交 Skill V8，不能误删                              |
| 保留     | `AgentTool`、`AgentToolRegistry`、`AgentToolDefinition`、`ToolSecurityPolicy`、Tool 枚举/常量、内置 Tool             | `infra-ai` 稳定 Tool 契约与实现                                                      |
| 迁移     | `AgentToolRuntimeConfig`、`AgentExecutionBudget`、`AgentToolExecutionContext`                                        | `agent-runtime`，合并进 Runtime State/Tool call context                              |
| 合并     | `AgentToolEventPublisher`、`AgentToolExecutionEvent`                                                                 | `ExecutionEvent`                                                                     |
| 合并     | `AgentToolApprovalGate`、`AgentToolApprovalPermit`、`AgentToolApprovalRequest`                                       | `HumanApprovalHook` 与 Tool Invocation 状态；不保留 Gate/Permit 双层协议             |
| 合并     | `HumanInteractionRequiredException`                                                                                  | `AgentRunResult.Interrupted`；删除异常控制流                                         |
| 迁移     | `AgentToolException`、`AgentToolErrorConstants`                                                                      | Runtime 稳定 Tool 失败类型；供应商 Tool 实现只抛该协议                               |
| 保留适配 | `ToolRuntimeService*`、`McpRuntimeToolCallback`、`KnowledgeSearchAgentToolFactory`、`GetCurrentUserInfoTool`         | Application/infra-ai Tool Provider，不拥有循环；继续委托 Spring AI Callback/MCP 能力 |

### 11.6 HITL

| 处置 | 现有类                                                                                                                    | 目标                                                                       |
| ---- | ------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| 保留 | `HitlInteractionController`、`HitlDecisionService`、`HitlDecisionTxService`、`HitlExpiryReconciler`、`HitlResumeConsumer` | Application API、事务、过期和 MQ 恢复入口                                  |
| 保留 | `InteractionSessionService*`、Entity/Repo/Mapper、VO/Request/Message                                                      | 澄清和人工交互持久化                                                       |
| 迁移 | `ToolApprovalFingerprint`                                                                                                 | `agent-runtime` Checkpoint 指纹算法；Application 只存值                    |
| 合并 | `ToolApprovalGateFactory`、`ToolApprovalTxService`                                                                        | Application 的 `RuntimeCheckpointStore` 实现 + Runtime `HumanApprovalHook` |

### 11.7 Memory 与上下文

| 处置       | 现有类                                                                                                                                 | 目标                                                                                                       |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 保留       | `ConversationMemoryService*`、`MemoryContextService`                                                                                   | Application 加载有界 Memory 快照                                                                           |
| 保留       | `ConversationSummaryMemoryService`、`UserProfileMemoryService`、`InteractionRecordMemoryService`、`MemoryPostProcessor` 及 MQ Consumer | Application 异步 Memory 写入闭环                                                                           |
| 保留       | Memory Entity/Repo/Mapper、`ConversationSummaryPersistenceService`、`InteractionMemoryStoreService`、`MemoryEmbeddingModelResolver`    | Application 数据与 Embedding 边界                                                                          |
| 保留并瘦身 | `ConversationContextService*`                                                                                                          | 只准备 Runtime 输入，不再构造可变执行状态                                                                  |
| 合并迁移   | `ConversationContext`、`RuntimeVariables`、`RuntimeVariableAccessor(s)`                                                                | Runtime `ContextSnapshot` 与受控变量读取                                                                   |
| 合并迁移   | `ModelChatContext`、`ModelUsageRecorder`、`ModelRequestFactory`                                                                        | Runtime `ModelCallContext`、`RuntimeContextAdvisor` 和 `RuntimeModelAdvisor`；删除重复的 Prompt/Usage 组装 |
| 保留并收束 | `MemoryCompletionService`                                                                                                              | 异步 Memory 模型调用；使用统一 `ChatClient` Gateway，但不进入 Agent Runtime 生命周期                       |

### 11.8 Workflow

| 处置     | 现有类                                                                                                                                                                                                                      | 目标                                                           |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| 保留     | `WorkflowDefinition`、节点/边/config/intent 模型、`WorkflowDefinitionValidator`                                                                                                                                             | `workflow-core` 独立协议                                       |
| 迁移     | `WorkflowExecutor`、`AgentNodeInvoker`、`IntentClassifierNodeInvoker`、`WorkflowExecutionRequest/Result`、`WorkflowNodeTrace/Status`、`AgentNodeInvocation`、`IntentClassifierNodeInvocation`、`WorkflowExecutionException` | 从 Application 迁到 `workflow-core`                            |
| 迁移     | `LangGraphWorkflowExecutor`、`WorkflowGraphState`、`WorkflowStateConstants`                                                                                                                                                 | 从 Application 迁到 `workflow-core`，保持唯一 LangGraph4j 边界 |
| 保留     | `WorkflowRunEntity/Repo/Mapper`、`WorkflowNodeAttemptEntity/Repo/Mapper`、`WorkflowRunEventEntity/Repo/Mapper`                                                                                                              | Application Workflow 持久化                                    |
| 保留适配 | `WorkflowRunListener*`、`WorkflowRunService*`                                                                                                                                                                               | Application 持久化监听适配器                                   |
| 保留适配 | `ChatAgentNodeInvoker`、`WorkflowIntentClassifierNodeInvoker`                                                                                                                                                               | Application Loader/Runtime 调用适配器                          |
| 删除     | Workflow 对 `ChatExecutionContextRegistry` 的依赖                                                                                                                                                                           | 改为显式 `WorkflowExecutionContext`                            |

### 11.9 控制面与前端

当前 Admin Skill 管理、Agent Skill 绑定、版本查看、Schema V8、迁移和测试全部保留。
它们是控制面，不随 Runtime 类迁移删除。Web 当前无需架构修改；实施到统一事件时再同步类型与展示。

## 12. 实施顺序

本节是架构能力顺序；实际开发以开发计划中拆分更细的 Batch 0–7 和回归门禁为准。

### Phase 1：Spring AI 调用主干、State、Event 与 Specialist

1. 统一 `ChatClient` 创建、Advisor 顺序、Tool Calling 自动关闭规则和 Observation 传播；
2. 将 Maven 目录/`artifactId`/Java 根包一次性更名为 `agent-runtime`/`com.lingxi.workbench.agentruntime`；
3. 在 `agent-runtime` 建立 `AgentRuntime`、`ExecutionState`、`AgentRunRequest/Result` 和 `ExecutionEvent`；
4. 迁入 `AgentExecutor`、Specialist 和基于 `ChatClient + ToolCallingManager` 的 user-controlled ReAct 循环；
5. 合并 `ChatExecutionContext` 与 `AgentLoopState` 的预算、Token 和截止时间；
6. Application 使用新 Runtime 执行直接 Agent，删除旧 `AgentChatService` 路径。

完成标准：直接 Specialist + Tool 的状态、事件、预算、流式文本和终态只有一条路径。

### Phase 2：Hook、Advisor、Skill、Tool、HITL

1. 建立显式 Hook 和 Spring AI Advisor 装配，不创建自有 Interceptor 框架；
2. 将 Skill 注入改为 `SkillHook`，保留当前 V8 `ALWAYS_ON` 事实；
3. 将 `BoundedToolCallback` 收为 Spring AI 原生 `RuntimeToolCallback` 装饰器；
4. HITL 改为 typed interruption + Checkpoint resume；
5. 删除 Gate/Permit/Exception 旧控制流。

完成标准：Tool 副作用只执行一次，审批恢复同一 State，Skill 不扩大 Tool 权限。

### Phase 3：Supervisor 与 Plan-and-Execute

1. 迁移 Planner、计划循环、重规划、最终综合；
2. Planner 接入 typed `responseEntity`、provider structured output 和有界 Schema validation，删除 `JSONObject` Parser；
3. 计划、步骤、Skill、Tool 全部发布统一事件；
4. Application 只实现 capability/checkpoint/event adapters；
5. 删除 Application 内旧 Planner/Plan service 和重复模型。

完成标准：Supervisor 从规划到最终回答不依赖 Application 编排代码。

### Phase 4：Workflow 与 Application 收口

1. Workflow 执行协议和 LangGraph4j 实现迁到 `workflow-core`；
2. 删除 `ChatExecutionContextRegistry`，Agent 节点显式调用 Runtime；
3. 瘦身 `ApplicationChatExecutionServiceImpl`；
4. 增加 ArchUnit 规则禁止 Application 再出现 Agent 模型循环、Tool Loop 或 Plan Loop。

完成标准：Application 只剩版本加载、上下文入口、Runtime 调用、事件转发和结果落库。

### Phase 5：激活模式与清理

1. 在真实闭环中实现 `EXPLICIT_ONLY`；
2. 以 Skill 元数据披露 + Runtime 激活实现 `MODEL_ROUTED`；
3. 删除所有旧枚举别名、旧 JSON 字段、旧事件 DTO 和迁移期适配器；
4. 更新历史设计文档顶部状态，指向本文。

## 13. 验收与回归

每个 Phase 完成后至少验证：

- Specialist 无 Tool、单 Tool、多轮 Tool、单 Tool 两次上限、未授权 Tool；
- 非流式/流式均走 `ChatClient`，自动 `ToolCallingAdvisor` 未与 user-controlled 循环并存；
- Advisor 顺序稳定，结构化输出自修复每次都占用模型次数与 Token 预算；
- Planner typed structured output 的 Schema 失败、业务语义失败、供应商不支持 native structured output 回退；
- 工作 Token 软边界、最终 Token 硬边界、模型/Tool/委派/重规划次数和动态超时；
- Skill V8 历史版本不漂移、`ALWAYS_ON` 激活、Tool 交集、`ai_execution_run_skill` 幂等；
- HITL approve/reject、重复决定、过期、取消、MQ 重投、进程重启和外部副作用对账；
- Supervisor 直执行、计划、步骤失败、有限重规划、部分结果收口和最终综合；
- Application 固定回复、澄清、AGENT 叶子、WORKFLOW 叶子和 fallback；
- Workflow Agent 节点调用 Runtime，且不依赖进程内 Context Registry；
- Event 顺序、幂等、敏感字段、SSE 恢复和 Web 计划/Tool/HITL 展示；
- Spring AI ChatClient/Advisor/ChatModel/Tool span 与 Lingxi Run/Agent/Plan 父级观测关系正确，无重复 span；Prompt、
  Completion、Tool 参数和结果默认不导出；
- Conversation Summary、User Profile、Interaction Record 三个 RocketMQ 消费链路；
- `./mvnw -pl agent-runtime -am test`、`./mvnw -pl application -am test`、`./scripts/verify.sh`；
- Admin `pnpm lint && pnpm build`、Web `pnpm test && pnpm lint && pnpm build`（对应契约变化时）；
- Application 真实启动和至少一条发布版本的端到端调用。

## 14. 已确认的决策

1. 同意将 `agent-execute` 原地更名为 `agent-runtime`，不并存旧模块或旧包名兼容层；
2. 同意 `agent-runtime -> workflow-core` 的单向依赖，并把 Workflow 执行端口迁入 `workflow-core`；
3. 同意 `ExecutionState` 为唯一可变运行事实，删除 `ChatExecutionContextRegistry`；
4. 同意只有 Agent Hook 可中断；Spring AI Advisor 与 `RuntimeToolCallback` 不表达 HITL 暂停；
5. 同意 Supervisor 只保留显式 Plan-and-Execute 多步骤语义，不并存另一套隐式 SubAgent Tool 循环；
6. 同意首期 HITL 只保留 approve/reject，不提前实现 edit；
7. 同意 Skill 按 Phase 演进：先迁移现有 `ALWAYS_ON`，再实现 `EXPLICIT_ONLY` 和 `MODEL_ROUTED`；
8. 同意最终合并不保留旧 Runtime 兼容逻辑、别名、代理类或 migration-on-read。
9. 同意 Spring AI First：统一使用 `ChatClient`/Advisor/Tool/MCP/structured output/Observation，
   但保留 Lingxi 对 Agent 循环、预算、HITL、Checkpoint、权限和副作用的最终控制权。
