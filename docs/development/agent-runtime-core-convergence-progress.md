# Agent Runtime 内核收束开发进度

> 本文档只追加批次进度，不替代 Git 历史、测试报告或设计文档。
> 开发计划：[Agent Runtime 内核收束开发计划](agent-runtime-core-convergence-development-plan-v1.md)

## 记录模板

```text
## YYYY-MM-DD HH:mm Batch N START|DONE|BLOCKED

- 分支与工作区：
- 开发前本地进度：
- 上批回归命令与结果：
- 本批目标：
- 本批不做：
- 实际开发内容：
- 删除的旧路径：
- 契约、Schema、配置与数据变更：
- 收束回归命令与结果：
- 手工/真实链路验证：
- 已知问题或阻塞：
- 剩余开发计划：
- 下批入口条件：
```

## 2026-08-17 Batch 0 PENDING

- 分支与工作区：Server、Admin、Web 均为 `main...origin/main`。
- 开发前本地进度：
  - Server 存在 Skill V8、Agent Skill 绑定、Spring AI `ChatClient`、执行持久化、观测测试、Schema/数据和增量迁移等未提交改动；
  - Admin 存在登录重定向、认证状态、Skill 管理、Agent Skill 绑定和版本展示等未提交改动；
  - Web 工作区干净。
- 当前设计进度：《Agent Runtime 内核收束设计》已确认；开发计划与本进度台账已建立。
- 已确认命名决策：Batch 1 将现有 `agent-execute` 目录、Maven `artifactId` 和
  `com.lingxi.workbench.agentexecute` 根包一次性更名为 `agent-runtime` 与
  `com.lingxi.workbench.agentruntime`，不保留兼容层。
- 上批回归命令与结果：本次只编写计划，尚未执行 Batch 0 代码回归。
- 已知问题或阻塞：当前有大量未提交改动，Batch 0 必须先完成分类和真实回归，不能直接开始 Runtime 重构。
- 剩余开发计划：Batch 0–7 全部未执行。
- 下批入口条件：开始 Batch 0 时重新读取三个仓库状态，把本条从 `PENDING` 下方追加为正式 `START` 记录。

## 2026-08-17 22:26 Batch 0 START

- 分支与工作区：Server、Admin、Web 均为 `main...origin/main`；Server 与 Admin 有未提交改动，Web 干净。
- 开发前本地进度：
  - Server 已修改 27 个受 Git 跟踪文件，共 473 行新增、18 行删除；另有 Skill V8、运行时 Skill 加载/审计、Spring AI `ChatClient` 工厂、数据库迁移和本计划文档等未跟踪文件；
  - Admin 已修改 13 个受 Git 跟踪文件，共 629 行新增、89 行删除；另有 Skill API/页面和登录 redirect 工具等未跟踪文件；
  - 当前未发现子目录 `AGENTS.md`，已重新完整读取 Server 根 `AGENTS.md`。
- 上批回归命令与结果：尚未执行；Batch 0 即是当前未提交改动的基线回归门禁。
- 本批目标：回归 Skill V8、Agent Skill 版本绑定与审计、Spring AI `ChatClient` 非流式调用、执行持久化、Admin 登录与 Skill/Agent 管理链。
- 本批不做：不更名 `agent-execute`，不新建 Runtime 类，不迁移 Application 运行逻辑。
- 预定回归：`agent-execute`、`application`、`admin` Maven 测试，Server `verify.sh`，Admin lint/build，必要的 Application/Admin 启动与真实页面验证。
- 剩余开发计划：Batch 0 回归通过后进入 Batch 1，执行 `agent-runtime` 原地更名与无 Tool Specialist 纵向切片。

## 2026-08-17 22:40 Batch 0 DONE

- 分支与工作区：三个仓库仍为 `main...origin/main`；保留了 Server/Admin 原有未提交改动，Web 仍干净。
- 上批回归命令与结果：
  - `./mvnw -pl agent-execute -am test`：通过，`agent-execute` 18 项测试通过；
  - `./mvnw -pl application -am test`：修复前 274 项、修复后 275 项测试通过；
  - `./mvnw -pl admin -am test`：沙箱内仅因 HTTP 测试禁止绑定本地端口失败，沙箱外重跑 202 项全部通过；
  - `./scripts/verify.sh`：沙箱外完整八模块 `clean verify` 通过；
  - Admin `pnpm lint`、`pnpm build`：通过。
- 实际开发内容：
  - 真实启动发现 `HitlExpiryReconciler` 把所有过期 PENDING 交互送入 Tool 审批状态机；
  - 只读审计确认告警记录均为旧 `PLANNER_CLARIFICATION`，不是 `ACTION_APPROVAL`；
  - `InteractionSessionRepo.findExpiredPending` 增加交互类型条件，HITL 收敛器固定查询 `ACTION_APPROVAL`；
  - 新增 `HitlExpiryReconcilerTest`，防止 Planner/Workflow 交互再次越界进入 HITL 生命周期。
