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

## 2026-08-21 14:52 Batch 5 START

- 分支与工作区：Server 为 `agent/agent-runtime-convergence...origin/agent/agent-runtime-convergence`，
  已提交 Batch 0–4；Admin 为 `agent/admin-skill-auth...origin/agent/admin-skill-auth`、Web 为
  `main...origin/main`，两者均干净。Server 另有 21 个未提交文件，经归类主要为注释/格式调整，
  其中 `.gitignore` 新增 `docs`、Chat Generation 稳定事件 code 改为小写属于未记录语义变更，
  本批视为既有改动保护，不纳入 Plan-and-Execute 设计，也不直接覆盖。
- 开发前本地进度：已重新完整读取根目录和 Server `AGENTS.md`，复查 Batch 4 DONE、Batch 5
  出入口及 `.learnings`；确认 Application 仍持有计划循环、步骤状态、能力委派、重规划和最终综合，
  Runtime 当前只持有 typed Planner 决策生成与校验。
- 上批回归命令与结果：Planner/Runtime/Application 9 个聚焦测试类共 40 项通过；
  `./scripts/verify.sh` 八模块全部 `SUCCESS`，共 600 项测试、0 失败、0 错误、1 项既有环境跳过。
  使用全量构建产物启动 Application 时已完成 Web、PostgreSQL、MinIO 和 RocketMQ Bean 装配，
  但配置的 `192.168.86.2:9876` 当前拒绝连接，MQ 消费者生命周期启动失败；本机无 Docker CLI，
  无法通过仓库 Compose 恢复依赖。该外部环境限制已记录到 `.learnings/ERRORS.md`，不通过关闭
  消费者或增加静默降级绕过，依赖恢复后补做成功启动。
- 本批目标：在 `agent-runtime` 建立唯一 Plan-and-Execute 运行入口，统一步骤隔离状态、共享 Run
  预算、能力调用、失败分类、有界重规划、副作用去重、Partial 收口和禁用 Tool 的最终综合；
  Application 收为锁定版本/Prompt/上下文准备、Runtime 能力适配、事件/快照转发与结果落库。
- 本批不做：不迁移 Workflow 图协议与实现，不删除 `ChatExecutionContextRegistry`，不实现
  `EXPLICIT_ONLY`/`MODEL_ROUTED` Skill，不引入 Spring AI Alibaba 依赖，不处理上述两个无来源
  的语义改动。
- 契约、Schema、配置与数据预期：在 Runtime 定义稳定的 `RuntimeCapabilityInvoker` 边界和
  typed 计划执行模型，Application 将锁定的 Agent/Workflow/Tool 适配为该边界；复用既有运行、
  计划、步骤和事件表，预期无对外 API、Schema、部署配置或开发数据写入。
- 剩余开发计划：Batch 5–7；本批完成后依次处理 Workflow/Application/Memory 边界和 Skill
  全激活模式与最终清理验收。

## 2026-08-21 15:17 Batch 5 DONE

- 分支与工作区：Server 仍为
  `agent/agent-runtime-convergence...origin/agent/agent-runtime-convergence`；Admin 的
  `agent/admin-skill-auth` 与 Web 的 `main` 均保持干净。本批未暂存、提交或推送；开发前识别的
  `.gitignore` 与 Chat Generation code 语义改动继续按既有改动保护，未混入本批设计处理。
- 开发前本地进度与上批回归：重新读取根目录和 Server `AGENTS.md`、检查三个仓库、Batch 4 DONE
  与本地错误记录；Planner/Runtime/Application 聚焦回归 40 项、八模块 `verify.sh` 600 项全部通过后
  才开始迁移。开发前 Application 启动仅受 RocketMQ 临时拒绝连接限制，未用禁用消费者或降级绕过。
- 实际开发内容：
  - 在 `agent-runtime` 建立唯一 `SupervisorRuntime`，统一初始规划、串行计划执行、直接能力执行、
    有界重规划、预算边界、Partial/Clarification/Interruption 和最终综合；Planner 继续使用同一个
    `ExecutionState`，模型调用由 Spring AI `ChatClient`、`RuntimeContextAdvisor` 与
    `RuntimeModelAdvisor` 统一记账；最终综合显式关闭 Tool 自动注册；
  - 建立具体 `RuntimeCapabilityInvoker`、`RuntimeCapability`、`AgentInvocationState`、
    `RuntimePlanStore` 与 typed Supervisor 请求/结果；每个步骤隔离输入和局部终态，同时把子调用实际
    模型、Tool、Token 和最后事件序号合并进共享 Run 预算与单调事件序列；
  - Agent、Workflow、Tool 三类锁定能力均通过同一边界调用；步骤输入只由当前任务与已完成依赖结果
    编译，Planner 不能提交自由参数。能力失败按稳定错误分类，有界重规划；写能力相同参数只复用结果，
    不重复消耗能力调用/委派额度，不同参数直接拒绝；
  - 新增 `STEP_STARTED/REUSED/COMPLETED/FAILED`、`CLARIFICATION_REQUIRED` 和 Agent 调用终态事件；
    以 `ExecutionScope.RUN/INVOCATION` 区分根运行和 Supervisor 子 Agent，子调用不再提前发出整次
    Run 的完成、部分完成、失败或取消事件；
  - 写能力已经完成但步骤事实落库或结果预算确认失败时统一进入
    `RUN_RECONCILIATION_PENDING`，禁止作为普通失败重试；Application 的能力执行边界也按
    `CapabilityRiskLevel` 保留该语义；
  - Application 的 `SupervisorAgentExecutor` 只准备发布 Prompt、模型配置、能力目录、历史与预算，
    将锁定能力适配给 Runtime，并转发事件、澄清/HITL 和最终结果；`ApplicationRuntimePlanStore`
    把 typed 计划与步骤终态追加到现有事实表。
