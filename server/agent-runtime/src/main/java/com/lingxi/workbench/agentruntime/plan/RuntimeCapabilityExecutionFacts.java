package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

import java.util.Objects;

/**
 * 子能力结束时回传给 Supervisor 的实际执行事实。
 *
 * @param modelCalls 已发生的模型调用数
 * @param toolCalls 已发生的工具调用数
 * @param usage 已发生的 Token 用量
 * @param lastSequenceNo 子能力已发布的最后事件序号
 */
public record RuntimeCapabilityExecutionFacts(int modelCalls, int toolCalls, TokenUsage usage,
                                              long lastSequenceNo) {

    public RuntimeCapabilityExecutionFacts {
        if (modelCalls < 0 || toolCalls < 0 || lastSequenceNo < 0) {
            throw new IllegalArgumentException("子能力执行事实不能小于0");
        }
        usage = Objects.requireNonNullElseGet(usage, TokenUsage::empty);
    }
}
