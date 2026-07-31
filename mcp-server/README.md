# Lingxi 本地 MCP Server

`mcp-server` 是独立部署的 MCP 协议服务。它不访问管理端数据库，也不依赖
`admin` 或 `application` 的业务实现。

当前阶段使用 Spring AI 2.0 WebMVC Streamable HTTP Starter，将平台已有
`AgentTool` 转换为 MCP Tool：

- `system_calculator`：基础四则运算；
- `system_current_time`：查询指定 IANA 时区的当前时间。

Function Calling 与 MCP 共用相同的工具执行器、参数 Schema 和服务端校验，
MCP 模块只承担协议暴露职责。

## 本地启动

```bash
./mvnw -pl mcp-server -am spring-boot:run
```

默认连接参数：

```text
Base URL: http://127.0.0.1:8091
Endpoint: /mcp
Transport: Streamable HTTP
Authentication: None
```

服务默认只监听回环地址。需要容器或其他主机访问时，显式设置：

```bash
LINGXI_MCP_SERVER_HOST=0.0.0.0
```

不要在未配置网络访问控制和标准 OAuth 资源服务器之前，将当前本地服务直接暴露到公网。

## 接入 Agent 的完整路径

1. 打开「Agent 管理 → MCP 服务」；
2. 新建 MCP Server，填写上面的本地连接参数；
3. 保存后执行「测试连接」；
4. 最近一次连接测试成功后启用服务；
5. 执行「发现工具」，选择远端工具并「导入为 Tool」；
6. 在「Agent 管理 → Tool 管理」中确认调用说明并启用该 Tool；
7. 在 Agent 草稿中绑定平台 `toolCode`，校验并发布；
8. Application 对话运行时会根据 Tool 来源自动选择本地执行器或 MCP Server。

Agent 不直接保存 MCP 地址和远端工具定义，只绑定稳定的 `toolCode`。
因此调用限额、SSE 工具事件、审计和模型工具协议仍复用同一条运行链。

## 协议回归测试

```bash
./mvnw -pl mcp-server -am \
  -Dtest=McpServerProtocolIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

测试会启动随机本地端口，并真实执行：

```text
initialize → tools/list → tools/call
```

同时覆盖计算成功和除零失败两条工具调用路径。