- 删除/合并的旧路径：删除 Application 的 `PlannerService*`、`PlanExecutionService*`、
  `PlannerRuntimeCoordinator*`、`FinalSynthesisService*`、`PlanInputCompiler` 及其重复 Request/Result/
  Context/Step 模型和旧单元测试；Planner 动作直接更名为 `EXECUTE_CAPABILITY`，没有别名、双路径、
  fallback 或旧运行时兼容逻辑。
- 契约、Schema、配置与数据变更：Runtime/Application Java 契约直接替换；步骤增加
  `INTERRUPTED/REUSED`，事件增加步骤、澄清和 Agent 终态，复用现有无 CHECK 约束的
  `VARCHAR/JSONB` 事实表，无数据库迁移、对外 API、前端、部署配置或开发数据写入；未引入
  Spring AI Alibaba 依赖。
- 收束回归命令与结果：
  - 聚焦回归 Runtime 15 项、Application 18 项通过，覆盖直接能力、写副作用复用、重规划、待对账、
    Invocation 生命周期隔离、步骤事件投影与计划事实追加；
  - `./mvnw -pl application -am test` 通过：Framework 51 项（1 项既有环境跳过）、Infra AI 26 项、
    Workflow Core 9 项、Agent Runtime 42 项、Application 266 项，0 失败、0 错误；
  - `./scripts/verify.sh` 沙箱外完整通过，八个 Maven 模块全部 `SUCCESS`，合计 597 项测试、
    0 失败、0 错误、1 项既有环境跳过；
  - `git diff --check` 通过；静态审计确认无 Application 旧 Plan/FinalSynthesis 运行类、无 Planner
    `EXECUTE_AGENT` 旧动作、无 Spring AI Alibaba 依赖、`agent-runtime` 无 Application/Workflow
    反向依赖，生产代码无新增直接 `ChatModel.call/stream` 路径。
- 手工/真实链路验证：依赖恢复后使用本批八模块构建产物在端口 51343 启动 Application 成功；
  Spring 上下文、Supervisor Runtime、PostgreSQL、RocketMQ 生产者与四个消费者、MinIO 均完成装配，
  Hikari 建立真实数据库连接；验证后通过 `SIGINT` 停止进程，无遗留验证进程。开发前 RocketMQ
  外部阻塞已关闭。
- 开发过程纠错：Mockito 嵌套 stubbing、重复目标补丁、猜测源码路径和带引号 glob 四类问题均记录在
  本地 `.learnings/ERRORS.md` 并已修复；未通过绕过测试或放宽业务校验处理。
- 已知问题或限制：Workflow 图执行协议与 LangGraph4j 实现仍在 Application，
  `ChatExecutionContextRegistry` 仍是 Workflow Agent 节点的进程内侧载上下文；Application 尚未完全
  收到目标的五项职责。`EXPLICIT_ONLY`、`MODEL_ROUTED` Skill 与最终真实发布版本端到端验收留待
  Batch 7。
- 剩余开发计划：Batch 6–7 未完成。Batch 6 迁移 Workflow 执行边界到 `workflow-core`、让 Workflow
  Agent 节点显式调用 Runtime、删除进程内上下文注册表并增加架构约束测试；Batch 7 完成 Skill 全模式、
  旧契约清理和三个仓库最终回归。
- 下批入口条件：开始 Batch 6 前重新检查三个仓库和本记录，先回归本批 Supervisor Runtime、能力边界、
  写副作用对账、步骤事件/事实、八模块 `verify.sh` 与 Application 真实启动，通过后再迁移 Workflow。

## 2026-08-21 15:22 Batch 6 START

- 分支与工作区：Server 为 `agent/agent-runtime-convergence`，保留已完成但未提交的 Batch 5 与开发前
  已识别既有改动；Admin `agent/admin-skill-auth`、Web `main` 均干净。本批不暂存、提交或推送。
- 开发前本地进度：Batch 5 已有 DONE 记录；Application 仍持有 Workflow LangGraph4j 引擎、运行监听、
  Agent 节点适配和 `ChatExecutionContextRegistry`，这是本批迁移对象。`agent-runtime` 已完成唯一
  Supervisor Plan-and-Execute 与能力边界，可作为 Workflow Agent 节点的明确调用目标。
- 上批回归命令与结果：聚焦 Runtime 15 项、Application 18 项通过；
  `./mvnw -pl application -am test` 五模块 394 项通过（1 项既有环境跳过）；
  `./scripts/verify.sh` 八模块 597 项全部通过（1 项既有环境跳过）；Application 在 51343 真实启动成功，
  PostgreSQL、RocketMQ、MinIO 和 Runtime Bean 均完成装配，验证进程已停止。
- 本批目标：Workflow 保持独立图执行域，将执行协议与 LangGraph4j 实现迁入 `workflow-core`；Workflow
  Agent 节点通过显式请求边界调用 Agent Runtime，删除进程内侧载上下文注册表；瘦身 Application 的
  Workflow/能力编排并增加架构约束测试。