- 删除的旧路径：撤回了基于错误类型推断生成、且从未应用的清理迁移；未用数据修补掩盖运行时查询边界问题。
- 契约、Schema、配置与数据变更：无 API、Schema 和开发库写入；`.gitignore` 增加本地 `.learnings/`。
- 收束回归命令与结果：新增精确测试 1 项通过；`./mvnw -pl application -am test` 共 275 项通过；Application 重启并跨过一次 60 秒扫描周期，无 HITL 越界告警。
- 手工/真实链路验证：
  - Admin/Application 均连接 PostgreSQL、Redis、RocketMQ、MinIO 并启动成功；
  - 错误密码返回 401，正确账号登录和 Session 返回 200，ROOT 会话含 58 项权限、8 个一级菜单和 Skill 动态菜单；
  - Skill 列表与 Agent Skill 选项接口返回 200；
  - 浏览器未登录访问 `/agent/skill`、`/agent/manage` 均跳转 `/auth/login` 并保留原 redirect。
- 已知问题或限制：出于浏览器凭据传输确认要求，未在自动化浏览器表单中输入开发账号密码；成功登录、Session 和动态菜单通过真实 HTTP 链路验证。
- 剩余开发计划：Batch 1–7 未完成；下一步原地更名 `agent-execute`，随后实现无 Tool Specialist 的 Runtime 纵向切片。
- 下批入口条件：重新读取 Server `AGENTS.md`，检查三个仓库最新状态，重跑 Batch 0 精确回归后记录 Batch 1 START。

## 2026-08-17 22:42 Batch 1 START

- 分支与工作区：Server/Admin/Web 均为 `main...origin/main`；Server 与 Admin 原有未提交改动保持不变，Web 干净。
- 开发前本地进度：已重新完整读取 Server `AGENTS.md`；未发现 Server/Admin 业务子目录的额外 `AGENTS.md`。
- 上批回归命令与结果：`./mvnw -pl admin,application -am test` 通过，其中 Admin 202 项、Application 275 项；Admin `pnpm lint`、`pnpm build` 通过。
- 本批目标：原地更名 `agent-execute` 为 `agent-runtime`，建立无 Tool Specialist 的 `AgentRuntime`、`ExecutionState`、`ExecutionEvent` 和 Application 调用纵向切片。
- 本批不做：不迁移 Tool/Skill 循环、HITL、Supervisor/Plan & Execute 或 Workflow 执行实现。
- 预计修改：根/模块/Application Maven 坐标，Java 根包，Server `AGENTS.md`，Runtime 模型与门面，Application 直接 Agent 入口及对应测试。
- 删除的旧路径：本批完成时必须不存在生产使用的 `agent-execute` 目录、`agent-execute` artifactId 或 `agentexecute` Java 包；不提供 relocation、alias 或兼容代理。
- 契约、Schema、配置与数据变更：预期无数据库 Schema 变更；Application 到 Runtime 的 Java 契约会直接替换。
- 本批验收：模块更名搜索为零、无 Tool 成功/失败/预算/超时/取消/部分结果与事件序列测试、`agent-runtime -am test`、`application -am test`、`verify.sh` 和真实启动。
- 剩余开发计划：Batch 1–7；本批先完成原地模块更名，再基于更名后的包建立纵向切片。

## 2026-08-17 23:02 Batch 1 DONE

- 分支与工作区：Server/Admin/Web 仍为 `main...origin/main`；保留了 Batch 0 已验证的
  Server/Admin 未提交改动，Web 仍干净；本批未执行 Git 暂存、提交或推送。
- 开发前本地进度：重新读取 Server `AGENTS.md`，检查三个仓库与子目录规则，
  确认 Batch 0 修复和 Skill V8/Spring AI 未提交基线仍在。
- 上批回归命令与结果：`./mvnw -pl admin,application -am test` 通过，Admin 202 项、
  Application 275 项；Admin `pnpm lint` 和 `pnpm build` 通过后才开始本批开发。
