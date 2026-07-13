# Lingxi Workbench Server

## Project map

- `framework`: 共享工程能力，不依赖业务模块。
- `infra-ai`: AI Port 与供应商适配，只依赖 `framework`。
- `bootstrap`: 主应用和业务领域，依赖 `framework`、`infra-ai`。
- `mcp-server`: 独立 MCP 应用，不依赖 `bootstrap`。
- `contracts`: OpenAPI、Workflow、Run Event 与 Tool Schema 的权威源。

## Commands

```bash
./scripts/bootstrap.sh
./scripts/dev-start.sh
./scripts/verify.sh
./scripts/dev-stop.sh
```

## Invariants

1. Domain 不依赖 Spring AI、MyBatis 或供应商 SDK。
2. Controller 不承载业务编排，不直接访问 Mapper。
3. 领域间只访问对方公开 `api`。
4. Workflow Version、Run Snapshot、Step Attempt、Run Event 不可覆盖。
5. 所有资源访问必须校验 Project Scope。
6. Redis 不是 Run 事实来源。
7. 密钥、Prompt 正文和 Tool 参数默认不进入日志。
8. 历史 Flyway migration 禁止修改。

## Definition of done

- 编译、单元测试和架构测试通过。
- API/Schema 变更同步契约并检查兼容性。
- 数据变更使用新 Flyway migration。
- 新运行路径包含 traceId、runId 和可操作错误码。