- 本批不做：不把 Workflow 图状态并入 `ExecutionState`，不把 Application 业务 Entity/Repo/权限迁入
  `workflow-core`，不实现 Skill 新激活模式，不引入 Spring AI Alibaba 依赖，不处理受保护既有语义改动。
- 契约、Schema、配置与数据预期：优先迁移纯 Workflow 协议、引擎和可注入节点调用边界；Application
  只实现版本加载、权限/上下文适配、事件与结果持久化。预期无对外 API、Schema 或部署配置变更。
- 剩余开发计划：Batch 6–7；当前开始 Batch 6 架构盘点与迁移。

## 2026-08-21 15:31 Batch 6 DONE

- 分支与工作区：Server 仍为 `agent/agent-runtime-convergence...origin/agent/agent-runtime-convergence`；
  Admin `agent/admin-skill-auth`、Web `main` 均保持干净。本批保留 Batch 5 与开发前识别的既有改动，
  未暂存、提交或推送。
- 开发前本地进度与上批回归：检查三个仓库、Batch 5 DONE、根目录与 Server `AGENTS.md` 以及本地
  错误记录；先确认 Batch 5 聚焦测试、Application Reactor、八模块 verify 和真实启动结果均通过，
  再迁移 Workflow 生产代码。
- 实际开发内容：
  - 将 Workflow 执行请求/结果、节点调用协议、运行监听协议、运行上下文、节点 Trace、图状态与
    LangGraph4j 执行器整体迁入 `workflow-core`；LangGraph4j 依赖随唯一实现迁入 Core，Application
    不再直接依赖或导入图引擎；
  - 新增三字段 `WorkflowExecutionPorts`，每次执行显式绑定 Agent 节点、意图识别节点和运行监听器；
    `LangGraphWorkflowExecutor` 只保留无状态 Validator，不再把 Application 回调固化在 Spring 单例；
  - 新增具体 `ApplicationWorkflowExecutionPortsFactory`，使用当前 `ChatExecutionContext` 创建本次
    Workflow 的 Agent 调用闭包与事实/SSE 监听器；`ChatAgentNodeInvoker` 改为显式接收上下文，删除
    `ChatExecutionContextRegistry` 及 Application Chat 的注册/清理分支；
  - Workflow Run、Event、Attempt 的 Entity/Repo/事务服务继续留在 Application；三个数据库枚举
    保留 `@EnumValue`，Core `WorkflowNodeStatus` 不携带 ORM 注解，Attempt 落库前显式转换持久化状态；
  - 增加 `WorkflowRuntimeBoundaryTest`，固定图执行器无 Application 回调字段、公开无参构造、
    `execute` 必须携带每次执行端口，以及 Core 状态不依赖持久化框架；端口工厂测试覆盖上下文绑定、
    事件持久化优先和 SSE 失败不篡改运行事实。
- 删除/合并的旧路径：删除 Application 内 Workflow 图引擎、图状态、执行协议/端口重复类、旧
  `WorkflowRunListenerImpl` 和进程级 `ChatExecutionContextRegistry`；没有保留兼容构造器、旧
  `execute(definition, request)` 重载、别名、回退分支或第二套 Workflow 状态。
- 契约、Schema、配置与数据变更：直接替换模块内 Java 合同；Workflow `ExecutionState` 与 Agent
  `ExecutionState` 继续保持独立边界；数据库表、API、前端、迁移和部署配置无变化；未引入 Spring AI
  Alibaba 依赖。
- 收束回归命令与结果：
  - 7 个 Workflow/Application 聚焦测试类共 28 项通过，覆盖图分支、端口显式绑定、Agent 上下文、
    Run/Node 事实、SSE 异常和 Application Chat 回归；
  - `./mvnw -pl application -am test` 通过：Framework 51 项（1 项既有环境跳过）、Infra AI 26 项、
    Workflow Core 15 项、Agent Runtime 42 项、Application 263 项，共 397 项，0 失败、0 错误；
  - `./scripts/verify.sh` 在沙箱外完整通过，8 个模块全部 `SUCCESS`，共 600 项测试、0 失败、0 错误、
    1 项既有环境跳过；
  - `git diff --check` 通过；静态审计确认 Workflow Core 无 Application/Chat/MyBatis/Spring/Hutool
    反向依赖，Application 无 LangGraph4j 直接依赖、旧 Workflow 引擎文件或上下文注册表。
- 手工/真实链路验证：尝试启动本批真实 Application 前检查到 PostgreSQL、RocketMQ、MinIO 均未监听，
  随后 `./scripts/dev-start.sh` 因当前 shell 无 `docker` 命令而无法恢复依赖；未通过禁用消费者、伪造
  Bean 或静默降级绕过。Batch 5 同一分支在本批开发前已完成完整真实启动，本批外部环境限制已记录到
  `.learnings/ERRORS.md`，恢复 Docker 后需补做 Workflow 真实路径验收。
- 已知问题或限制：`EXPLICIT_ONLY`、`MODEL_ROUTED` Skill 尚未接入统一运行态选择和真实发布路径；
  Memory 仍需做最终归属审计，Application 还要按目标五项职责完成旧契约清理；本批没有把 Workflow
  图状态错误并入 Agent `ExecutionState`。