- 实际开发内容：
  - 将目录、Maven `artifactId`、Java 根包和所有调用方一次性从 `agent-execute` /
    `agentexecute` 更名为 `agent-runtime` / `agentruntime`；
  - 建立具体 `AgentRuntime.run`、强类型 `AgentRunRequest/Result`、唯一 `ExecutionState`、
    `ExecutionPolicy`、单调 `ExecutionEvent.sequenceNo` 和安全事件载荷；
  - 无 Tool Specialist 已形成 Application 版本/上下文准备 → Runtime 调用 →
    事件先持久化后投影 SSE → 文本/用量返回外层落库的纵向闭环；
  - 流式和非流式模型调用均统一经过 Spring AI `ChatClient`，生产代码不再直接调用
    `ChatModel.call/stream`；通过 `RuntimeContextAdvisor` 传递服务端可信身份，
    `RuntimeModelAdvisor` 统一模型次数、实际 Token 和模型事件；
  - 执行事件持久化支持 Runtime 显式序号/发生时间、运行加锁、同序号同类型幂等和断号拒绝；
  - 运行终态明确区分 `PARTIAL`、`CANCELLED`、`FAILED`，为后续外部副作用对账保留
    `RECONCILIATION_PENDING` 语义，不再使用含混的运行级 `INTERRUPTED`。
- 删除的旧路径：旧模块目录、旧 Maven 坐标、旧 Java 包名及无 Tool 模型流的
  Application 直调路径；未增加 relocation POM、alias、兼容代理或双读分支。
- 计划调整：`AgentRuntime.resume` 将在 Batch 3 与 Checkpoint/HITL 真实恢复闭环同时引入；
  Batch 1 只保留已有真实调用方的 `run`，以遵守“不制造新的空抽象”。
- 契约、Schema、配置与数据变更：Application 到 Runtime 的 Java 契约直接替换；
  运行状态与事件枚举扩充，对应数据库列为无 CHECK 约束的 `VARCHAR`，无需 Schema 迁移；
  无对外 API、部署配置或开发数据写入。
- 收束回归命令与结果：
  - `agent-runtime` 26 项测试通过，覆盖成功与事件序列/用量、部分超时、活跃输出宽限、
    模型发布器失败、空结果、Token 预算耗尽、取消、Tool 旧路径回归和 Advisor 真实整合；
  - Application 282 项测试通过，新增覆盖事件“先持久化、后 SSE”、文本用量、
    显式序号/幂等、PARTIAL/CANCELLED 终态、零活跃输出宽限和无 Tool Runtime 调用；
  - `./scripts/verify.sh` 在沙箱外完整通过，八个 Maven 模块均 `SUCCESS`；
  - `git diff --check` 通过；生产源码中无直接 `ChatModel.call/stream`，Maven/脚本/
    `AGENTS.md`/生产与测试代码中无旧模块或旧包名。
- 手工/真实链路验证：使用本批重新打包的 Application 在随机端口启动成功，
  Spring 上下文、PostgreSQL 连接、RocketMQ 生产者/四个消费者、MinIO 与新 Runtime Bean 均装配成功；
  验证后优雅停机，未遗留本批启动进程。
- 已知问题或限制：
  - 有 Tool Agent 仍暂时经过 `AgentChatServiceImpl.generateWithTools`，将在 Batch 2 删除；
  - Skill `ALWAYS_ON` 的解析/审计仍由 Application 完成，将与 Tool 权限交集一起迁入 Batch 2；
  - `ChatExecutionContext` 仍是 Application 外层过渡状态，Tool、HITL、Supervisor 迁移完成前不能删除；
  - 当前 `RuntimeEventSink` 是 Runtime 到 Application 的真实持久化/SSE 端口；文本增量继续使用消息生成事件，
    不会把每个 token chunk 持久化为 Runtime `ExecutionEvent`。
- 剩余开发计划：Batch 2–7 未完成；下批迁移 Spring AI user-controlled Tool Execution、
  Tool 幂等/预算/事件、Skill `ALWAYS_ON` 及有效 Tool 交集，并删除 Application 旧 Tool Loop。
- 下批入口条件：开始 Batch 2 前重新检查三个仓库状态与本记录，先回归
  Batch 1 无 Tool Runtime 纵向切片、Runtime Advisor 整合、事件序号/幂等、全量 Server `verify`
  和 Application 真实启动，通过后才开始本批开发。

## 2026-08-18 00:17 Batch 2 START

- 分支与工作区：Server/Admin/Web 均为 `main...origin/main`；Server 与 Admin 保留上批已验证的
  未提交改动，Web 干净，未发现来源不明的新改动。
- 开发前本地进度：已重新完整读取工作区根目录和 Server `AGENTS.md`，确认 Server 无子目录
  额外规则；重新检查 Batch 1 DONE 记录和 Batch 2 出入口。发现工作区根 `AGENTS.md`
  仍有两处历史 `agent-execute` 描述，将在上批回归通过后与 Batch 2 一并收正为 `agent-runtime`。
- 上批回归命令与结果：执行中；预定先回归 `AgentRuntimeTest`、
  `RuntimeAdvisorIntegrationTest`、`ApplicationRuntimeEventSinkFactoryTest`、
  `ExecutionPersistenceServiceImplTest`、`AgentChatServiceImplTest`，再执行全量 `verify.sh` 和 Application 真实启动。
