# Lingxi Workbench Server

灵析 AiWorkbench 后端工程。项目采用 Java 21、Spring Boot 3.5、Spring AI 1.1、Sa-Token、MyBatis-Plus、PostgreSQL + pgvector。

## Modules

- `framework`: 认证、上下文、缓存、数据库、Web 与可观测性等共享基础设施。
- `infra-ai`: Chat、Embedding、Rerank、Token 与模型供应商适配。
- `bootstrap`: 主应用与业务领域。
- `mcp-server`: 可独立启动的 MCP 示例与测试服务。

## Commands

```bash
./mvnw clean verify
./mvnw -pl bootstrap -am spring-boot:run
./mvnw -pl mcp-server -am spring-boot:run
```

本地依赖通过 `resources/docker/compose.yml` 启动，配置请从 `.env.example` 复制。
