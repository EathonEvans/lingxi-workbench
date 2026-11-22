# 知识检索预算、诊断与离线评测

评测直接调用 Application 在线使用的 `KnowledgeSearchService`：用户授权 → 固定索引校验 → Query Embedding → 向量召回与正文回填 → 可选重排。它不启动 Web 服务、任务调度或业务写入入口；模型调用会产生正常的供应商用量。

## 运行

1. 在 Admin 配置知识库授权、Embedding/Rerank 模型与可用索引。权限迁移 `20260907_knowledge_access_v1.sql` 必须已由部署流程执行；默认 RESTRICTED 且没有授权的知识库不可检索。
2. 复制 `example.json` 为本地数据集。示例的用户、知识库、索引、模型、片段 ID 全为占位值，必须替换。固定索引必须属于指定知识库和 Embedding 模型；使用实际用户 ID 验证实时 ACL。移除 `rerank` 可单独评测向量检索。
3. 配置与目标环境一致的 `LINGXI_DB_URL`、`LINGXI_DB_USERNAME`、`LINGXI_DB_PASSWORD` 和 `LINGXI_SECRET_MASTER_KEY`。密钥用于解密数据库内的模型凭据，不写入数据集。内网模型沿用 `lingxi.model.security.allowed-private-hosts` 配置。
4. 从 Server 仓库运行：

```bash
./scripts/verify.sh
./scripts/evaluate-knowledge.sh --dataset=/absolute/path/dataset.json --output=/tmp/knowledge-report-new.json
```

参数按上述顺序传入；输出文件必须不存在。数据集最大 1 MiB、1–100 个样本，样本 ID 唯一。完整数据集先通过约束校验再执行检索，任意断言失败时仍写出报告并返回退出码 1。非法数据集或文件错误也返回非零退出码。CLI 使用应用配置与环境变量，不接受额外 Spring 命令行参数。

评测由持有数据库连接和模型解密权限的运维人员运行。数据集可指定被测用户，但每次检索仍执行该用户的实时知识库 ACL，没有管理员绕过路径。不得将此 CLI 包装成允许外部用户任意指定身份的接口。

## 指标与断言

| 输出 | 计算或含义 |
| --- | --- |
| `vectorMetrics` | 同次召回中原始向量排名的前 `topK` 条 |
| `finalMetrics` | 重排、分数阈值过滤后的最终结果；关闭重排时为向量结果 |
| `recallAtK` | 命中的不同预期片段数 / 预期片段总数 |
| `mrr` | 单样本首个相关片段名次的倒数；总报告为正例样本均值 |
| `diagnostics` | 阶段耗时、完整精度的片段排名与双分数、输入裁剪数和估算 Token 数 |

`expectedChunkIds` 非空的样本至少命中一条即通过，不能将 `passed=true` 解读为 Recall=1。正例检索失败计零分并保留错误码。`expectedChunkIds=[]` 且 `expectDenied=false` 表示必须返回空结果；`expectDenied=true` 只接受 403，不能同时配置预期片段。没有正例时总指标为 null，JSON 文件省略该字段。总 `passed` 要求所有样本断言通过，不额外设置隐式质量门槛。

报告不包含 query、正文、模型密钥或供应商异常消息。失败样本保留错误码，不伪造成功耗时或空结果。当前评测衡量检索质量，不衡量最终回答正确率、引用正确率或 Agent 工具后续上下文裁剪；同次向量基线是候选召回的前 K 条，不能等同于另一次独立执行的 ANN 查询。

## 平台预算

以下配置由部署方维护，不增加 Agent 编辑字段，也不修改已冻结的业务检索策略。

| 配置 | 默认值 | 行为 |
| --- | --- | --- |
| `lingxi.knowledge.retrieval.max-document-tokens` | 2000 | 单候选发送给重排模型的正文估算上限 |
| `lingxi.knowledge.retrieval.max-rerank-input-tokens` | 16000 | 所有 query-document 对的估算总上限，每条候选重复计入 query |
| `lingxi.knowledge.retrieval.database-timeout-seconds` | 3 | 每段只读事务的 JDBC 语句时限上限 |
| `lingxi.model.rerank-http.max-request-bytes` | 262144 | UTF-8 请求体上限，发送前检查 |
| `lingxi.model.rerank-http.max-response-bytes` | 65536 | 流式接收上限，超限立即取消 |

候选按向量顺序消耗预算；过长正文截断，预算耗尽则丢弃后续候选。裁剪仅影响重排输入，最终结果保留原正文与来源。无预算容纳任何候选时明确失败。Token 使用平台本地分词器估算，不能视为供应商实际计费或其模型窗口的精确值；字节限制是独立边界。

数据库查询使用独立只读事务，将剩余秒级时限交给 MyBatis/JdbcTemplate，不修改共享 JdbcTemplate。Embedding 和重排远程调用位于事务之外。在线 RAG 的 Query Embedding 固定为单次尝试，避免 SDK 内部重试退避越过检索预算；其他 Embedding 场景的配置不变。重排对允许的短暂失败保留有限重试，各次共享完整响应截止时间，超时取消 HTTP 请求，收到响应头不算请求完成。失败不会降级为向量结果。

JDBC 和 Embedding 配置粒度为秒，阶段结束后再次检查总时限，因此这不是毫秒级硬实时保证。连接池获取连接的等待仍受部署端连接池配置约束，不由 JDBC Statement 超时替代。

## 在线 Trace

沿用 `StageObserver` 的 `KNOWLEDGE_SEARCH` 总阶段与 `KNOWLEDGE_AUTHORIZATION`、`KNOWLEDGE_EMBEDDING`、`KNOWLEDGE_RECALL`、`KNOWLEDGE_RERANK` 子阶段。成功检索关联执行 ID、知识库、索引与策略参数，记录裁剪计数；异常由阶段 Observation 标记失败，不输出原始 JDBC/供应商错误正文。

排名属性为 `lingxi.knowledge.candidates.N` / `lingxi.knowledge.selected.N`。每条编码为 `chunkId/向量分数/重排分数;`，无重排分数用 `-`，Trace 分数保留四位小数。按完整条目分页且每个属性不超过 240 字符，适配现有 OTLP 的 256 字符限制。离线报告保留完整分数精度。没有将 query 或文档正文添加到 Trace。

当前批次不包含混合召回、文档级 ACL、引用展示或评测管理页面。

## 本批验证（2026-09-07）

- `./scripts/verify.sh`：815 项，0 失败、0 错误、1 跳过。HTTP 测试覆盖慢正文超时取消、请求/响应字节上限、供应商协议与有限重试。
- JDBC 事务测试使用真实 `DataSourceTransactionManager`、`JdbcTemplate` 和生产 VectorRepo，模拟 JDBC 连接，验证 Statement 超时设置、SQL 超时失败以及远程调用时没有数据库事务。
- 数据集到报告的协作测试使用实际检索服务与本地 HTTP 重排服务；固定数据库和 Embedding 样本验证预算裁剪、排名改善、403 断言和报告脱敏。该结果不代表真实供应商的检索质量。
- 已验证打包后的 CLI 可独立启动：将数据库地址指向不可用的本机端口，非法数据集在查询前被拒绝，退出码为 1，未创建报告。
- 未执行真实 PostgreSQL 迁移、真实授权修改或供应商调用；现有 PostgreSQL JSONB 集成测试仍跳过。登录会话中的 RAG、SSE 和外部 Trace 收集链路需要环境验收。
