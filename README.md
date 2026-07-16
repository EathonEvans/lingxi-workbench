# Lingxi Workbench Server

灵析 AiWorkbench 后端工程。项目采用 Java 21、Spring Boot 3.5、Spring AI 1.1、Sa-Token、MyBatis-Plus、PostgreSQL + pgvector。

## Modules

| 模块 | 作用域 | 是否独立启动 | 依赖 |
| --- | --- | --- | --- |
| `framework` | 认证、缓存、数据库、Web、统一审计、可观测性等通用技术能力 | 否 | 不依赖业务模块 |
| `infra-ai` | 模型、Embedding、Rerank 等 AI 供应商适配 | 否 | `framework` |
| `admin` | 管理员认证以及用户、角色、权限、菜单、系统配置和审计事件产生 | 是，默认 `8081` | `framework` |
| `application` | 用户端认证、工作台业务、HTTP/SSE 接口和业务装配 | 是，默认 `8080` | `framework`、`infra-ai` |
| `mcp-server` | 独立 MCP 协议入口和 MCP Tool 暴露 | 是 | `framework` |

业务 HTTP 后端由 `application` 和 `admin` 两个独立服务组成，二者不互相依赖。`mcp-server`
是可选的独立协议服务，不属于管理端或用户端 HTTP 服务。依赖方向固定为：

```text
application -> infra-ai -> framework
      +---------------------> framework

admin ----------------------> framework
mcp-server -----------------> framework
```

`framework` 不得反向依赖业务；`infra-ai` 不决定业务流程；`admin` 与 `application` 不得通过直接
依赖对方实现来复用代码。跨服务能力通过明确的 HTTP/API 契约交互。业务代码按业务模块组织，
模块内统一使用 `Controller -> Service -> Repo -> Mapper` 三层架构，详细约束见 `AGENTS.md`。

### `framework`

通用技术基础层，包括 Sa-Token 认证授权、Redis 缓存、请求上下文、错误码和通用响应、
MyBatis-Plus 配置、统一审计持久化、幂等、可观测性及 Web 基础配置。不放 Workflow、Agent、Prompt、Knowledge
等业务规则，也不放 AI 供应商适配。

### `infra-ai`

AI 技术适配层，负责 Chat Model、Embedding、Rerank、Tokenizer 和供应商 SDK 差异收敛。
不负责 Prompt 版本、Agent/Workflow 编排、权限和模型业务选择策略，不向上层暴露供应商 SDK 对象。

### `admin`

独立管理端服务，负责管理员登录、用户、角色、权限、菜单、部门/组织、系统配置和产生操作审计事件，
使用 Sa-Token `admin` 登录域，默认监听 `8081`。不直接访问 `application` 的 Mapper；需要操作工作台
业务资源时，通过稳定的内部 API 调用 `application`。

### `application`

独立用户端服务，负责用户登录与访问控制、HTTP/SSE 接口和业务服务，
并承载 Project、Application、Model Center、Prompt、Knowledge、Tool、Agent、Workflow、Runtime、
Harness 和 Evaluation 等工作台领域，使用 Sa-Token `user` 登录域，默认监听 `8080`。
Controller 中不得放业务编排、事务逻辑或直接访问 Repo、Mapper、AI Client。
当单个业务模块的规模和边界稳定后，再将其拆成独立 Maven 模块。

### `mcp-server`

可独立启动和部署的 MCP 协议入口，负责 Tool、Resource、Prompt 的协议映射、认证、参数校验、
错误映射和审计接入。不放 Workflow、Agent、Knowledge 等核心业务实现，不直接访问业务表。
需要复用业务能力时，应通过稳定 Application Port/SDK 或主应用内部 API 调用。

## Commands

```bash
./mvnw clean verify
./mvnw -pl application -am spring-boot:run
./mvnw -pl admin -am spring-boot:run
./mvnw -pl mcp-server -am spring-boot:run
```

本地依赖通过 `resources/docker/compose.yml` 启动，配置请从 `.env.example` 复制。两个业务服务默认连接
同一个 PostgreSQL 实例，但提供独立的连接环境变量，后续拆分数据库时无需修改配置结构。