- 剩余开发计划：仅 Batch 7 未完成。下一批完成 Skill 全激活模式、Memory/Hook/Interceptor/Tool/HITL
  最终边界审计，删除剩余旧契约，执行三个仓库静态检查、后端全量 verify，并在外部依赖可用时完成
  发布版本 Application -> Supervisor/Workflow -> Agent -> Tool -> audit 的真实链路验收。
- 下批入口条件：开始 Batch 7 前重新检查三个仓库和本记录，先回归本批 Workflow Core、显式端口、
  Run 事实与 Batch 5 Supervisor Runtime；依赖可用时优先补做 Application 真实启动，再修改 Skill 与
  最终边界生产代码。

## 2026-08-21 15:32 Batch 7 START

- 分支与工作区：Server 为 `agent/agent-runtime-convergence...origin/agent/agent-runtime-convergence`，保留
  Batch 5/6 与开发前识别的既有改动；Admin `agent/admin-skill-auth`、Web `main` 均干净。三个仓库
  均未发现来源不明的新改动，本批不暂存、提交或推送。
- 开发前本地进度：重新检查根目录与 Server `AGENTS.md`、Batch 6 DONE 和本地错误记录；`git diff
  --check` 通过。外部依赖仍因当前 shell 无 Docker CLI 无法恢复，本批先执行不依赖外部服务的确定性
  回归，不通过关闭消费者、伪造 Bean 或兼容分支绕过真实启动。
- 上批回归：重新执行 Workflow Core 图引擎/边界、Application 显式端口、Workflow Run 事实、Agent
  节点、Capability 和 Application Chat 共 7 个聚焦测试类，28 项全部通过，0 失败、0 错误。
- 本批目标：在锁定 AgentVersion 范围内贯通 `ALWAYS_ON`、服务端校验的 `EXPLICIT_ONLY` 与 Runtime
  模型选择的 `MODEL_ROUTED`；模型路由只披露 code/name/description，选中后才把完整 instructions
  激活进统一 `ExecutionState`，并让 HITL Checkpoint 恢复同一冻结 Skill 集。随后审计 Memory、Hook、
  Interceptor、Tool、HITL 与 Application 五项职责，删除剩余旧契约并执行三个仓库收束回归。
- Spring AI 取舍：模型路由复用 Spring AI `ChatClient`、typed structured output、Advisor 和统一模型预算；
  当前单 Agent 冻结 Skill 上限为 3，目录规模未达到 Tool Search Advisor 的收益阈值，本批不引入其额外
  检索链路，并以架构测试固定该决定。未引入 Spring AI Alibaba 依赖。
- 契约预告：Chat 请求将增加可选显式 Skill code 列表，Checkpoint JSON 协议直接升级且不保留旧版本
  读取兼容；数据库表暂不变。若开发中发现 Schema、配置或跨服务契约变化，将在 DONE 中逐项记录。
- 剩余开发计划：Batch 7 正在进行；完成后本次 Agent Runtime 内核收束计划无后续开发批次，仅保留
  外部依赖恢复后的真实启动与发布版本端到端验收（若本批环境仍不具备）。

## 2026-08-21 15:54 Batch 7 DONE

- 分支与工作区：Server 仍为
  `agent/agent-runtime-convergence...origin/agent/agent-runtime-convergence`，Admin 为
  `agent/admin-skill-auth...origin/agent/admin-skill-auth`，Web 为 `main...origin/main`。三个仓库均保留
  本轮未提交改动，本批未暂存、提交或推送；Server 开始前识别的 `.gitignore` 与 Chat Generation
  code 既有语义改动继续原样保护。
- 开发前本地进度与上批回归：重新检查三个 Git 仓库、根目录与各目标仓库 `AGENTS.md`、Batch 6
  DONE 及本地纠错记录；先重跑 Workflow Core 图引擎/边界、Application 显式端口、Workflow Run
  事实、Agent 节点、Capability 和 Application Chat 共 28 项，全部通过后才进入 Skill 收束。
- Runtime 与 Spring AI 收束：
  - `AgentRuntime` 在统一生命周期内调用 `SkillHook`；`ALWAYS_ON`、服务端校验的 `EXPLICIT_ONLY`、
    Runtime 模型选择的 `MODEL_ROUTED` 都写入同一 `ExecutionState`、单调 `SKILL_ACTIVATED` 事件与
    模型/Token 预算；HITL Resume 不重复路由；
  - `SkillRouter` 使用 Spring AI `ChatClient`、`BeanOutputConverter`、供应商原生 JSON Schema（支持时）、
    `StructuredOutputValidationAdvisor`、`RuntimeContextAdvisor` 和 `RuntimeModelAdvisor`；路由 Prompt 只披露
    code/name/description，不泄露未激活 instructions，关闭 Tool 自动注册，并把最多一次结构化修复纳入
    同一模型调用额度；
  - AgentVersion 冻结目录上限固定为 3，因此未引入收益不足的 Tool Search 检索链路；无 Tool 的
    MODEL_ROUTED 路径为路由、可选修复、最终回答保留至多 3 次模型调用，有 Tool 时继续由统一 Tool Loop
    使用剩余额度；
  - 生命周期扩展仍使用具体 `SkillHook`、`HumanApprovalHook`；单次模型拦截使用 Spring AI Advisor，
    Tool 拦截使用受控 `RuntimeToolCallback`，没有新增泛化 Interceptor、空接口或一对一抽象；
  - Memory 保持为 Application 加载、裁剪、持久化的可信入口上下文，Runtime 只消费冻结 Prompt/历史/
    摘要/长期记忆。没有套用 Spring AI ChatMemory Advisor，因为 Lingxi 的发布版本、上下文预算、Tool
    消息与 HITL Checkpoint 恢复语义不能由进程级 ChatMemory 替代。