- 本批目标：用 Spring AI `ChatClient + ToolCallingManager` 完成 user-controlled Tool Execution，
  将 ReAct 迭代、Tool 幂等/预算/事件、Skill `ALWAYS_ON` 指令与有效 Tool 交集收入同一
  `ExecutionState`，并删除 Application 旧 Tool Loop。
- 本批不做：不迁移 HITL/Checkpoint 恢复、Supervisor/Plan & Execute 或 Workflow 图执行；
  不实现 `EXPLICIT_ONLY` 与 `MODEL_ROUTED`；不引入 Spring AI Alibaba 依赖。
- 契约、Schema、配置与数据预期：优先直接替换 Runtime Java 契约；预期无对外 API 和 Schema 变更；
  Skill 仍绑定已发布版本，“请求的 Tool”只作为依赖声明，不扩大 Agent/Platform 授权。
- 剩余开发计划：Batch 2–7；当前只进入 Batch 2 回归门禁，未开始 Tool Runtime 生产代码迁移。

## 2026-08-18 00:45 Batch 2 DONE

- 分支与工作区：Server/Admin/Web 仍为 `main...origin/main`；保留 Batch 0–1 已确认的
  Server/Admin 未提交改动，Web 仍干净；本批未执行 Git 暂存、提交或推送。
- 开发前本地进度：重新完整读取工作区根目录与 Server `AGENTS.md`，检查三个仓库状态、
  Batch 1 DONE 记录和 Batch 2 出入口；先修正根规则中两处过期的 `agent-execute` 描述，
  再进入开发。
- 上批回归命令与结果：精确回归 `AgentRuntimeTest`、`RuntimeAdvisorIntegrationTest`、
  `ApplicationRuntimeEventSinkFactoryTest`、`ExecutionPersistenceServiceImplTest` 和
  `AgentChatServiceImplTest` 通过；`./mvnw -pl application -am test` 与上一批全链路回归通过，
  并使用 Batch 1 构建产物完成 Application 真实启动后才开始本批生产代码迁移。
- 实际开发内容：
  - 将无 Tool 与有 Tool Agent 合并为唯一 `AgentRuntime.run` 入口，Application 只准备锁定版本、
    Runtime 上下文和持久化事件出口，不再维护独立 Tool Loop；
  - 使用 Spring AI `ChatClient + ToolCallingManager` 实现 user-controlled Tool Execution；
    每轮真实模型调用统一经过 `RuntimeContextAdvisor` 与 `RuntimeModelAdvisor`，仍由 Runtime
    掌握迭代、预算、最终综合和 Tool 执行时机；
  - 扩展唯一 `ExecutionState`，统一模型/Token/Tool 预算、单 Tool 次数、Tool Ledger、Skill、
    生命周期、最终综合与单调事件序列；显式 `finalizationOnly` 不再依赖含混的预算推断；
  - 增加具体 `SkillHook`，将锁定的 `ALWAYS_ON` Skill 指令注入每次工作/最终模型调用，
    Skill 请求的 Tool 仍只作为依赖声明，最终可用 Tool 由版本授权交集决定；
  - 用 `RuntimeToolCallback` 收口 Tool 参数指纹、确定性结果复用、结果长度限制、审批入口和
    `TOOL_STARTED/REUSED/COMPLETED/FAILED` 事件；Application 事件出口先持久化，再投影
    Timeline、Progress 和 SSE；
  - 中间 Tool 推理文本不再向用户输出，只有无 Tool Call 的最终模型响应形成回答；最终综合
    明确关闭 Tool，防止预算耗尽后的额外副作用。
- 删除/合并的旧路径：删除 `AgentLoopState`、`AgentLoopLimitException`、
  `BoundedToolCallback`、`AgentExecutionBudget`、`AgentToolEventPublisher`、
  `AgentProgressPublisher` 和 Application 私有 `ActivatedSkill`；`AgentToolRuntimeConfig`
  只保留 Tool、执行上下文、结果上限和审批 Gate。未保留兼容别名、代理或双路径。
- 契约、Schema、配置与数据变更：Runtime Java Request/Event/State 契约直接替换；新增的
  Skill/Tool 事件仍落入现有无 CHECK 约束的事件表，无数据库 Schema、对外 API 或部署配置变更。
