package com.lingxi.workbench.chat.model.bo;

/**
 * 会话摘要的触发条件和单次输入边界。
 *
 * @param minNewRounds 新增完整轮数阈值
 * @param minNewTokens 新增消息 Token 阈值
 * @param maxInputTokens 旧摘要与新增消息共享的输入上限
 */
public record ConversationSummaryTrigger(int minNewRounds, int minNewTokens, int maxInputTokens) {
}
