# Agent Runtime 内核收束开发计划

> 状态：执行中，Batch 0–4 已完成
> 更新日期：2026-08-18
> 设计基线：[Agent Runtime 内核收束设计](agent-runtime-core-convergence-v1.md)
> 持续记录：[Agent Runtime 内核收束开发进度](agent-runtime-core-convergence-progress.md)

## 1. 执行原则

本次收束不按“先大量搬类，最后一次性联调”执行。每个批次必须交付一条可回归的完整纵向路径，
并严格循环：

```text
检查本地开发进度
-> 识别上批次实际交付与未完成项
-> 回归上批次
-> 回归全部通过
-> 实施本批次
-> 本批次收束回归
-> 记录完成内容、验证证据、已知问题和剩余计划
-> 下一批次
```

硬性规则：

1. 上批次回归未通过，不得开始本批次新能力；先定位并修复上批次。
2. 每次开发前都重新读取根目录及目标子项目 `AGENTS.md`，不依赖上次记忆。
3. 每次开发前都检查 Server、Admin、Web 三个 Git 仓库，不把 Lingxi 根目录当作 Git 仓库。
4. 未提交改动默认属于已有开发进度；不覆盖、不回滚、不擅自 stash，先分类与本批次的重叠。
5. 一个能力迁入 Runtime 时，同一批次删除对应旧路径，不留兼容分支、别名、代理类或双写。
6. 不以“编译通过”代替业务回归；必须验证失败路径、状态转换、幂等、事件和真实启动链。
7. 每个批次结束都必须追加进度记录；没有记录不算批次完成。

## 2. 每批次开发前的固定检查

### 2.1 本地进度盘点

每次开始时依次检查：

1. 当前分支、跟踪分支、未提交和未跟踪文件；
2. 上一条进度记录中的已完成、未完成、已知问题和下批入口；
3. 当前 diff 中是否有新增契约、Schema、迁移、配置、前端类型或运行时状态；
4. 本批次与已有未提交改动的文件重叠；
5. 当前可测试基线、本地依赖和必要服务状态。

基本检查命令：

```bash
git -C lingxi-workbench-server status --short --branch
git -C lingxi-workbench-admin status --short --branch
git -C lingxi-workbench-web status --short --branch

git -C lingxi-workbench-server diff --stat
git -C lingxi-workbench-admin diff --stat
git -C lingxi-workbench-web diff --stat
```

实际执行时从 Lingxi 根目录运行；若位于子仓库，需要按当前路径调整，不复制错误相对路径。

### 2.2 上批次回归门禁

先重跑上批记录的精确测试和验收场景，再执行其直接影响模块的组合回归。门禁结果只有：

- `PASS`：可进入本批开发；
- `FAIL`：本批暂停，修复上批回归后从门禁开始重跑；
- `BLOCKED`：记录缺失依赖、外部服务或需要的用户决策，不用未验证假设继续新开发。

每批回归范围至少包含：

- 上批新增或修改的单元/集成测试；
- 上批改动模块及其上游调用方测试；
- 上批交付的成功、失败、权限、超时、幂等和状态转换场景；
- 有契约变化时的 Server 消费方、Admin/Web 类型与交互回归；
- 涉及 Spring 装配时的 Application 真实启动。

## 3. 本批次开发规约

回归门禁通过后，先在进度记录中标记“本批开始”，固定：

- 批次目标与明确不做项；
- 预计修改模块、主要类和契约；
- 需要先删除的旧路径；
- 本批验收场景与命令；
- 是否影响 Schema、API、配置、SSE 或前端。

实施期间：

1. 先找可比较的现有实现，再修改。
2. 优先小而完整的纵向切片，不创建本批无调用方的空抽象。
3. 能力迁入 `agent-runtime` 后立即删除 Application 中的对应运行语义。
4. Spring AI 已有能力优先：`ChatClient`、Advisor、typed structured output、`ToolCallback`、
   `ToolCallingManager`、MCP Callback、Embedding 与 Observation。
5. 任何 Spring AI 内部重试、Tool 循环或流式重入都必须受 `ExecutionState` 额度和事件覆盖。
6. 不保留旧 Runtime 兼容逻辑，不以双路径长期并存作为迁移手段。

## 4. 每批次开发完成后的收束回归

收束顺序固定为：

1. 运行本批最小精确测试；
2. 运行受影响 Maven 模块的 `-am test`；
3. 回归与上批次共享的用户路径；
4. 执行 `git diff --check` 并重新检查三个仓库状态；
5. 按风险执行 `./scripts/verify.sh`、前端 lint/build/test 和真实启动；
6. 对照本批验收条件，确认旧路径、兼容分支和无调用类已删除；
7. 追加进度记录，再把本批标记为完成。

通用后端命令：

```bash
./mvnw -pl agent-runtime -am test
./mvnw -pl application -am test
./mvnw -pl admin -am test
./scripts/verify.sh
```

契约影响 Admin 时：

```bash
pnpm lint
pnpm build
```