- Application、API 与控制面收束：
  - `SkillRuntimeService` 只校验数据库当前状态、版本归属、Schema/checksum、Tool 权限交集与 Token 预算，
    向 Runtime 返回冻结 `SkillRuntimeCatalog`，不再决定激活；Skill 激活事件先原子落事件与审计，再转发
    SSE；
  - Chat API 增加最多 3 个可选 `skillCodes`；显式 Skill 只允许直接 SPECIALIST 目标，且必须属于该
    AgentVersion 锁定的 `EXPLICIT_ONLY` 目录。Workflow/Supervisor 显式选择直接拒绝，避免跨多个 Agent
    猜测归属；Web 请求合同同步支持该字段；
  - Admin Agent 绑定允许三种激活模式，编辑器和版本查看器展示实际策略，不再把所有 Skill 硬编码为
    自动启用；全局选择接口仍只暴露已发布、系统预置且系统可见的 Skill，不放宽用户/私有归属边界；
  - Checkpoint 协议直接升级到 V3，保存显式 code 与 Skill 激活原因，恢复时使用同一冻结目录校验
    mode/reason/checksum/必选集合；不读取旧 Payload，不保留兼容字段或迁移分支；
  - 新增 `ApplicationRuntimeBoundaryTest`，固定 Application 适配器不得直接持有 `ChatClient`、`ChatModel`
    或 `ToolCallingManager`，旧 Plan Coordinator、旧上下文注册表不可恢复。Application 只负责发布版本与
    权限校验、可信上下文准备、Runtime/Workflow 调用适配、事件转发和运行事实/结果落库。
- 数据一致性：增加 `20260821_execution_run_skill_event_idempotency_v1.sql` 及回滚脚本，为
  `ai_execution_run_skill` 增加 `event_sequence_no` 和 `(run_id, event_sequence_no)` 唯一索引；Skill
  激活事实与单调事件一一绑定，重放已有事件不重复插入。历史事实使用负主键回填，与新事件正序号隔离；
  `schema.sql` 同步更新。除此之外无配置或开发数据写入。
- 删除与静态边界：Batch 5/6 已删除的 Application Plan/FinalSynthesis/Coordinator、Workflow 重复协议、
  LangGraph4j Application 实现和 `ChatExecutionContextRegistry` 在 clean 构建后均不存在；生产代码搜索确认
  无 `agent-execute`、Spring AI Alibaba、旧 Skill loader/审计合同，Application Runtime 无直接 Spring AI
  执行引擎字段，Workflow Core 无 Application/Chat/MyBatis/Spring/Hutool 反向依赖。
- 收束回归命令与结果：
  - Batch 7 Runtime/Workflow/Admin/Application 12 个聚焦测试类共 79 项通过；
  - `./mvnw -pl application -am test` 共 414 项通过，0 失败、0 错误、1 项既有环境跳过；
  - `./scripts/verify.sh` 首次在受限沙箱内因 Admin 三个本地 HTTP 测试禁止绑定端口而停止，未跳过测试；
    随后在允许回环端口的环境执行同一 clean verify，八模块全部 `SUCCESS`，共 618 项测试、0 失败、
    0 错误、1 项既有环境跳过；
  - Admin `pnpm lint`、`pnpm build` 通过；Web `pnpm test` 16 项、`pnpm lint`、`pnpm build` 通过；三个
    仓库 `git diff --check` 通过。
- 手工/真实链路验证：本批结束时当前 shell 无 Docker CLI，PostgreSQL 5432、RocketMQ 9876、MinIO
  9000 均未监听，无法应用新迁移、启动完整 Application 或执行发布版本
  `Application -> Supervisor/Workflow -> Agent -> Skill -> Tool -> audit` 真实链路。未通过关闭消费者、
  伪造 Bean 或静默降级制造成功；Batch 5 已在同一分支完成过真实 Application 装配与启动。
- 开发过程纠错：新增 import、直接替换合同后的陈旧测试、Mockito 嵌套 stubbing、带引号模块 glob、
  沙箱端口绑定五项问题已记录在 `.learnings/ERRORS.md` 并修复或使用正确执行环境重跑。
- 剩余开发计划：本次 Batch 0–7 代码收束全部完成，无后续开发批次。仅剩外部环境恢复后的验收：执行
  新增数据库迁移，真实启动 Application/Admin，发布包含三种激活策略的 Agent/Application，分别验证
  直接 Agent、Supervisor、Workflow、Tool、HITL Resume、事件与 `ai_execution_run_skill` 审计；同时在
  已登录 Admin 页面人工确认 Skill 策略标签。验收通过后再决定是否提交三个仓库。

## 2026-08-21 16:52 Batch 8 ACCEPTANCE START

- 批次定位：Batch 0–7 的生产代码收束已经完成，本批只做外部环境恢复后的最终验收和交付收口，不新增
  Runtime 抽象、不改运行语义，也不引入 Spring AI Agent Utils 或 Spring AI Alibaba。
