package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/** 将处理链内部模型调用的 Token 用量汇总到当前执行上下文。 */
@FunctionalInterface
public interface ModelUsageRecorder {

    /**
     * 记录一次模型响应返回的用量。
     *
     * @param usage 当前模型响应的 Token 用量
     */
    void record(TokenUsage usage);
}
