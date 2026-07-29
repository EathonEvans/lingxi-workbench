package com.lingxi.workbench.chat.model.ccontext;

/** 将处理链内部模型调用的 Token 用量汇总到当前执行上下文。 */
@FunctionalInterface
public interface ModelUsageRecorder {

    /**
     * 记录一次模型响应返回的用量。
     *
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param totalTokens  总 Token 数
     */
    void record(Integer inputTokens, Integer outputTokens, Integer totalTokens);
}
