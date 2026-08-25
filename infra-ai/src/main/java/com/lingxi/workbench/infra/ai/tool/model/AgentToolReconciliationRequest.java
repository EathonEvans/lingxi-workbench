package com.lingxi.workbench.infra.ai.tool.model;

import java.util.Objects;

/**
 * @param idempotencyKey 外部系统用于查询同一次副作用的稳定幂等键
 * @param toolInput 原始 Tool 入参的 JSON 文本，仅传给同一受信任执行器
 * @param executionContext 服务端可信执行身份，不由模型或远端响应提供
 */
public record AgentToolReconciliationRequest(String idempotencyKey, String toolInput,
                                             AgentToolExecutionContext executionContext) {

    public AgentToolReconciliationRequest {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Tool对账幂等键不能为空");
        }
        if (toolInput == null || toolInput.isBlank()) {
            throw new IllegalArgumentException("Tool对账原始入参不能为空");
        }
        Objects.requireNonNull(executionContext, "Tool对账执行上下文不能为空");
    }
}