- 收束回归命令与结果：
  - `agent-runtime` 25 项测试通过，覆盖无 Tool、单/多轮 Tool、确定性复用、超限、最终综合、
    Skill 与 Tool 策略注入顺序、事件和 Spring AI `ToolCallingManager` 真实整合；
  - Application 283 项测试通过，覆盖统一 Runtime 调用、Tool 事件持久化/投影、预算顺序和
    既有执行持久化；
  - `./scripts/verify.sh` 在沙箱外完整通过，八个 Maven 模块均 `SUCCESS`；沙箱内仅因
    Admin Mock HTTP Server 无权绑定本机临时端口失败，与业务代码无关；
  - `git diff --check` 通过；生产源码中无旧 Tool Loop 类型、重复预算/发布抽象、旧模块/包名，
    也无直接 `ChatModel.call/stream` 调用。
- 手工/真实链路验证：使用本批重新打包的 Application 在随机端口启动成功，Spring 上下文、
  PostgreSQL、RocketMQ 生产者/四个消费者、MinIO 和新 Tool-capable Runtime Bean 均装配成功；
  验证后优雅停机，未遗留本批启动进程。
- 开发过程纠错：Mockito 嵌套 stubbing 与新增类型漏 import 两个复发问题已记入工作区本地
  `.learnings/ERRORS.md`，用于后续批次开发前自检；该记录不属于产品 Runtime 契约。
- 已知问题或限制：HITL 仍通过 Application Gate/Permit 与异常控制流衔接，尚未持久化完整
  Runtime Checkpoint；`ChatExecutionContext` 仍承担 Run 级共享预算；`EXPLICIT_ONLY` 与
  `MODEL_ROUTED` Skill 尚未进入真实 Runtime。
- 剩余开发计划：Batch 3–7 未完成；下一批先迁移 HITL、Checkpoint 和恢复，建立
  `AgentRuntime.resume`，保证审批恢复不重新路由、规划或重复执行 Tool；随后依次处理
  Supervisor typed structured output、Plan-and-Execute、Workflow/Application 边界和 Skill 全模式验收。
- 下批入口条件：开始 Batch 3 前重新检查三个仓库状态与本记录，先回归 Batch 2 的无 Tool、
  Tool/Skill、幂等/预算/事件、全量 Server `verify` 和 Application 真实启动，通过后再修改
  HITL/Checkpoint 生产代码。

## 2026-08-18 08:16 Batch 3 START

- 分支与工作区：Server/Admin/Web 均为 `main...origin/main`；Server/Admin 只有 Batch 0–2
  已确认的未提交改动，Web 干净，未发现来源不明的新改动；未执行 Git 暂存、提交或推送。
- 开发前本地进度：已重新完整读取工作区根目录和 Server `AGENTS.md`，检查三个仓库状态、
  Batch 2 DONE 记录、Batch 3 目标及 HITL 历史约束；确认 PostgreSQL 是恢复事实源，审批必须绑定
  Tool 参数指纹和冻结版本，MQ 只负责唤醒，外部副作用成功后不得自动重放。
- 开发过程预防：按本地 `.learnings/ERRORS.md` 复查 Mockito 嵌套 stubbing 与漏 import 的
  复发记录；本批复杂 Mock 先独立构造，新增类型后先做小范围编译。
- 上批回归命令与结果：执行中；先回归 `AgentRuntimeTest`、`ExecutionStateTest`、
  `RuntimeToolCallbackTest`、`SpringAiStreamingChatGatewayTest`、`RuntimeAdvisorIntegrationTest`、
  `ApplicationRuntimeEventSinkFactoryTest` 和 `AgentChatServiceImplTest`，再执行全量 `verify.sh`
  与 Application 真实启动。
- 本批目标：将人工审批从异常/Gate-Permit 控制流收为 Runtime typed interruption 与持久化
  Checkpoint，建立 `AgentRuntime.resume`，保证 approve/reject、重复决定、过期、取消、MQ 重投
  和进程重启均恢复同一 State，不重新路由、规划或重复执行 Tool。
- 本批不做：不迁移 Supervisor Planner、Plan-and-Execute、Workflow 图执行或 Skill 新激活模式；
  不引入 Spring AI Alibaba 依赖。
- 契约、Schema、配置与数据预期：优先复用现有 Interaction/Run/Event 表及事务消息；如现有表
  无法完整持久化 Runtime Checkpoint，则通过新增迁移脚本直接演进，不保留旧恢复兼容分支。
- 剩余开发计划：Batch 3–7；当前只进入 Batch 3 回归门禁，尚未修改 HITL/Checkpoint 生产代码。

## 2026-08-18 08:50 Batch 3 DONE

- 分支与工作区：Server/Admin/Web 仍为 `main...origin/main`；保留 Batch 0–2 已确认的
  Server/Admin 未提交改动，Web 干净；本批未执行 Git 暂存、提交或推送。
- 开发前本地进度：重新完整读取工作区根目录和 Server `AGENTS.md`，检查三个仓库、
  Batch 2 DONE 与本地错误记录；确认旧 HITL 由 Application Gate/Permit、异常控制流和简化
  Checkpoint Payload 组成，是本批直接替换范围。
