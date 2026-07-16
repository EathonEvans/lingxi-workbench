# 管理端模型配置管理 V1 开发设计

> 状态：评审稿，确认后再进入代码开发  
> 适用范围：`lingxi-workbench-server`、`lingxi-workbench-admin`、`lingxi-workbench-web`  
> 当前约束：单租户、公司内部使用、PostgreSQL、Spring AI 2.0、管理端完全控制配置

## 1. 背景

当前应用端通过 `application.yml` 固定配置 DeepSeek 的 Base URL、API Key、模型编码和默认参数，
`infra-ai` 中的 `SpringAiDeepSeekStreamingChatGateway` 直接注入唯一的 `OpenAiChatModel`。

这种方式可以验证第一条流式对话链路，但不能满足后续需求：

- 管理员在线维护模型配置；
- API Key 不进入代码仓库；
- 多个 OpenAI Compatible 供应商共存；
- 用户端选择已启用模型；
- 模型停用、密钥轮换后及时生效；
- 后续知识库、Agent、Workflow 引用稳定的模型实例；
- 模型调用能够追溯到实际使用的配置。

产品文档中的完整模型中心还包含模型定义、路由、主备、权重、熔断、成本和可观测性。
这些能力不会全部进入第一版，避免在业务链路尚未稳定时过度设计。

## 2. 第一版目标

第一版只解决“模型配置可以安全管理，并能够被应用端真实调用”。

### 2.1 必须完成

1. 管理模型供应商及 OpenAI Compatible 地址；
2. 管理供应商 API Key，密文保存且永不回显明文；
3. 管理可调用的 Chat 模型实例及默认参数；
4. 启用、停用模型实例；
5. 每种模型类型只能设置一个默认实例；
6. 使用真实请求测试模型连通性；
7. 应用端从数据库解析默认模型，不再只依赖启动配置；
8. 用户端只能看到已启用且测试可用的模型；
9. 关键配置变更可审计，审计信息不包含密钥；
10. 模型调用异常不得向前端暴露供应商原始敏感信息。

### 2.2 第一版不实现

- 多租户、团队级模型权限；
- 多环境字段；开发、测试、生产使用各自独立数据库；
- 权重路由、主备切换、熔断、限流和自动降级；
- API Key 池、额度调度和自动轮换；
- 模型成本核算、成功率和 P95 聚合；
- Embedding、Rerank、Vision 的真实调用；
- 自动同步供应商模型列表；
- 完整 Model Playground；
- 独立模型微服务。

第一版在数据结构中保留模型类型和能力标签，但只开放 `CHAT` 类型，其他类型在对应业务开始时再实现。

## 3. 核心业务规则

1. 供应商、密钥和模型实例是三个独立概念，禁止将 API Key 直接存入模型实例表；
2. 一个供应商可以配置多个密钥和多个模型实例；
3. 一个模型实例只能绑定同一供应商下的密钥；
4. 停用供应商或密钥后，其下模型实例立即不可调用；
5. 停用模型实例时，如果它是默认模型，必须先指定新的默认模型；
6. 默认模型必须处于启用状态，且最近一次连通性测试成功；
7. API Key 创建后只返回脱敏提示，更新时空值表示保持不变；
8. 密钥轮换必须覆盖旧密文，同时记录操作人和轮换时间；
9. 已被业务引用的模型实例不物理删除，只允许停用；
10. 模型编码使用供应商真实模型名，例如 `deepseek-chat`，显示名称可独立修改；
11. `Temperature` 和 `Top P` 不建议同时设置，至少一个允许为空；
12. 每次调用必须记录实际模型实例 ID，不能只记录模型名称；
13. 管理端前端权限只控制展示，后端权限注解才是安全边界。

## 4. 架构决策建议

### 4.1 第一版不新增 Maven 模块

当前模型配置只存在一个写入方：管理端。应用端后续只需要读取一次可调用的运行配置，
尚未形成需要独立复用的完整共享领域，因此第一版不新增 `model-center` 模块。

职责划分：

| 模块 | 职责 |
| --- | --- |
| `admin/model` | 供应商、密钥、模型实例的完整 CRUD、业务校验和审计 |
| `application/model` | 只读查询默认或指定模型，生成最小运行配置投影 |
| `infra-ai` | 接收运行配置，负责 Spring AI 客户端构建、协议适配和流式调用 |
| `framework` | 通用 AES-GCM 实现、异常和响应体，不包含模型业务 |

