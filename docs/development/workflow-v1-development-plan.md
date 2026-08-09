# Workflow V1 开发计划

## 1. 实现目标

本轮将现有 VueFlow 画布和 LangGraph4j POC 补齐为可管理、可发布、可执行、可观测的 Workflow V1。完成后，运营人员能够创建包含条件分支和多个 Agent 的 Workflow，独立校验并发布不可变版本；Application 运行时能够执行指定 WorkflowVersion，并反馈节点级执行状态。

Workflow 是独立能力，不是 Application 的固定主流程。Application 仅在执行决策命中 Workflow 能力时调用其已发布版本。

## 2. 本轮范围

支持以下节点：

- `START`：唯一开始边界，画布自动创建，不允许删除。
- `END`：唯一结束边界，画布自动创建，不允许删除。
- `INTENT_CLASSIFIER`：绑定已发布意图树，将自然语言查询转换为结构化意图结果。
- `CONDITION`：读取工作流变量，产生 `MATCHED` 和 `NOT_MATCHED` 两条分支。
- `AGENT`：引用 Agent，配置输入输出映射，发布时锁定具体 AgentVersion。

Agent 可从资源列表直接拖入画布。该操作只是创建并预绑定一个 `AGENT` 节点，Agent 资源本身不等同于节点定义。

本轮不实现 Tool、子 Workflow、并行、循环、人工审批、Checkpoint 和运行中恢复。这些能力必须在执行语义、权限和持久化协议明确后单独演进。

## 3. 当前问题

- 管理端只能拖入 Agent，无法创建和配置条件节点。
- 重新打开草稿后节点位置和视口丢失。
- 加载后未保留 AgentVersion、输入输出映射、条件配置和分支路由。
- 保存操作可能用空配置覆盖后端已有配置，导致发布校验失败。
- 校验与发布未形成独立操作，错误无法定位到画布节点。
- Workflow 名称直接进入编辑态，历史版本无法使用只读画布回看。
- 运行时仅在完成后返回 Trace，没有节点级 SSE、失败 Trace 和持久化运行记录。

## 4. 协议与数据设计

执行定义与编辑器状态分离：

```text
WorkflowDefinition
  schemaVersion / code / nodes / edges
  只表达校验和运行语义

WorkflowCanvasState
  nodePositions / viewport
  只表达管理端画布状态
```

草稿保存 `definition + canvasState`，发布版本保存不可变的 `definitionSnapshot + canvasSnapshot`。画布坐标不进入 LangGraph4j 编译和运行上下文。

映射方向固定为：

- Agent 输入映射：`Agent 输入名 -> Workflow 变量名`。
- Agent 输出映射：`Agent 输出名 -> Workflow 变量名`。
- END 输出映射：`API 输出名 -> Workflow 变量名`。

Workflow 草稿允许保存尚未连线完成的中间状态；独立校验和发布执行完整结构校验。保存使用必填 `expectedRevision` 实现数据库条件更新，禁止先查询再无条件覆盖。

新增运行数据使用 `ai_workflow_run`、`ai_workflow_node_attempt` 和 `ai_workflow_run_event`。版本、运行、节点尝试和事件只追加保存。所有引用字段使用带索引的逻辑外键，不创建物理外键。

## 5. 管理端后端

在现有 `/api/admin/workflows` 下补齐以下能力：

```text
POST /{id}/validate
GET  /{id}/versions/{versionId}
POST /{id}/versions/{versionId}/activate
POST /{id}/versions/{versionId}/restore-draft
```

发布流程必须在事务内锁定 Workflow 和草稿，校验 revision，解析 Agent 引用，锁定 AgentVersion，校验图结构，计算规范化 checksum，写入版本快照并更新生效版本指针。切换版本只允许选择属于当前 Workflow 的版本；恢复历史版本创建新的草稿 revision，不覆盖历史版本。

## 6. 管理端交互

Workflow 名称进入只读详情，列表操作区提供明确的“编辑”入口。详情顶部展示状态、生效版本、草稿 revision 和更新时间，历史版本通过版本选择器切换并以只读画布展示。

编辑器采用三栏结构：

- 左侧：意图识别节点、条件节点和已发布 Agent 资源。
- 中间：VueFlow 画布、连线、缩放、适配画布、撤销和重做。
- 右侧：当前节点的紧凑配置面板。

意图识别节点绑定意图树并输出 `status`、`intentCode`、`confidence` 和 `candidateCodes`。条件节点提供两个固定输出锚点，并在边上显示“满足”和“不满足”。Agent 节点配置 Agent、可选历史版本、输入映射和输出映射；END 节点配置最终输出。字段释义使用可聚焦信息图标 Tooltip，校验错误和运行状态保持直接可见。

校验结果按节点聚合。点击问题后自动定位、选中节点并打开对应配置项。离开存在未保存修改的编辑器时必须确认。

## 7. 运行时与事件

Application 能力执行只读取锁定的 WorkflowVersion。运行时按以下顺序执行：

```text
创建 WorkflowRun
-> 编译 WorkflowDefinition
-> 执行节点并追加 NodeAttempt
-> 发布节点事件
-> 提取 END 输出
-> 完成 WorkflowRun
```

持久化事件包含 `RUN_STARTED`、`NODE_STARTED`、`NODE_SUCCEEDED`、`NODE_FAILED`、`RUN_SUCCEEDED` 和 `RUN_FAILED`。Chat SSE 复用执行时间线中的 `STEP_STARTED`、`STEP_COMPLETED` 和 `STEP_FAILED`。事件只包含 runId、workflowVersionId、nodeId、节点名称、状态、耗时和安全错误码，不记录完整 Prompt、敏感输入或原始模型输出。

Chat 侧复用现有执行时间线 SSE 通道展示 Workflow 进度，不为相同展示语义重复设计第二套事件通道。执行失败必须保存失败 Attempt 和终态事件后再向上抛出统一业务异常。

## 8. 开发顺序

1. 固化 WorkflowDefinition、CanvasState、版本和运行事件协议。
2. 增加数据库迁移、严格乐观锁、校验、版本详情、切换和恢复接口。
3. 完成条件节点、分支边、映射编辑器和画布状态持久化。
4. 完成只读详情、历史版本查看和显式编辑入口。
5. 增加 WorkflowRun、NodeAttempt、RunEvent 和节点执行监听。
6. 接入 Chat SSE，完成 Application 到 WorkflowVersion 的真实执行回归。

## 9. 验收标准

- 新建 Workflow 自动生成 START 和 END，二者不可删除。
- 可拖入多个 Agent 和条件节点，并配置两条条件分支。
- 保存并重新进入后，节点配置、连线、路由、坐标和视口完全一致。
- 无效结构可保存草稿，但校验和发布必须失败并准确指出节点。
- 发布快照中的每个 Agent 节点均锁定有效 AgentVersion。
- 任意历史版本可点击并以只读画布查看，也可切换生效或恢复为新草稿。
- Application Chat 能执行命中分支并得到 END 声明的 `response`。
- 前端能够看到节点开始、成功和失败状态；后台能够查询对应运行记录。
- 后端目标测试及 `./scripts/verify.sh` 通过，管理端 `pnpm lint`、`pnpm build` 通过，并完成真实创建、校验、发布、历史查看和 Chat 执行回归。