- 上批回归命令与结果：先完成 Batch 2 的 Runtime Tool/Skill、事件、预算、Application 调用
  精确回归，再执行 `./mvnw -pl application -am test`、全量 `./scripts/verify.sh` 和 Application
  真实启动；门禁均通过后才修改 Batch 3 生产代码。
- 实际开发内容：
  - 在 `agent-runtime` 建立 `RuntimeCheckpoint` V2、`ExecutionStateSnapshot`、
    `RuntimeCheckpointContext`、`RuntimeInterruption`、`RuntimeCheckpointStore` 和
    `AgentResumeRequest`，完整冻结 Agent/Skill/Tool 授权、策略、预算、Token、事件序号、
    Tool Ledger、Spring AI Message 历史及原始 pending `ToolCall`；V1 Application 简化载荷直接拒绝，
    不做迁移读取；
  - `HumanApprovalHook` 在 Spring AI `ToolCallingManager` 前持久化中断，`AgentRuntime.run/resume`
    返回强类型 Completed/Interrupted；恢复直接还原同一 `ExecutionState` 和原始 Tool Call，
    不重新路由、规划或重放首轮模型调用；恢复前先检查运行时限、版本、Skill、Tool 配置哈希、
    资源范围、参数与审批指纹；
  - Application 的 `ApplicationRuntimeCheckpointStore` 在同一事务内写入 Checkpoint、
    pending invocation、Interaction 和 `WAITING_HUMAN`；批准后以 CAS 领取 invocation，Tool 成功后
    原子关闭 invocation/checkpoint/interaction；MQ 仍只负责唤醒，PostgreSQL 是恢复事实源；
  - 外部 Tool 副作用成功但本地确认失败时，运行与 invocation 进入
    `RECONCILIATION_PENDING`，客户端收到不可重试的安全失败，禁止自动重复执行外部操作；
  - Runtime 增加 `HUMAN_REQUIRED`、`HUMAN_RESOLVED`、`RUN_RECONCILIATION_PENDING` 事件；
    Application 继续只负责先持久化、后投影 Timeline/SSE，且先将 Assistant 落为
    `WAITING_HUMAN` 再发布可操作卡片；
  - Application resume 只从可信 Checkpoint 恢复锁定版本与最小上下文，跳过 Memory 加载、
    Query Rewrite、Intent Routing 和重新规划；Workflow Agent 节点明确拒绝 Agent 内部 HITL，
    Workflow 级人工交互继续由独立交互节点负责。
- 删除/合并的旧路径：删除 `HumanInteractionRequiredException`、`AgentToolApprovalGate`、
  `AgentToolApprovalPermit`、`AgentToolApprovalRequest`、Application 的 `ToolApprovalGateFactory`、
  `ToolApprovalTxService`、旧 `ToolApprovalFingerprint`、`ToolApprovalAuthorization`、
  `ExecutionCheckpointPayload` 和 `HitlApprovalRef`；删除 Chat 层无调用的旧交互事件转换方法，
  未保留别名、适配器、双写或兼容分支。
- 契约、Schema、配置与数据变更：Runtime/Application Java 合同直接替换；Checkpoint JSON 升级为
  V2 完整载荷，运行/调用/事件增加人工中断和待对账状态。相关数据库列均为无 CHECK 的现有
  `VARCHAR/JSONB`，无需新增迁移；对外交互卡片字段保持不变，无前端改动和部署配置变更。
- 收束回归命令与结果：
  - HITL/Checkpoint/恢复与事件定向回归 45 项通过；另有 Checkpoint Fastjson 完整往返、
    待对账失败分类和 Chat typed pause 等 24 项组合回归通过；
  - `./mvnw -pl application -am test` 共 401 项测试通过，0 失败、0 错误、1 项环境型跳过；
    其中 Runtime 29 项、Application 286 项；
  - `./scripts/verify.sh` 沙箱内仅因 Admin HTTP 测试无权绑定临时端口失败；在允许本地端口的
    环境中重跑后八个 Maven 模块全部 `SUCCESS`，Admin 202 项、Application 286 项、
    Runtime 29 项和 MCP 协议集成均通过；
  - `git diff --check` 通过；生产/Maven 中无旧模块包名、旧 Gate/Permit/异常/简化载荷，
    `agent-runtime` 无 Application/Workflow 反向依赖，`infra-ai`/`framework` 无 Runtime 依赖，
    且未引入 Spring AI Alibaba 依赖。
- 手工/真实链路验证：使用本批全量构建产物在随机端口 51340 启动 Application 成功；
  Spring 上下文、Runtime/Checkpoint Store、PostgreSQL、RocketMQ 生产者与四个消费者、MinIO
  均装配成功；验证后 Tomcat、消费者和 Hikari 连接池均优雅停机，无遗留进程。