应用端不复制管理端的完整 Service 和模型对象，只通过一条只读关联查询获得运行所需字段。
如果以后模型配置需要独立部署，或路由、限流、计费形成复杂共享业务，再将现有代码迁移到独立模块。

### 4.2 为什么不直接使用 Spring AI 自动配置

Spring AI 自动配置适合启动时只有一个固定模型。模型配置进入数据库后，需要根据模型实例动态设置
Base URL、API Key、模型编码和调用参数。

Spring AI 2.0 支持通过 Builder 创建 `OpenAiChatModel`，并支持在请求级别覆盖模型参数，因此第一版继续使用
Spring AI，不重新实现供应商 SDK。`infra-ai` 只负责根据运行配置创建或复用 Spring AI 客户端。

参考：

- [Spring AI 2.0 OpenAI Chat](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/api/chat/openai-chat.html)
- [OpenAiChatModel Builder API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/OpenAiChatModel.Builder.html)

## 5. 后端目录设计

```text
admin/src/main/java/com/lingxi/workbench/admin/model
├── controller
├── service
│   └── impl
├── repo
│   └── mapper
├── model
│   ├── request
│   ├── vo
│   ├── entity
│   ├── bo
│   └── converter
└── common
    ├── enums
    └── constants

application/src/main/java/com/lingxi/workbench/model
├── service
│   └── impl
├── repo
│   └── mapper
└── model
    └── bo

infra-ai/src/main/java/com/lingxi/workbench/infra/ai
├── client
│   └── OpenAiCompatibleClientFactory
└── chat
    └── SpringAiStreamingChatGateway
```

只在存在代码时创建目录，不提前建立空包。

## 6. 数据库设计

表统一放在 `lingxi_core`，审计表放在 `lingxi_audit`。所有主键使用 PostgreSQL Identity 自增。

### 6.1 `ai_model_provider`

模型供应商和协议地址，不保存密钥。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, Identity | 主键 |
| `name` | VARCHAR(64) | NOT NULL | 显示名称 |
| `code` | VARCHAR(64) | UNIQUE, NOT NULL | 稳定编码，小写 |
| `protocol` | VARCHAR(32) | NOT NULL | 第一版仅 `OPENAI_COMPATIBLE` |
| `base_url` | VARCHAR(512) | NOT NULL | API 基础地址 |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`、`DISABLED` |
| `remark` | VARCHAR(255) | NULL | 备注 |
| `created_by` | BIGINT | NOT NULL | 创建人 |
| `updated_by` | BIGINT | NOT NULL | 最后修改人 |
| `created_at` | TIMESTAMPTZ | NOT NULL | 创建时间 |
| `updated_at` | TIMESTAMPTZ | NOT NULL | 修改时间 |

校验规则：

- `code = LOWER(BTRIM(code))`；
- Base URL 只允许 `http`、`https`；
- 禁止 URL 携带用户名、密码、查询参数和 Fragment；
- 生产环境默认只允许 HTTPS，内网模型地址通过服务端允许列表放行；
- Base URL 末尾的 `/` 由服务端统一移除。

### 6.2 `ai_model_credential`

保存供应商密钥。密钥使用 AES-256-GCM 加密，数据库中不得出现明文。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, Identity | 主键 |
| `provider_id` | BIGINT | FK, NOT NULL | 所属供应商 |
| `name` | VARCHAR(64) | NOT NULL | 密钥配置名称 |
| `secret_ciphertext` | TEXT | NULL | GCM 密文，撤销后清空 |
| `secret_iv` | VARCHAR(64) | NULL | 随机 IV，Base64 编码 |
| `secret_hint` | VARCHAR(32) | NOT NULL | 脱敏提示，例如末四位 |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`、`DISABLED`、`REVOKED` |
| `rotated_at` | TIMESTAMPTZ | NULL | 最后轮换时间 |
| `created_by` | BIGINT | NOT NULL | 创建人 |
| `updated_by` | BIGINT | NOT NULL | 最后修改人 |
| `created_at` | TIMESTAMPTZ | NOT NULL | 创建时间 |
| `updated_at` | TIMESTAMPTZ | NOT NULL | 修改时间 |

约束：同一供应商下密钥名称唯一；`REVOKED` 状态必须清空密文和 IV。

主密钥通过环境变量 `LINGXI_MODEL_CREDENTIAL_MASTER_KEY` 注入，要求为 Base64 编码的 32 字节随机值。
主密钥不得放入数据库、配置文件、日志或接口响应。

### 6.3 `ai_model_instance`

