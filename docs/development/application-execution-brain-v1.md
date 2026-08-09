# Application 执行大脑 V1 开发计划

> 状态：实施中
> 更新时间：2026-08-08
> 目标：将 Chat 主链从“意图树决定一切”调整为“共享上下文 + 可选快速路由 + 模型规划 + 受控能力执行”。

## 1. 背景与结论

意图树适合表达少量稳定、可评测的高确定性分流，但不应承担通用任务规划。Workflow
已经独立为可发布、可版本化的能力，也不再是所有 Chat 请求的默认外壳。当前主链统一为：

```text
用户请求
-> 构建共享上下文
-> 恢复待处理澄清
-> 尝试可选快速路由
-> Planner 或默认 ReAct Agent
-> ReAct Agent / Workflow / Plan-and-Execute / 澄清
-> 结果持久化与 SSE 事件
```

核心结论：

1. Application 必须锁定默认 ReAct AgentVersion，确保未配置 Planner 时仍可工作。
2. 意图树降级为 `optionalFastRoutes`，未配置或未命中均不得阻断 Chat。
3. Planner 只从 Application 发布快照中的能力目录选择能力，不能生成资源主键或绕过权限。
4. Workflow 仅在快速路由或 Planner 明确选择对应 Workflow 能力时执行。
5. 知识检索是 ReAct Agent 可按需调用的 Tool，不在上下文准备阶段自动执行。
6. Supervisor 是多 Agent 动态委派角色，不替代 Planner 的显式计划和执行记录。
7. 新项目直接采用目标契约，不保留旧字段、旧类型、别名或迁移时读取逻辑。

## 2. 本期范围

### 2.1 必须完成

- 在路由前统一加载历史消息、摘要、画像、相关交互记录和运行时变量。
- 为上下文各部分设置独立 Token 预算，保留用户原始问题用于审计。
- 建立 Application 默认 Agent、Planner 策略、能力目录和运行时限制的发布契约。
- 支持 `EXECUTE_REACT`、`EXECUTE_WORKFLOW`、`EXECUTE_PLAN`、`CLARIFY` 四类模型决策。
- 校验计划结构、能力类型、步骤依赖、输入引用、风险确认和调用上限。
- 支持计划执行、步骤失败、有界重规划和最终结果合成。
- 持久化 Run、Plan、StepAttempt、Event 和待处理交互会话。
- 通过 SSE 输出规划、步骤、澄清、完成、失败和取消状态。
- 管理端完成默认 Agent、能力目录、Planner、上下文、记忆和快速路由配置。
- 补齐发布校验、运行时、权限、预算、幂等性和失败路径测试。

### 2.2 本期不做

- 不让意图树成为所有请求的强制入口。
- 不支持模型动态发现未发布能力或直接提交资源主键。
- 不实现并行计划、任意 DAG、无限循环或无限重规划。
- 不自动执行知识检索、问题改写或高风险写操作。
- 不将 Redis 作为执行状态、澄清状态或记忆的唯一事实来源。
- 不为旧 `RAG_CHAT`、旧 Chat 节点链或内嵌 Workflow 提供兼容逻辑。

## 3. 里程碑

### M1：契约与旧架构清理

- 删除旧 ChatProcessNode、Prompt 节点、自动 RAG 节点和问题改写节点。
- 删除 `RAG_CHAT` 及其配置模型，只保留 `REACT`、`SUPERVISOR`。
- 使用共享 `AgentType` 枚举贯通管理端、数据库和运行时快照。
- 删除 Application 内嵌主 Workflow 和强制意图绑定。

完成标准：仓库不存在旧运行时入口、旧类型分支或静默兼容读取。

### M2：共享上下文

- `ConversationContextService` 在任何路由和模型执行前准备上下文。
- 加载最近消息、会话摘要、用户画像和相关交互记录。
- 生成仅供路由和规划使用的上下文化问题，保留原问题用于回答和审计。
- 初始化统一运行时变量，并执行各分区 Token 预算检查。

完成标准：快速路由、Planner、Agent 和 Workflow 读取同一份服务端可信上下文。

### M3：Application 发布契约

- 草稿配置 `defaultAgent`、`optionalFastRoutes`、`contextPolicy`、`memory`、
  `plannerPolicy`、`capabilityCatalog` 和 `runtimePolicy`。
- 发布时将 Agent、Workflow、Tool 引用解析为具体版本和配置校验和。
- 校验资源归属、启用状态、版本关系、风险等级、输入输出 Schema 和预算。

完成标准：运行时只读取不可变 ApplicationVersion 快照，不动态跟随资源当前版本。

### M4：执行决策

- Planner 使用发布的模型和 Prompt 生成结构化 `ExecutionDecision`。
- 能力目录以不可信数据区块提供给模型；模型只能返回能力编码。
- Parser 严格拒绝 Markdown、未知字段和未知枚举。
- Validator 校验动作与能力类型、计划依赖、参数来源和风险确认。

完成标准：任意非法模型输出都在执行前失败，不能产生 Tool、Agent 或 Workflow 调用。

### M5：计划执行

- 直接执行单个 ReAct Agent 或 Workflow 能力。
- 串行执行有限计划，支持 `USER_QUERY`、`LITERAL`、`STEP_OUTPUT` 输入来源。
- 步骤失败后在上限内携带安全观察重新规划。
- 所有步骤完成后调用 `FinalSynthesisService` 生成最终回答。

完成标准：每次能力调用均可追溯到 ApplicationVersion、计划版本和步骤尝试。

### M6：交互与可观测性

- 普通信息不足进入 `PLANNER_CLARIFICATION`。
- 写操作或高风险能力进入 `RISK_CONFIRMATION`。
- 澄清会话锁定 ApplicationVersion、原问题、能力范围和过期时间。
- SSE 输出规划、步骤、工具、澄清和终态事件，不泄露 Prompt、工具参数和原始结果。

完成标准：断开重连能够读取已持久化事件，取消、失败和超时都有明确终态。

### M7：管理端与验收

- Application 编辑器按基础信息、默认 Agent、能力目录、上下文与记忆、Planner、
  快速路由、校验发布分步配置。
- 字段说明使用可聚焦 Tooltip；校验错误、风险和运行状态保持可见。
- 历史版本详情展示完整冻结配置并支持激活合法历史版本。

完成标准：完成创建、保存草稿、校验、发布、查看历史版本、切换版本和 Chat 执行全链路。

## 4. 验收场景

1. 未启用 Planner、未配置快速路由时，简单问题进入默认 ReAct Agent。
2. 快速路由命中固定回复时，不调用模型或执行引擎。
3. 快速路由命中 Workflow 时，只执行锁定的 WorkflowVersion。
4. 快速路由未命中且 Planner 启用时，由 Planner 决定直接执行或生成计划。
5. Planner 选择未发布能力、错误能力类型或非法步骤引用时，执行前明确失败。
6. 高风险能力未经确认时不执行，确认后恢复同一 ApplicationVersion 和能力范围。
7. 步骤失败只在配置上限内重规划，达到上限后记录失败终态。
8. 知识检索只在 ReAct Agent 主动调用 `knowledge_search` 时发生。
9. Supervisor 委派不超过发布快照中的 `collaboration.max_delegations`。
10. 历史消息、摘要、画像和交互记录受预算约束，日志中不出现敏感原文。

## 5. 验证命令

```bash
./mvnw -pl admin,application -am test
./mvnw clean verify
```

管理端执行 `pnpm lint`、`pnpm build`，并手工验证 Application 配置、发布、版本回看、
Planner 澄清、计划执行和 SSE 时间线。任何跳过项必须在交付说明中单独列出。
