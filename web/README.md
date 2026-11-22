# Lingxi Workbench Web

Lingxi Workbench 用户端前端。面向企业用户提供 AI 对话、智能体使用、知识库检索与任务协作能力。

> 本目录只承载用户端，不包含运营管理功能。服务端位于 [`server/`](../server)（管理端暂未开源）。

## 功能

- SSE 流式 AI 会话（意图路由、Agent / Workflow 双引擎响应的服务端推送渲染）
- 执行轨迹可视化：展示意图路由决策与工作流 / Agent 执行过程
- 通知中心：主动任务与人工协同事项的站内通知
- 定时触发器管理：创建与查看触达式主动运行任务
- 登录态与会话管理（Sa-Token，同源 Cookie）

## 技术栈

- [Next.js](https://nextjs.org/) 16（App Router）
- React 19 + TypeScript
- [LobeUI](https://github.com/lobehub/lobe-ui) 组件库
- Ant Design 6 + antd-style
- Lucide React
- pnpm

本项目使用 LobeUI 作为设计系统与 AI 交互组件底座，没有复制 LobeHub 完整应用源码，也不在浏览器中直连模型供应商。认证、模型调用、知识库与业务数据统一经由灵析后端服务。

## 本地开发

环境要求：Node.js 22+、pnpm 10+。

```bash
cp .env.example .env.local
pnpm install
pnpm dev
```

默认访问地址：<http://localhost:3000>。

## 常用命令

```bash
pnpm dev       # 启动开发服务器
pnpm lint      # ESLint 静态检查
pnpm build     # 生产构建与类型检查
pnpm start     # 启动生产服务器
```

## 目录结构

```text
src/
├── app/                 # Next.js 路由、页面与全局样式
└── components/          # 用户端界面与跨页面组件
```

后续按业务增长增加 `features/`、`services/`、`stores/` 等目录；在出现真实复用和复杂度之前不提前分层。

## 接口边界

- 前端只保存后端签发的会话状态，不持有模型厂商密钥。
- API 根地址通过 `NEXT_PUBLIC_API_BASE_URL` 配置。
- Sa-Token 认证由后端负责，前端通过同源 Cookie 或受控请求头接入。
- 接口契约以服务端 OpenAPI 为准，接入后再生成类型安全客户端。

## 图标规范

- 用户端业务图标统一使用 Lucide React，与 LobeUI 保持一致。
- 不在业务组件中手写 SVG 路径，也不混用其他通用图标库。

## 开源说明

项目自身采用 [Apache-2.0](./LICENSE) License。第三方依赖仍遵循各自许可证，详见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)。
