# Workflow Engine V1

## 目标

本 POC 验证配置驱动的多 Agent Workflow 是否可以在不改变现有 Chat/RAG
实现的前提下，使用 LangGraph4j Core 作为可替换的图执行内核。

验证路径：

```text
START
  -> 意图分类 Agent
  -> CONDITION
       -> MATCHED     -> 知识库 Agent
       -> NOT_MATCHED -> 通用 Agent
  -> END
```

## 边界

- `WorkflowDefinition`、节点配置、版本绑定和运行结果属于灵析自身协议。
- `LangGraphWorkflowExecutor` 只负责把灵析协议编译成 `StateGraph`。
- Agent 节点只依赖 `AgentNodeInvoker` 端口。
- 正式接入时，`AgentNodeInvoker` 适配现有 `AgentVersion`、`ModelChatContext`
  和 `ChatProcessChain`。
- Workflow 发布时必须把 Agent 引用解析并锁定为 `agentVersionId`。
- POC 阶段只支持无环图，以及 `START`、`AGENT`、`CONDITION`、`END` 四种节点。
- LangGraph4j 会在节点间克隆状态，因此工作流变量必须是可序列化数据；
  后续落库协议应进一步收敛为 JSON 兼容类型。

## 当前已实现

- `workflow-core` 保存与执行框架无关的 Workflow/Application 配置协议；
- JSONB 多态节点配置协议，使用显式 `configType` 判别字段；
- Application、ApplicationDraft、ApplicationVersion 三层持久化；
- 草稿 revision 乐观锁；
- 发布前结构校验与 Agent 引用校验；
- 草稿可跟随 Agent 当前生效版本，发布快照锁定具体 `agentVersionId`；
- 不可变版本、SHA-256 快照校验和、生效版本切换与审计；
- LangGraph4j Core DAG 执行 POC。

## 当前暂不实现

- PostgreSQL Checkpoint
- SSE 节点事件
- 并行节点、循环节点、人工审批和子工作流
- 编译结果缓存
- Agent 运行时正式适配器
