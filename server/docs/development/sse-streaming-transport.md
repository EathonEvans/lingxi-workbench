# SSE 真实流式传输问题记录

> 状态：已解决  
> 记录时间：2026-07-16  
> 适用范围：`application`、用户端前端及其反向代理/网关

## 1. 问题现象

应用端对话页面具有逐字渲染效果，但实际请求中，后端产生的多个 SSE 数据块会在模型响应结束后一次性到达浏览器。
因此页面看起来像流式输出，端到端链路实际上仍是批量返回。

## 2. 根因

Spring AI 返回的 `Flux` 和 application 输出的 `Flux<ServerSentEvent<?>>` 均能够持续产生增量数据。
问题发生在 HTTP 传输链路：用户端通过 Next.js `/api` rewrite 访问后端，代理对 SSE 响应启用压缩后会缓存小数据块，
直到缓存达到阈值或响应结束才统一发送。

这类问题与前端打字机效果、模型是否分块输出无关，也不能通过把文本继续拆成更小片段解决。

## 3. 处理方案

保留 Spring AI 原生响应式调用链：

```text
Spring AI Flux
    -> ChatStreamService
    -> Flux<ServerSentEvent<?>>
    -> framework SSE 响应过滤器
    -> 前端 ReadableStream
```

framework 通过 `SseResponseHeaderFilter` 统一识别显式携带 `Accept: text/event-stream` 的请求，并设置：

- `Cache-Control: no-cache, no-transform`：禁止中间节点转换响应；
- `Content-Encoding: identity`：禁止对事件流进行内容压缩；
- `X-Accel-Buffering: no`：通知支持该响应头的反向代理关闭缓冲。

过滤器不依赖具体接口路径。业务 Controller 不得重复设置这些响应头，也不得为了表现出流式效果主动休眠、
逐字符发送或引入仅用于规避代理缓冲的特殊逻辑。

## 4. 技术决策

当前项目继续使用 `Flux<ServerSentEvent<?>>`，不改为 `SseEmitterSender`：

- Spring AI 原生返回 `Flux`，无需转换为命令式回调；
- `Flux` 能自然传播取消信号，并支持流组合和异常恢复；
- `SseEmitterSender` 适合多线程主动推送、后台任务回调和手动管理连接的场景；
- 更换 SSE 编程模型无法解决代理压缩与缓冲问题。

如果后续出现后台任务主动向客户端连接推送事件的需求，再单独评估 `SseEmitter` 封装，不在当前链路同时维护两套模型。

## 5. 开发与部署约束

1. 前端流式请求必须显式发送 `Accept: text/event-stream`；
2. 前端必须直接消费 `Response.body`，不得先调用 `response.text()` 再模拟逐字输出；
3. 新增 Nginx、Ingress、API Gateway 或 CDN 时，必须确认 SSE 路由未被压缩和缓冲；
4. 不得只根据浏览器动画判断流式是否生效，应检查网络数据块的实际到达时间；
5. 普通 JSON 请求和 `Accept: */*` 请求不得套用 SSE 响应策略；
6. 调整传输配置后必须重启 application，并通过真实代理链路回归。

## 6. 验证标准

- application 持续产生多个 SSE `message` 事件，最终发送 `done` 事件；
- 携带浏览器常见 `Accept-Encoding` 请求头时，SSE 响应仍不启用 gzip/br 压缩；
- 客户端在模型生成期间持续收到网络数据块，而不是响应结束后一次收到；
- 普通 JSON 接口不携带 SSE 专用响应头；
- 取消前端请求后，上游 Flux 能收到取消信号并停止后续处理。

