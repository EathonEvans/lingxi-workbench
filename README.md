# Lingxi Workbench Web

灵析 AiWorkbench 最终用户端，基于 [Nuxt UI Chat Vue](https://github.com/nuxt-ui-templates/chat-vue)
的 MIT 开源版二次开发。面向使用 AI 应用的普通用户，不提供模型、Prompt、Knowledge、
Agent 和 Workflow 等平台管理能力。

## Stack

- Vue 3 + TypeScript
- Vite
- Nuxt UI
- Vue Router
- AI SDK Vue
- Nitro（上游基线暂时保留）

## Scope

- AI 应用入口和会话列表。
- 流式回答、思考过程、Markdown 和代码展示。
- 文件上传、Tool Calling 结果和多模态消息。
- 最终用户会话历史、个人设置与移动端适配。

用户、会话、消息、文件和 Agent Run 的事实数据将由 `lingxi-workbench-server` 管理。
上游自带的 GitHub OAuth、SQLite/Drizzle、Vercel AI Gateway 和 Nitro 业务路由作为当前可运行基线暂时保留，
后续按功能迭代逐步替换为 Spring Boot + Sa-Token + HTTP/SSE 接口。

## Commands

```bash
pnpm install --frozen-lockfile
pnpm dev
pnpm lint
pnpm typecheck
pnpm build
```

## Project boundary

- 后端仓库：`EathonEvans/lingxi-workbench-server`
- 管理端仓库：`EathonEvans/lingxi-workbench-admin`
- 后端是认证、权限、会话、模型调用和 Agent Run 的安全边界。
- 浏览器不存储模型供应商 API Key，不直接调用模型供应商。

## Upstream

当前基线来自 Nuxt UI Chat Vue commit `1d94e2cdd764ff590aa6e24639234e49073edadc`。
上游版权和 MIT License 见 [LICENSE](LICENSE)，同步策略见 [UPSTREAM.md](UPSTREAM.md)。