表示一个可以被业务引用和真实调用的模型配置。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | BIGINT | PK, Identity | 稳定引用 ID |
| `provider_id` | BIGINT | FK, NOT NULL | 供应商 |
| `credential_id` | BIGINT | FK, NOT NULL | 使用的密钥 |
| `name` | VARCHAR(64) | NOT NULL | 显示名称 |
| `code` | VARCHAR(64) | UNIQUE, NOT NULL | 平台内稳定编码 |
| `model_code` | VARCHAR(128) | NOT NULL | 供应商真实模型编码 |
| `model_type` | VARCHAR(32) | NOT NULL | 第一版仅 `CHAT` |
| `capabilities` | JSONB | NOT NULL | 能力标签数组 |
| `temperature` | NUMERIC(4,3) | NULL | 0 到 2，按供应商能力校验 |
| `top_p` | NUMERIC(4,3) | NULL | 0 到 1 |
| `max_tokens` | INTEGER | NULL | 最大输出 Token |
| `context_window` | INTEGER | NULL | 上下文窗口，仅用于校验和展示 |
| `stop_sequences` | JSONB | NOT NULL | 停止序列数组，默认空数组 |
| `timeout_seconds` | INTEGER | NOT NULL | 调用超时 |
| `max_retries` | INTEGER | NOT NULL | 第一版建议 0 到 2 |
| `is_default` | BOOLEAN | NOT NULL | 是否为该类型默认模型 |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`、`DISABLED` |
| `last_test_status` | VARCHAR(16) | NOT NULL | `UNTESTED`、`SUCCESS`、`FAILED` |
| `last_test_latency_ms` | INTEGER | NULL | 最近测试耗时 |
| `last_test_error_code` | VARCHAR(64) | NULL | 平台映射后的错误码 |
| `last_test_message` | VARCHAR(255) | NULL | 脱敏结果摘要 |
| `last_test_at` | TIMESTAMPTZ | NULL | 最近测试时间 |
| `created_by` | BIGINT | NOT NULL | 创建人 |
| `updated_by` | BIGINT | NOT NULL | 最后修改人 |
| `created_at` | TIMESTAMPTZ | NOT NULL | 创建时间 |
| `updated_at` | TIMESTAMPTZ | NOT NULL | 修改时间 |

能力标签第一版允许：`STREAMING`、`TOOL_CALLING`、`STRUCTURED_OUTPUT`、`VISION`。

关键索引和约束：

```sql
CREATE UNIQUE INDEX uk_ai_model_instance_default_type
    ON lingxi_core.ai_model_instance (model_type)
    WHERE is_default = TRUE;

CREATE INDEX idx_ai_model_instance_available
    ON lingxi_core.ai_model_instance (model_type, status, last_test_status);
```

Service 必须校验 `provider_id` 与 `credential.provider_id` 一致，数据库外键只能保证记录存在，不能替代该业务规则。

### 6.4 `ai_model_change_log`

仅追加的关键变更审计表，记录：目标类型、目标 ID、操作人、动作、变更摘要、时间。

动作建议：

- `PROVIDER_CREATE`、`PROVIDER_UPDATE`、`PROVIDER_STATUS_CHANGE`；
- `CREDENTIAL_CREATE`、`CREDENTIAL_ROTATE`、`CREDENTIAL_REVOKE`；
- `MODEL_CREATE`、`MODEL_UPDATE`、`MODEL_STATUS_CHANGE`；
- `MODEL_SET_DEFAULT`、`MODEL_CONNECTIVITY_TEST`。

审计摘要禁止包含 API Key、完整请求头和供应商原始响应。

## 7. 接口设计

管理端接口继续使用动作可读路径，响应统一使用 `ApiResponse`。

### 7.1 供应商

| 方法 | 地址 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/model-providers/list` | `model:provider:list` | 查询供应商 |
| POST | `/api/v1/model-providers/create` | `model:provider:create` | 新增供应商 |
| POST | `/api/v1/model-providers/update/{id}` | `model:provider:update` | 修改供应商 |
| POST | `/api/v1/model-providers/change-status/{id}` | `model:provider:status` | 启停供应商 |

### 7.2 API Key