- 分支与工作区：Server 为 `agent/agent-runtime-convergence...origin/agent/agent-runtime-convergence`，Admin 为
  `agent/admin-skill-auth...origin/agent/admin-skill-auth`，Web 为 `main...origin/main`；三个仓库继续保留
  Batch 5–7 未提交变更，未暂存、提交、推送或覆盖既有文件。
- 开发前回归：`./mvnw -pl application -am test` 通过，Framework 51 项（1 项既有环境跳过）、Infra AI
  26 项、Workflow Core 15 项、Agent Runtime 47 项、Application 275 项，共 414 项；Admin `pnpm lint`
  与 `pnpm build` 通过；Web `pnpm test` 16 项、`pnpm lint`、`pnpm build` 通过。
- 本批验收范围：应用 `20260821_execution_run_skill_event_idempotency_v1.sql`，真实启动 Admin 与
  Application，发布包含 `ALWAYS_ON`、`EXPLICIT_ONLY`、`MODEL_ROUTED` 的冻结 Agent/Application 版本，
  依次验证直接 Agent、Supervisor、Workflow、Skill -> Tool、HITL pause/resume、单调 ExecutionEvent 与
  `ai_execution_run_skill` 一一审计，以及 Admin 策略标签。

## 2026-08-21 16:57 Batch 8 ACCEPTANCE BLOCKED

- 阻塞证据：当前 shell 无 Docker、OrbStack、Podman，也无 PostgreSQL、MinIO、RocketMQ 本地服务二进制；
  `127.0.0.1` 与既定开发主机 `192.168.86.2` 的 5432、9876、9000 端口均未监听。仓库只有
  `.env.example`，没有可直接使用的本地 `.env`。因此无法安全应用迁移、启动完整服务或创建真实发布数据。
- 已完成的替代验证：在允许回环端口绑定的环境重新执行 `./scripts/verify.sh`，八模块全部 `SUCCESS`，
  共 618 项测试、0 失败、0 错误、1 项既有环境跳过；残留扫描只在架构测试的禁止名单中命中旧类名，
  Application Runtime 生产代码没有直接持有 `ChatClient`、`ChatModel` 或 `ToolCallingManager`；Server
  `git diff --check` 通过。
- 本批开发内容：未修改生产代码、API、Schema、配置或前端合同；仅补充本验收进度记录。没有通过关闭
  MQ 消费、绕过密钥、伪造数据库或静默降级制造“真实验收成功”。
- 恢复入口：依赖服务可用后，从本记录重新开始 Batch 8；先备份并确认目标数据库，再执行新增迁移并校验
  `event_sequence_no` 非空及 `(run_id, event_sequence_no)` 唯一索引，然后真实启动 Admin/Application，
  完成三种 Skill 激活、直接 Agent/Supervisor/Workflow/Tool/HITL 和事件审计验收，最后重跑三仓检查并
  将本记录追加为 `Batch 8 ACCEPTANCE DONE`。
- 剩余计划：仅剩上述真实环境验收和已登录 Admin 页面人工确认；未完成前不把整个收束计划标记为最终
  验收通过，也不新增 Batch 9 开发范围。

## 2026-08-21 17:15 Batch 8 ACCEPTANCE RESUMED

- 阻塞更正：用户确认 Docker 容器已启动后，在允许局域网访问的环境重新检查，`192.168.86.2` 的
  PostgreSQL 5432、Redis 6379、RocketMQ 9876、MinIO 9000 均连接成功，MinIO 健康端点返回 200。
  上一条 `BLOCKED` 使用沙箱内网络失败作为服务关闭证据，属于执行环境假阴性，已记录到本地纠错台账；
  后续容器/局域网状态必须在沙箱外复核。
- 数据库迁移：确认真实开发库为 `192.168.86.2:5432/lingxi_dev`，`ai_execution_run_skill` 迁移前存在、
  历史行数为 0，目标列与索引不存在，当前账号具备 UPDATE 与 Schema CREATE 权限；随后在单事务中执行
  `20260821_execution_run_skill_event_idempotency_v1.sql`。回读确认 `event_sequence_no` 为 NOT NULL、
  `uq_execution_run_skill_event` 为唯一索引，空序号和重复 `(run_id,event_sequence_no)` 均为 0。
- 真实启动：使用仓库开发配置与已确认数据库连接启动 Application 8080、Admin 8081；两者均完成
  PostgreSQL、Redis、RocketMQ、MinIO 和 Spring 上下文装配。Application 的 HITL、会话摘要、交互记录、
  用户画像四个 RocketMQ Consumer 均运行，Admin 知识切分 Consumer 运行。
- 发布数据检查：当前 Active `career_assistant` 为 Application Schema V11，但发布 Supervisor/专家 Agent
  仍为 Schema V7，另一个回归 Agent 为 V6；数据库没有 Skill，因此还不能执行三种激活策略真实验收。
  必须通过 Admin 正常业务流程创建并发布 Skill，再重新发布 Agent/Application，禁止直接修改发布快照。
- 当前暂停点：Admin 前端已在 3006 启动并打开登录页；应用内浏览器无既有会话，Chrome 未连接。为避免
  读取、猜测或重置管理员密码，等待用户在已显示的本地登录页手动登录。服务和浏览器页面保持运行。