契约影响 Web 时：

```bash
pnpm test
pnpm lint
pnpm build
```

命令必须在对应子仓库执行。未执行、外部依赖阻塞或仅手工验证的项目必须在进度记录中单独列出。
Batch 0 执行时模块尚未更名，因此使用 `./mvnw -pl agent-execute -am test`；Batch 1 完成更名后，
从收束回归开始统一使用 `agent-runtime`。

## 5. 开发批次

### Batch 0：当前本地进度收束与基线回归

目标：确认当前 Skill V8、Spring AI `ChatClient`、登录重定向和相关未提交改动是一个可回归基线。
本批不开始 Agent Runtime 重构。

必做：

- 按功能分类 Server/Admin/Web 未提交文件，确认无未知重叠和误生成文件；
- 回归 `SpringAiChatCompletionGateway` 非流式调用、Advisor 自动 Tool Calling 关闭与 Observation 父子链；
- 回归 Skill 草稿、发布、Agent 绑定、历史版本锁定、`ALWAYS_ON` 激活、有效 Tool 交集和运行审计；
- 回归 Admin 登录成功、登录失败、redirect、动态路由初始化和 Skill/Agent 管理页；
- 检查 Skill 迁移脚本的增量性、枚举约束和无物理外键规则；
- 执行 Server 相关模块测试、Admin lint/build 以及 Application/Admin 真实启动。

出口：当前本地改动的成功与失败路径均有验证证据，进度记录写明未提交改动的真实状态。

### Batch 1：模块更名、Spring AI 调用主干与无 Tool Specialist 纵向切片

目标：先将现有模块原地更名为 `agent-runtime`，再用一条可运行的无 Tool Agent 路径建立
`AgentRuntime`、`ExecutionState`、`ExecutionEvent`、
`ChatClient` 和 Application 适配边界。

必做：

- 目录 `agent-execute` 更名为 `agent-runtime`，Maven `artifactId` 和所有模块依赖同步更新；
- Java 根包 `com.lingxi.workbench.agentexecute` 更名为 `com.lingxi.workbench.agentruntime`，同步更新测试、import 和架构规则；
- 更新根 `pom.xml`、`application/pom.xml`、脚本、`AGENTS.md` 和当前文档中的模块名；
- 删除旧目录、旧 `artifactId` 和旧 Java 包名，不发布 relocation POM，不保留过渡模块；
- 建立 `AgentRuntime.run` 门面及强类型 Request/Result；`resume` 与 Batch 3 的
  Checkpoint/HITL 恢复闭环同时引入，不提前创建空恢复抽象；
- 建立唯一 `ExecutionState` 和单调 `ExecutionEvent.sequenceNo`；
- 非流式与流式均进 `ChatClient`，建立 `RuntimeModelAdvisor` 和 `RuntimeContextAdvisor`；
- 打通 Application 版本加载、上下文入口、Runtime 调用、事件持久化/SSE 投影与结果落库；
- 直接 Agent 路径迁入后删除 Application 中对应无 Tool 循环和双事件路径。

不做：Tool、Skill 激活改造、HITL、Supervisor、Workflow 迁移。

出口：仓库中不再存在生产使用的 `agent-execute`/`agentexecute`；无 Tool Specialist 的成功、模型失败、
预算超限、超时、取消、部分结果和流式观测只有一条路径。

本批收束时额外验证：

```bash
./mvnw -pl agent-runtime -am test
./mvnw -pl application -am test
./scripts/verify.sh
rg -n "agent-execute|agentexecute" pom.xml */pom.xml scripts AGENTS.md agent-runtime application
```

`rg` 允许只命中明确标记为历史的设计/进度记录，不得命中 Maven、脚本、AGENTS、生产代码或测试包。

### Batch 2：Tool、Skill `ALWAYS_ON` 与 ReAct 循环

目标：在 Batch 1 的 Runtime 中完成 Spring AI user-controlled Tool Execution 和当前已存在的 Skill V8 运行事实。

必做：

- 用 `ChatClient + ToolCallingManager` 实现受 `ExecutionState` 控制的 ReAct 循环；
- 保留并收为 `RuntimeToolCallback`，处理幂等、结果上限、超时和 Tool 事件；
- 整合 `AgentToolExecutionLedger`、单 Tool 两次限制、总 Tool 预算和确定性结果复用；
- 迁入 `SkillHook`，支持已发布且锁定的 `ALWAYS_ON` Skill 和有效 Tool 交集；
- 删除 `AgentChatService` 旧 Tool Loop、独立 Tool 进度协议和重复预算。

出口：无 Tool、单 Tool、多轮 Tool、超限、未授权、确定性复用、MCP 和知识检索 Tool 均经过同一 Runtime。

### Batch 3：HITL、Checkpoint 与恢复

目标：把人工审批从异常控制流收为 Runtime typed interruption 与可恢复 State。

必做：

