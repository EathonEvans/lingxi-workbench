# Lingxi Workbench Web

## Project map

- `src/pages`: 用户端路由页面。
- `src/components`: 通用和聊天组件。
- `src/composables`: 会话、消息、认证和模型状态。
- `src/utils`: 纯前端工具。
- `server`: 上游 Nitro 基线，将逐步替换为灵析后端 API。
- `shared`: 当前前后共享类型与工具。

## Commands

```bash
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm build
```

## Invariants

1. 用户端不承载平台管理功能。
2. 浏览器不持有模型供应商 API Key，不直连模型。
3. Sa-Token 服务端鉴权是安全边界，前端路由守卫不是授权依据。
4. 会话、消息和 Run 事实以后端为准，前端只管理交互状态。
5. 保留 Nuxt UI Chat Vue LICENSE 和 UPSTREAM 记录。