- 剩余计划：登录完成后创建/发布 `ALWAYS_ON`、`EXPLICIT_ONLY`、`MODEL_ROUTED` Skill，绑定并重新发布
  Schema V8 Agent 与 Application；依次执行直接 Agent、Supervisor、Workflow、Skill -> Tool、HITL
  pause/resume、ExecutionEvent 与 `ai_execution_run_skill` 审计及 Admin 策略标签验收，最后全量回归并追加
  `Batch 8 ACCEPTANCE DONE`。

## 2026-08-21 18:06 Batch 8 ACCEPTANCE DONE

- 环境与迁移：更正默认沙箱对局域网依赖的假阴性后，确认 `192.168.86.2` 的 PostgreSQL、Redis、
  RocketMQ、MinIO 均可用；在 `lingxi_dev` 单事务应用
  `20260821_execution_run_skill_event_idempotency_v1.sql`，回读确认 `event_sequence_no` 非空、
  `(run_id,event_sequence_no)` 唯一且无重复数据。Application 启动时必须 unset `.env.example` 中的空
  `LINGXI_REDIS_PASSWORD`，避免覆盖 Spring 开发默认值。
- 正常控制面发布：通过已登录 Admin 创建并发布三个 Skill V1：`runtime_time_guard`（`ALWAYS_ON`，请求
  `system_current_time`）、`runtime_explicit_summary`（`EXPLICIT_ONLY`）、
  `runtime_model_routed_career`（`MODEL_ROUTED`）；个人求职教练与 Agent 委派器发布 V6，个人求职助手
  发布 V12。Admin 编辑器与版本详情人工确认三种策略标签和冻结绑定可见。
- Spring AI/模型配置核验：真实 Supervisor 首次使用供应商原生 JSON Schema 时被 DeepSeek 兼容端点以
  `response_format type is unavailable` 拒绝。控制面移除两个 DeepSeek Chat 实例不真实的原生
  `STRUCTURED_OUTPUT` 能力标记，Runtime 随后使用既有 Spring AI `BeanOutputConverter +
  StructuredOutputValidationAdvisor` 非原生路径完成结构化规划；没有增加 Application fallback、供应商
  特判或 Spring AI Alibaba 依赖。
- 真实 Agent Runtime 路径：
  - Supervisor 运行 `2090735752024612864` 完成规划、委派个人求职教练、激活 `ALWAYS_ON` 与
    `MODEL_ROUTED` Skill、执行 `system_current_time` 和知识搜索，最终 `RUN_COMPLETED`；
  - 直接专家运行 `2090736252795150336` 同时激活 `ALWAYS_ON`、显式 `EXPLICIT_ONLY` 与
    `MODEL_ROUTED`，执行用户信息/知识搜索并完成；Supervisor fallback 携带显式 Skill 时被服务端按边界
    正确拒绝；
  - 数据库审计确认三个 Skill 分别以 `ALWAYS_ON`、`EXPLICIT`、`MODEL_ROUTED` 原因写入
    `ai_execution_run_skill`，序号与单调 `SKILL_ACTIVATED` 事件一一对应；未显式请求的运行不存在
    `EXPLICIT_ONLY` 审计行。
- HITL 真实恢复：临时把无副作用的 `system_current_time` 配置为调用前批准，运行
  `2090739460896645120` 进入 `HUMAN_REQUIRED` 与 interaction 16；批准后同一 `runId` 产生
  `HUMAN_RESOLVED`，当前时间 Tool 只执行一次，随后知识搜索与最终模型调用完成，终态为
  `RUN_COMPLETED`。验收后已把 Tool 恢复为直接执行。
- Workflow 边界：临时发布 V13，把 `intent_3` 快速路由绑定到已发布的 `codex_valid_workflow`；真实请求
  进入 Workflow，Start/End 节点均产生 `STEP_STARTED/STEP_COMPLETED`，随后因既有 Workflow 快照没有
  产出非空 `response` 被 `BUSINESS_RULE_REJECTED` 正确拒绝。另一个 `codex_qa_workflow` 草稿仍含旧
  `__start__` 节点 ID，无法通过当前校验。因此本批验证了 Application -> Workflow 调度、节点事件与失败
  边界，但没有伪造成功响应。验收后草稿恢复为 0 个显式 Workflow 能力、1 条 Agent 快速路由，并通过
  版本历史重新激活 V12；V13 仅保留不可变验收历史。数据库确认当前正式版 V12 无 `intent_3` 映射。
- 收束回归：
  - 后端模块回归 `./mvnw -pl admin,application -am test` 在允许回环端口的环境通过；
  - `./scripts/verify.sh` 八个模块全部 `SUCCESS`，共 618 项测试、0 失败、0 错误、1 项既有环境跳过；
  - Admin `pnpm lint`、`pnpm build` 通过；Web `pnpm test` 16 项、`pnpm lint`、`pnpm build` 通过；
  - Server `git diff --check` 通过。本批未新增生产代码，数据库和控制面变更均通过正常迁移、发布、激活
    与 Tool 编辑流程完成。
- 当前结论：Agent Runtime 内核收束的直接 Agent、Supervisor、三种 Skill、Tool、Memory 入口、HITL、
  事件、审计和 Workflow 边界均完成真实或明确失败语义验收。Application 当前恢复为 V12，Tool 恢复为
  直接执行；没有遗留临时运行配置。