| 方法 | 地址 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/model-credentials/list` | `model:credential:list` | 查询脱敏密钥列表 |
| POST | `/api/v1/model-credentials/create` | `model:credential:create` | 新增密钥 |
| POST | `/api/v1/model-credentials/rotate/{id}` | `model:credential:rotate` | 轮换密钥 |
| POST | `/api/v1/model-credentials/change-status/{id}` | `model:credential:status` | 启停或撤销密钥 |

任何查询接口都不返回 `secret_ciphertext`、`secret_iv` 或 API Key 明文。

### 7.3 模型实例

| 方法 | 地址 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/model-instances/list` | `model:instance:list` | 查询模型实例 |
| POST | `/api/v1/model-instances/create` | `model:instance:create` | 新增实例 |
| POST | `/api/v1/model-instances/update/{id}` | `model:instance:update` | 修改实例 |
| POST | `/api/v1/model-instances/change-status/{id}` | `model:instance:status` | 启停实例 |
| POST | `/api/v1/model-instances/set-default/{id}` | `model:instance:default` | 设置默认实例 |
| POST | `/api/v1/model-instances/test/{id}` | `model:instance:test` | 真实连通性测试 |

测试请求第一版只允许传入简短测试内容和是否流式，不允许前端覆盖 Base URL、API Key 或模型编码。

### 7.4 用户端可用模型

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/models/list-available` | 查询当前可用 Chat 模型，不返回供应商连接和密钥信息 |

用户端返回字段只包含：实例 ID、显示名称、能力标签、是否默认。

## 8. 管理端页面设计

“模型管理”作为与“系统管理”同级的一级侧边栏目录，供应商、API Key 和模型实例分别使用独立页面。

```text
模型管理
├── 供应商
├── API 密钥
└── 模型实例
```

页面继续参照 Art Design Pro 的系统管理表格风格：搜索区、工具栏、表格区、分页区。
新增和编辑全部使用右侧抽屉，不使用弹窗。

### 8.1 供应商管理页

表格字段：名称、编码、协议、Base URL、启停状态、更新时间、操作。

抽屉字段：名称、编码、协议、Base URL、状态、备注。

### 8.2 API Key 管理页

表格字段：名称、供应商、密钥提示、状态、轮换时间、更新时间、操作。

交互规则：

- 创建抽屉中的 API Key 使用密码输入框；
- 编辑页面不展示旧密钥；
- 轮换使用独立抽屉，要求输入新密钥并二次确认；
- 撤销属于高风险操作，必须二次确认；
- 页面、Pinia、URL 和浏览器持久化状态均不得保存 API Key。

### 8.3 模型实例管理页

表格字段：实例名称、供应商、模型编码、类型、能力、默认、状态、测试状态、更新时间、操作。

抽屉字段：名称、编码、供应商、密钥、真实模型编码、模型类型、能力、调用参数、状态。

操作：编辑、启停、设置默认、连通性测试。

测试结果抽屉展示：状态、返回摘要、首 Token 耗时、总耗时、平台错误码。第一版不展示完整原始响应。

## 9. 运行时调用链

```text
ChatStreamController
    → ChatStreamService
    → ModelRuntimeService.resolveDefault(CHAT)
        → 校验实例、供应商、密钥状态
        → 解密 API Key
        → 生成 ModelRuntimeConfig
    → StreamingChatGateway.stream(runtimeConfig, command)
        → OpenAiCompatibleClientFactory
        → Spring AI OpenAiChatModel
        → 供应商接口
