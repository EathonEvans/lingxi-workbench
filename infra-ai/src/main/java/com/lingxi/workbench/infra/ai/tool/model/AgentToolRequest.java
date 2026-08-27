package com.lingxi.workbench.infra.ai.tool.model;

import java.util.Objects;

/**
 * Agent Tool 的稳定执行请求，不暴露 Spring AI 或 AgentScope 类型。
 *
 * @param input          模型生成并经 Schema 约束的 JSON 参数
 * @param execution     服务端可信执行身份
 * @param idempotencyKey 审批恢复时锁定的幂等键，普通调用可为空
 */
public record AgentToolRequest(String input, AgentToolExecutionContext execution, String idempotencyKey) {

    public AgentToolRequest {
        input = Objects.requireNonNullElse(input, "{}");
        Objects.requireNonNull(execution, "Tool执行上下文不能为空");
    }
}