- 剩余计划：Runtime 收束无阻塞项。控制面数据维护可另行修复并重新发布一个符合当前协议、能够输出
  非空 `response` 的 Workflow，再补一条 Workflow 成功终态验收；下一开发批次同时清理未被运行端读取的
  `ChatRequest.agentCode` 旧请求字段，继续遵循“先回归上一批、单批开发、收束回归、更新记录”的循环。

## 2026-08-21 18:08 Batch 9 FINAL CLOSURE START

- 批次定位：只处理 Batch 8 明确留下的两个收尾项：删除运行端从未读取的 `ChatRequest.agentCode` 旧请求
  字段；通过正常控制面发布一个符合当前协议、能够输出非空 `response` 的 Workflow，并补齐成功终态真实
  验收。不新增 Runtime 抽象、兼容分支或新功能范围。
- 开发前检查与上批回归：三个仓库继续保留 Batch 5–8 未提交改动，未暂存、提交或推送；先执行
  `./scripts/verify.sh`，默认沙箱仅因 Admin 三个回环 HTTP 测试禁止 Socket 而失败，在允许回环端口的环境
  重跑后八模块全部 `SUCCESS`，共 618 项测试、0 失败、0 错误、1 项既有环境跳过。Admin `pnpm lint`、
  `pnpm build` 与 Web `pnpm test` 16 项、`pnpm lint`、`pnpm build` 均通过。
- 开发计划：先直接删除旧请求字段并用 Application 边界测试锁定；再通过 Admin 创建、校验、发布最小
  Start -> End Workflow，临时把其加入 Application 快速路由执行真实 Chat；审计成功状态、追加事件与
  非空消息后恢复正式版本和草稿；最后执行三个仓库全量回归并更新本记录。本批完成后无剩余开发计划。

## 2026-08-21 18:24 Batch 9 FINAL CLOSURE DONE

- 代码收尾：从 `ChatRequest` 直接删除未被运行端读取的 `agentCode` 旧字段，不保留 alias、兼容读取或
  fallback；`ApplicationRuntimeBoundaryTest` 增加负向反射断言，固定该字段及 getter 不得恢复。聚焦测试
  `ApplicationRuntimeBoundaryTest` 4 项通过，生产代码残留扫描为 0，唯一文本命中是该负向边界测试。
- 正常控制面发布：通过 Admin 创建、校验并发布 `runtime_success_workflow` V1。Workflow 使用当前协议的
  Start -> End 图，End 映射 `response <- query`，未直接写数据库、伪造运行事实或绕过发布校验。随后临时
  发布 `career_assistant` V14，将 `intent_3` 快速路由绑定到该 Workflow，形成真实 Application Chat 入口。
- Workflow 成功终态验收：请求“请计算12加30等于多少”产生顶层 execution
  `2090744830893428736`、Workflow run `62d8a466-bffb-46b0-8b89-dcbbf43086e2`、assistant message 388；
  顶层 `ai_execution_run` 为 `SUCCEEDED / TERMINAL / WORKFLOW`，Workflow run 为 `SUCCEEDED`，用户与
  Assistant 消息均为 `COMPLETED` 且 response 非空。追加事件严格为 `RUN_STARTED`、Start 节点开始/成功、
  End 节点开始/成功、`RUN_SUCCEEDED`，序号 1–6 单调无缺口；SSE 同步收到两个节点的
  `STEP_STARTED/STEP_COMPLETED` 与 done。
- 环境恢复：通过版本历史重新激活 Application V12，并把草稿中的临时 Workflow 能力和 `intent_3`
  快速直达恢复掉。数据库回读确认当前正式版 ID 21 / V12、正式快照没有 `intent_3` 映射，草稿显式
  Workflow 能力为 0、快速路由为 1；V14 仅保留为不可变验收历史，已发布的成功验收 Workflow 保留为
  可复用控制面资产。Tool 仍为直接执行，既有 Admin 8801 与 Docker 依赖未停止或修改。
- 最终收束回归：
  - `./scripts/verify.sh` 八模块全部 `SUCCESS`，共 619 项测试、0 失败、0 错误、1 项既有环境跳过；
  - Admin `pnpm lint`、`pnpm build` 通过；Web `pnpm test` 16 项、`pnpm lint`、`pnpm build` 通过；
  - Server、Admin、Web 三个仓库 `git diff --check` 全部通过；无生产代码 `agentCode`、`agent-execute`、
    旧 package、Spring AI Alibaba 或 Agent Utils 依赖残留；
  - 本轮启动的 Application 8080 与 Admin Vite 3006 已正常停止，未停止用户原有的 Admin 8801 服务。
- 最终结论：Batch 0–9 既定 Agent Runtime 内核收束目标全部完成。Agent 生命周期、统一状态与事件、迭代
  预算、Spring AI Advisor/ToolCallingManager/结构化输出、Hook、Skill、Tool、Memory、HITL、Supervisor、
  Plan & Execute 和 Workflow 边界均已完成代码收束与真实链路验收；Application 保持发布版本加载、可信
  上下文入口、Runtime/Workflow 调用、事件转发和结果落库职责。没有旧运行时兼容逻辑、新空抽象、
  Spring AI Alibaba 或 Agent Utils 生产依赖。
- 剩余开发计划：无。本次三个仓库仍保留未提交改动，未暂存、提交或推送；后续仅需按用户指令进行代码
  审阅、分仓提交与推送，不再增加内核收束 Batch。
