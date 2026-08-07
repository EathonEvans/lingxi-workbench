package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;

/**
 * @param content    面向用户的最终回答
 * @param tokenUsage 本次无Tool模型调用用量
 */
public record FinalSynthesisResult(String content, TokenUsage tokenUsage) {
}