- 开发过程纠错：修复两处开发期问题——Java nullable 泛型列表默认值类型推断、Mockito
  对两个非同一 Agent 快照做参数匹配；均已写入本地 `.learnings/ERRORS.md`。另为 Specialist
  Runtime 空结果增加明确业务失败保护，避免不透明 NPE。
- 已知问题或限制：Supervisor Planner、Plan-and-Execute、Workflow 执行迁移、Memory 边界收口
  及 `EXPLICIT_ONLY`/`MODEL_ROUTED` Skill 仍未开始；本批未用真实高风险外部 Tool 写入开发数据，
  但 Spring AI 原始 Tool Call 暂停/恢复/执行链已通过真实 `DefaultToolCallingManager` 集成测试。
- 剩余开发计划：Batch 4–7 未完成；下一批先将 Supervisor 的决策生成与校验迁入 Runtime，
  最大程度复用 Spring AI typed structured output、`BeanOutputConverter` 与受 Runtime 预算约束的
  Advisor，删除 Application `completeJson`/`JSONObject` 协议和 `ExecutionDecisionParser`；之后再
  依次完成 Plan-and-Execute、Workflow/Application 边界和 Skill 全模式验收。
- 下批入口条件：开始 Batch 4 前重新读取根目录与 Server `AGENTS.md`、检查三个仓库和本记录，
  先重跑本批 Runtime HITL/Checkpoint/恢复、Application 交互/对账、全量 `verify.sh` 与
  Application 真实启动；全部通过后再修改 Supervisor Planner 生产代码。

## 2026-08-18 08:52 Batch 4 START

- 分支与工作区：Server/Admin/Web 均为 `main...origin/main`；Server/Admin 只有 Batch 0–3
  已确认的未提交改动，Web 干净；未执行 Git 暂存、提交或推送。
- 开发前本地进度：已重新完整读取工作区根目录与 Server `AGENTS.md`，确认无子目录附加规则；
  重新检查三个仓库、Batch 3 DONE 和 Batch 4 计划，未发现来源不明的新改动。
- 上批回归命令与结果：Runtime HITL/Checkpoint/恢复 24 项、Application 交互/恢复/过期/失败分类
  30 项，共 54 项精确测试通过；Batch 3 收束时完成的八模块 `verify.sh` 与 Application 真实启动
  均通过，因此 Batch 4 门禁为 `PASS`。
- 本批目标：把 Supervisor 的“决策生成与校验”迁入 `agent-runtime`，最大程度合理复用 Spring AI
  typed structured output、`BeanOutputConverter` 与受 `ExecutionState` 模型/Token 预算约束的 Advisor；
  统一 `PLAN_CREATED`/`REPLANNING` 事件和决策 Checkpoint。
- 本批不做：不迁移完整 Plan-and-Execute 步骤执行、子 Agent/Workflow 委派、最终综合或 Workflow 图执行；
  不引入 Spring AI Alibaba 依赖。
- 预计删除/合并：Application 的 `completeJson`/`JSONObject` Planner 运行协议和
  `ExecutionDecisionParser`；保留能力编码、步骤依赖、参数来源、风险和预算业务 Validator，
  将其语义迁入 Runtime 后删除旧入口，不保留双路径。
- 契约、Schema、配置与数据预期：直接替换 Runtime/Application Java 合同；优先复用现有运行、
  计划和事件表，预期无对外 API、前端、Schema 或部署配置变更。
- 本批验收：合法决策、结构错误、自修复、未知字段、未授权能力、错误步骤引用、预算超限、
  决策事件与 Checkpoint 均在能力执行前收口；完成精确测试、Application 反应堆、全量 verify、
  静态边界审计和 Application 真实启动。
- 剩余开发计划：Batch 4–7；本批完成后进入 Batch 5 的 Plan-and-Execute 与能力边界迁移。

## 2026-08-18 09:17 Batch 4 DONE

- 分支与工作区：Server/Admin/Web 仍为 `main...origin/main`；保留 Batch 0–3 已确认的
  Server/Admin 未提交改动，Web 干净；本批未执行 Git 暂存、提交或推送。
- 开发前本地进度：重新完整读取工作区根目录与 Server `AGENTS.md`，检查三个仓库、
  Batch 3 DONE、本地错误记录和 Batch 4 入口；先完成 Runtime HITL/Checkpoint/恢复与
  Application 交互/对账精确回归，确认上批门禁通过后才迁移 Planner 生产代码。
- 上批回归命令与结果：Runtime 24 项与 Application 30 项定向测试共 54 项通过；Batch 3
  完整 `verify.sh` 和真实 Application 启动均通过，未发现上批回归。