```

`ModelRuntimeConfig` 是应用端只读关联查询生成的最小投影，只在服务端内存中短暂存在，
不进入 Controller VO、日志、Redis 或数据库快照。

### 9.1 客户端缓存

为避免每次对话重新创建 HTTP 客户端，`infra-ai` 可以按以下键缓存客户端：

```text
modelInstanceId + instanceUpdatedAt + credentialUpdatedAt
```

配置或密钥发生变化后缓存键自然失效。缓存数量设置上限，禁止将 API Key 作为缓存键或打印到日志。

### 9.2 过渡策略

第一阶段保留环境变量配置作为兜底：

1. 数据库存在可用默认 Chat 实例时，优先使用数据库配置；
2. 不存在时使用当前环境变量配置，并输出一次不包含密钥的警告日志；
3. 管理端配置链路稳定后，删除环境变量模型兜底，只保留凭证主密钥。

## 10. 连通性测试

测试流程：

1. 校验供应商、密钥和模型实例状态；
2. 解密密钥；
3. 使用固定、短小且不包含业务数据的测试 Prompt；
4. 设置独立的短超时和较小的最大输出 Token；
5. 记录成功状态、首 Token、总耗时和平台错误码；
6. 清理内存中的临时引用；
7. 写入脱敏审计记录。

建议错误码：

- `MODEL_AUTHENTICATION_FAILED`；
- `MODEL_ENDPOINT_UNREACHABLE`；
- `MODEL_NOT_FOUND`；
- `MODEL_RATE_LIMITED`；
- `MODEL_TIMEOUT`；
- `MODEL_RESPONSE_INVALID`；
- `MODEL_PROVIDER_ERROR`。

供应商原始错误只进入受控服务端日志，并在记录前进行密钥、Header 和 URL 参数脱敏。

## 11. 安全要求

1. API Key 必须使用可轮换的 AES-256-GCM 主密钥加密，不能使用 Base64 代替加密；
2. API Key 不能使用 BCrypt，因为运行时需要解密后调用供应商；
3. 密钥主密钥只通过部署环境或密钥管理服务提供；
4. Request/VO 的 `toString()`、日志过滤器和异常信息不得包含 API Key；
5. 管理端创建和轮换密钥接口必须配置日志敏感字段掩码；
6. 禁止把 API Key 放入 Redis、前端 Store、LocalStorage、URL 或审计摘要；
7. Base URL 必须进行 SSRF 风险校验和服务端网络出口限制；
8. 供应商原始响应不得直接返回前端；
9. 密钥停用或撤销后，新请求立即失败，已有客户端缓存必须失效；
10. 只有拥有对应后端权限的管理端用户可以读取密钥提示或执行测试。

## 12. 事务与并发规则

- 创建、更新模型实例与审计日志在同一事务内完成；
- 设置默认模型时锁定当前类型的模型记录，先取消旧默认，再设置新默认；
- 密钥轮换与审计日志在同一事务内完成；
- 连通性测试的远程调用不占用数据库事务；先读取配置，远程调用结束后再短事务更新结果；
- 禁止在持有数据库行锁时发起模型网络请求；
- 停用供应商或密钥时，在事务内批量取消相关实例默认状态。

## 13. 测试范围

### 13.1 后端

- 供应商编码、Base URL 和唯一性校验；
- 密钥加密、解密、轮换、撤销和错误主密钥；
- 接口响应永不包含密文、IV 和明文；
- 模型实例供应商与密钥归属校验；
- 默认模型唯一性和并发设置；
- 停用供应商、密钥、实例后的调用阻断；
- 连通性测试成功和各类错误映射；
- 数据库默认模型与环境变量兜底；
- 日志与审计脱敏；
- Controller 权限注解和动作可读接口命名。

### 13.2 管理端

- 三个 Tab 的查询、空状态和错误状态；
- 新增、编辑、轮换全部使用抽屉；
- API Key 不反显、不持久化；
- 设置默认和高风险状态变更二次确认；
- 权限按钮隐藏与后端拒绝场景；
- 连通性测试加载、成功、失败和超时状态。

### 13.3 应用端

- 可用模型列表只返回安全字段；
- 默认模型选择；
- 指定模型对话；
- 模型停用后无法发起新对话；
- 密钥轮换后客户端缓存失效；
- SSE 成功、供应商错误和超时。

## 14. 验收标准

1. 管理员可以新增 OpenAI Compatible 供应商；
2. 管理员可以创建和轮换 API Key，任何查询接口都无法获得明文；
3. 管理员可以创建、修改、启停和测试 Chat 模型实例；
4. 系统能够保证只有一个默认 Chat 模型；
5. 用户端能够查询可用模型，并使用默认或指定模型发起流式对话；
6. 停用供应商、密钥或模型后，应用端不能继续创建新的模型调用；
7. 模型调用能够记录实际实例 ID、耗时、结果状态和平台错误码；
8. API Key 不出现在代码仓库、接口响应、日志、审计、Redis 和前端状态中；
9. 后端测试、管理端 lint/build、用户端 lint/build 全部通过；
10. 数据初始化脚本不包含真实 API Key。

## 15. 推荐开发顺序

1. 确认本文档中的关键决策；
2. 新增数据库 Schema；
3. 在 `admin/model` 实现密钥加解密及单元测试；
4. 实现供应商、密钥、模型实例后端管理接口；
5. 实现管理端三个独立页面；
6. 在 `application/model` 实现最小只读运行配置；
7. 实现 Spring AI 动态客户端和连通性测试；
8. 实现用户端可用模型选择；
9. 完成审计、权限、回归测试和数据迁移。

## 16. 已确认的关键决策

1. **首版范围**：第一版只真实支持 OpenAI Compatible Chat，Embedding 和 Rerank 后置；
2. **页面结构**：模型管理为与系统管理同级的一级目录，下设供应商管理、API Key 管理和模型实例管理三个独立页面；
3. **密钥方案**：第一版使用环境变量主密钥 + AES-256-GCM，后续再接 Vault 或云 KMS。
