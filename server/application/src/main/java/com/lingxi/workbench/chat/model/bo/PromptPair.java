package com.lingxi.workbench.chat.model.bo;

/**
 * 注入模型的最终 Prompt 文本对。
 *
 * @param userPrompt   拼接用户问题和知识库片段后的用户消息
 * @param systemPrompt 系统提示词
 */
public record PromptPair(String userPrompt, String systemPrompt) {
}
