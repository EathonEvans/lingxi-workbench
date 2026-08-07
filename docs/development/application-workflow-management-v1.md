# Application Workflow 配置管理 V1

> 历史设计说明：自 2026-08-07 起，Workflow 已升级为独立管理、独立发布的资源。
> 新 Application 不再维护内嵌主 Workflow，而是通过意图叶子映射引用已发布
> WorkflowVersion、AgentVersion 或固定答复。本文件中的内嵌 Workflow 仅用于理解和
> 兼容迁移前快照，当前实现以 `application-intent-routing-v1.md` 和 Workflow 管理模块为准。

## 1. 数据边界

```text
ai_application
  稳定身份、启停状态、生效版本指针

ai_application_draft
  唯一可编辑草稿、revision 乐观锁、ApplicationDefinition JSONB

ai_application_version
  不可变发布快照、版本号、SHA-256、发布审计
```

表之间使用逻辑外键：`application_id`、`base_version_id` 和
`published_version_id` 由服务层维护引用完整性，数据库不创建
`FOREIGN KEY` 约束。引用列通过唯一索引或普通索引保证查询效率。

历史 V1 中 Workflow 是 Application 的内部能力；当前版本已改为独立产品资源。
Agent 独立管理，Application 的 Agent 节点只保存 Agent 引用和输入输出映射，
不会复制 Agent 内部模型、Prompt、知识库或 RAG 策略。

## 2. Agent 版本语义

草稿节点可以不指定 `agentVersionId`：

```json
{
  "id": "rag-agent",
  "name": "知识库问答",
  "type": "AGENT",
  "config": {
    "configType": "AGENT",
    "agentId": "1001",
    "agentVersionId": null,
    "inputMapping": { "query": "query" },
    "outputMapping": { "answer": "answer" }
  }
}
```

这表示“发布时采用该 Agent 当前生效版本”，不是“运行时动态跟随”。
发布服务会把它解析为具体且不可变的 AgentVersion ID，再写入
ApplicationVersion 快照。因此 Agent 后续发布或切换版本，不会改变已经发布的
Application。

显式填写 `agentVersionId` 时，可以选择该 Agent 的历史版本；服务端会验证该版本
确实属于所选 Agent，防止跨 Agent 引用。

所有跨前端边界的数据库 ID 都使用字符串，避免 JavaScript 大整数精度损失。

## 3. Workflow V1 协议

支持节点：

- `START`
- `AGENT`
- `CONDITION`
- `END`

V1 约束：

- 有且仅有一个 START 和一个 END；
- START 必须只有一条出边；
- AGENT 必须至少有一条入边且只有一条出边；
- CONDITION 必须同时具有 `MATCHED`、`NOT_MATCHED` 两条出边；
- 所有节点必须从 START 可达；
- 暂不允许环路；
- 发布版本中的每个 AGENT 节点必须锁定 `agentVersionId`。

节点 `config` 必须包含 `configType`，用于 JSONB 的安全多态反序列化。该字段必须
与外层节点 `type` 一致，服务端会再次校验，不能依靠客户端保证。

## 4. 管理接口

基础路径：`/api/v1/applications`

```text
GET  /list
GET  /detail/{id}
GET  /versions/{id}
GET  /version-detail/{id}/{versionId}
POST /create
POST /update/{id}
POST /save-draft/{id}
POST /validate/{id}
POST /publish/{id}
POST /activate-version/{id}/{versionId}
POST /change-status/{id}
```

权限：

```text
application:list
application:manage
application:publish
```

保存草稿示例：

```json
{
  "expectedRevision": 3,
  "changeNote": "增加意图路由",
  "workflow": {
    "schemaVersion": 1,
    "code": "main",
    "nodes": [],
    "edges": []
  }
}
```

保存时执行 Workflow 结构校验，并使用 `expectedRevision` 保护并发编辑。发布时重新
在事务中锁定 Application 和草稿，执行资源解析与完整校验，不能复用客户端之前的
校验结论。

## 5. 数据库脚本

- 初始化结构：`resources/database/postgresql/schema.sql`
- 增量迁移：`resources/database/postgresql/migration/20260727_application_workflow_v1.sql`
- 回滚脚本：`resources/database/postgresql/rollback/20260727_application_workflow_v1_rollback.sql`
- 菜单迁移：`resources/database/postgresql/migration/20260727_application_menu_enable.sql`

管理端 V1 提供 Application 列表、版本管理和有序单向 Workflow 编辑器。
条件分支协议已在后端保留，但前端 V1 不会覆盖包含条件分支或非线性连线的草稿；
后续图画布版本再开放分支编辑。

## 6. 运行时接入

Chat 启动接口通过 `applicationCode` 选择 AI 应用：

```json
{
  "conversationId": "1001",
  "clientMessageId": "web-uuid",
  "query": "如何申请退款？",
  "applicationCode": "customer-support"
}
```

运行链路：

```text
applicationCode
→ ACTIVE Application
→ publishedVersionId
→ ApplicationVersion.definitionSnapshot
→ LangGraph4j Workflow
→ 节点锁定的 AgentVersion.definitionSnapshot
→ ModelChatContext / ChatProcessChain / RAG
→ Workflow response
→ 原有 Assistant 消息与 SSE 终态
```

运行端只读发布快照，不读取草稿，也不会在请求时动态跟随 Agent 当前生效版本。
Assistant 消息保存 `application_id`、`application_version_id` 和入口模型实例，便于复现
历史回答。Application 两个引用列都是逻辑外键，不创建数据库 `FOREIGN KEY`。

V1 为了复用现有同步 LangGraph4j 执行边界，会在后台线程完成 Workflow 后一次性发布
最终 `response`；SSE 断线续传、显式取消和消息终态仍沿用原机制。后续可把执行协议升级
为节点事件流，使最终 Agent 的 Token 增量直接透传。
