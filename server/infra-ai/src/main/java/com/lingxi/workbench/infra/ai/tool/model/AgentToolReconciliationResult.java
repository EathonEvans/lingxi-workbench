package com.lingxi.workbench.infra.ai.tool.model;

import java.util.Objects;

/**
 * @param status 外部系统可证明的对账结论
 * @param result 已确认成功时的结果摘要，其他状态可以为空
 * @param errorCode 安全的稳定错误码，不传递远端原始异常文本
 */
public record AgentToolReconciliationResult(AgentToolReconciliationStatus status, String result,
                                            String errorCode) {

    public AgentToolReconciliationResult {
        Objects.requireNonNull(status, "Tool对账结论不能为空");
        if (status == AgentToolReconciliationStatus.SUCCEEDED && (result == null || result.isBlank())) {
            throw new IllegalArgumentException("Tool对账确认成功时必须提供结果摘要");
        }
    }
}
