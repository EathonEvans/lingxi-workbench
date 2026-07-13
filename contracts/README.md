# Contracts

后端是 OpenAPI、Workflow Definition、Run Event、Tool 与 Handoff Schema 的权威发布方。

- `openapi/`: HTTP/SSE API 基线。
- `workflow/`: Workflow Definition JSON Schema。
- `events/`: Run Event JSON Schema。
- `tool/`: Tool、Handoff 与 Structured Output Schema。

前端只消费带版本号的契约制品，不通过相对路径依赖本目录。