- 实际开发内容：
  - 在 `agent-runtime` 增加唯一 `PlannerRuntime`，将 Supervisor 决策生成、结构转换、业务校验、
    有界修复、预算和 `PLANNING/PLAN_CREATED/REPLANNING` 事件收入同一 `ExecutionState`；
  - 采用 Spring AI `ChatClient.responseEntity`、`BeanOutputConverter<PlannerDecision>` 和官方
    `StructuredOutputValidationAdvisor`；Advisor 顺序固定为 Runtime Context、结构校验、Runtime Model，
    确保每次结构重试仍消耗 Runtime 模型/Token 预算并产生统一模型事件；
  - 模型实例已声明 `STRUCTURED_OUTPUT` 能力时启用 provider-native `JSON_SCHEMA`，其他兼容模型
    使用 `BeanOutputConverter` 生成的格式指令；能力从既有 `capabilities` JSONB 投影至
    `ChatModelRuntimeConfig`，不再按 Provider 名称猜测；
  - 新建 Runtime typed `PlannerDecision/PlannerPlan/PlannerStep/PlannerClarification` 与 Validator，
    严格拒绝未知字段、未授权能力、非法步骤依赖和 Planner 越权确认能力；失败修复达到预算后
    返回安全澄清，不把不可信模型输出直接交给执行器；
  - Application `PlannerServiceImpl` 收为锁定版本/模型配置/Prompt/能力目录/历史与 Runtime 上下文
    组装、Runtime 调用和结果转发；计划事件由 Runtime 唯一发布，Application Event Sink 只负责
    先落事件，再投影 Timeline/SSE；既有计划快照继续承接 typed 决策结果落库。
- 删除/合并的旧路径：删除 Application `ExecutionDecision`、`ExecutionPlan`、`PlanStep`、
  `PlannerResult`、旧 Validator/Parser 等重复协议；删除 `ChatCompletionGateway.completeJson` 和
  Fastjson `JSONObject` Planner 路径；没有保留别名、适配器、JSON_OBJECT 兜底或双执行路径。
- 契约、Schema、配置与数据变更：Runtime/Application Java 合同直接替换；复用模型实例既有
  `capabilities` JSONB 和运行/计划/事件表，无数据库迁移、对外 API、前端或部署配置变更。
- 收束回归命令与结果：
  - `./mvnw -pl agent-runtime -am test` 通过：Framework 51 项（1 项环境跳过）、Infra AI 26 项、
    Runtime 37 项；覆盖 typed 决策、未知字段、业务修复、预算降级和 native/portable 结构输出；
  - `./mvnw -pl application -am test` 通过：Application 274 项，Planner Adapter、计划执行、
    快照持久化、事件投影和既有 Agent/HITL 链均无失败；
  - `./scripts/verify.sh` 在沙箱外完整通过，8 个 Maven 模块全部 `SUCCESS`，合计 600 项测试、
    0 失败、0 错误、1 项既有环境跳过；
  - `git diff --check` 通过；生产/Maven 中无旧模块/包名、`completeJson`、JSON_OBJECT、
    Application 旧 Planner 类型或 Spring AI Alibaba 依赖，`agent-runtime` 无 Application/Workflow
    反向依赖。
- 手工/真实链路验证：使用本批重新打包的 Application 在随机端口 51341 启动成功；
  Spring 上下文、Planner Runtime、PostgreSQL、RocketMQ 生产者与四个消费者、MinIO 均完成装配；
  验证后 Tomcat、消费者和 Hikari 连接池优雅停机，无遗留进程。
- 开发过程纠错：第一次直接运行 Jar 未加载本地密钥环境，按 `SecretCipher` 启动校验失败；
  改为仅向当前验证进程注入临时随机主密钥后启动成功，问题已记入 `.learnings/ERRORS.md`。
- 已知问题或限制：完整 Plan-and-Execute 步骤调度、子 Agent/Workflow 委派、重规划执行和最终综合
  仍位于 Application，按计划在 Batch 5 迁移；本批只迁移决策生成与校验，未伪造完整计划恢复；
  `EXPLICIT_ONLY`/`MODEL_ROUTED` Skill 和最终 Workflow/Application 边界仍未处理。
- 剩余开发计划：Batch 5–7 未完成；下一批迁移 Plan-and-Execute 的迭代、步骤状态、能力委派、
  重规划和最终综合到 Runtime，再依次收口 Workflow/Application 边界、Memory 与 Skill 全模式验收。
- 下批入口条件：开始 Batch 5 前重新检查三个仓库状态、Batch 4 DONE 与本地错误记录；先回归
  typed Planner、结构重试预算、计划事件/快照、全量 `verify.sh` 和 Application 真实启动，全部通过后
  再修改 Plan-and-Execute 生产代码。