- `HumanApprovalHook` 在 Tool 执行前产生 Checkpoint 和 `Interrupted`；
- 固定指纹、冻结版本、pending invocation 和原 Tool Call；
- approve/reject、重复决定、过期、取消、MQ 重投和进程重启恢复同一 State；
- 删除 `HumanInteractionRequiredException` 与 Gate/Permit 双层控制流；
- 外部副作用成功、本地确认失败时进入对账状态，不自动重放。

出口：审批暂停不依赖 JVM 内存对象，恢复不重新路由、重新规划或重复执行 Tool。

### Batch 4：Supervisor Planner 与 typed structured output

目标：把 Planner 从 Application 迁入 Runtime，先完成“决策生成与校验”，不同时搬完整计划执行。

必做：

- 使用 Spring AI typed `responseEntity`、`BeanOutputConverter`和按能力启用的 provider structured output；
- 使用有界 `StructuredOutputValidationAdvisor`，其每次自修复均消耗 Runtime 模型次数和 Token；
- 删除 `completeJson`/`JSONObject` Runtime 协议和 `ExecutionDecisionParser`；
- 保留并迁移能力编码、步骤依赖、参数来源、风险和预算的业务语义 Validator；
- 生成统一 `PLAN_CREATED` / `REPLANNING` 事件和决策 Checkpoint。

出口：合法决策、Schema 错误、未知字段、未授权能力、错误步骤引用和预算超限都在能力执行前完成收口。

### Batch 5：Plan-and-Execute 与能力边界

目标：迁入计划执行、子 Agent 委派、Workflow/Tool 能力调用、有界重规划和最终综合。

必做：

- 每个步骤创建隔离 `AgentInvocationState`，共享 Run 总预算；
- 通过 `RuntimeCapabilityInvoker` 调用已锁定 Agent、Workflow 或平台 Tool；
- 步骤失败分类、有界重规划、副作用去重与 Partial 收口；
- 最终综合不开放 Tool，只使用预留模型和 Token 额度；
- 删除 Application 内 Planner/Plan/FinalSynthesis 旧运行入口和重复模型。

出口：Supervisor 从规划到最终回答不依赖 Application 编排代码，计划、步骤、Tool、Skill 使用同一事件序列。

### Batch 6：Workflow 边界与 Application 收口

目标：使 Workflow 保持独立图执行域，并将 Application 收为版本加载、上下文入口、Runtime 调用、事件转发和结果落库。

必做：

- Workflow 执行协议与 LangGraph4j 实现迁入 `workflow-core`；
- Workflow Agent 节点显式调用 `AgentRuntime`；
- 删除 `ChatExecutionContextRegistry` 及所有进程内侧载上下文；
- 瘦身 `ApplicationChatExecutionServiceImpl` 和 `CapabilityExecutionServiceImpl`；
- 增加架构测试，禁止 Application 再引入模型循环、Tool Loop 或 Plan Loop。

出口：AGENT 叶子、WORKFLOW 叶子、Workflow Agent 节点和 Supervisor Workflow 步骤边界清晰，无隐式共享状态。

### Batch 7：Skill 激活模式、旧契约清理与全量验收

目标：完成 `EXPLICIT_ONLY`、`MODEL_ROUTED` 真实闭环，删除所有旧 Runtime 残留并进行全链路回归。

必做：

- 实现服务端验证的显式 Skill 激活；
- 实现先元数据披露、后完整指令加载的模型路由 Skill；
- Tool Search Advisor 只在授权/Skill 过滤后目录达到实测阈值时引入；
- 删除旧 DTO、枚举、异常、事件、别名、兼容读取和无调用类；
- 执行 Server 全量验证、Admin/Web 契约回归、真实启动与至少一条已发布版本端到端调用；
- 更新历史设计文档状态并指向最终 Runtime 契约。

出口：设计验收场景全部通过，Application 职责和依赖方向符合目标，无旧 Runtime 兼容逻辑。

## 6. 进度记录规则

每个批次在进度文档追加两条记录：

1. `START`：本地进度检查结果、上批回归证据、本批范围和不做项；
2. `DONE` 或 `BLOCKED`：实际改动、删除的旧路径、契约/数据/配置变更、回归证据、已知问题和剩余计划。

开发记录不写“大概完成”。每项只能是已完成、未完成或阻塞，并附文件/测试/手工验证证据。

## 7. 计划完成标准

只有同时满足以下条件，才将本计划标记为完成：

- Batch 0–7 均有 START 和 DONE 记录；
- 每批的开发前回归与收束回归均有证据；
- Application 只保留版本加载、上下文入口、Runtime 调用、事件转发和结果落库；
- `ExecutionState`、`ExecutionEvent`、迭代、Hook、Advisor、Skill、Tool、Memory、HITL、Supervisor 和 Workflow 边界唯一；
- Spring AI 现有能力得到合理复用，无并行 Model/Tool 底层框架；
- 三个仓库的相关契约、测试、构建、真实启动和端到端路径完成最终回归；
- 没有旧 Runtime 兼容分支、无调用空抽象或未记录的跳过项。
