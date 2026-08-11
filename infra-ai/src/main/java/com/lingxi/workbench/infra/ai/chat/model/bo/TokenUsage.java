package com.lingxi.workbench.infra.ai.chat.model.bo;

import java.util.Objects;

/**
 * 项目内部统一的模型 Token 用量快照。
 *
 * <p>该对象不实现 Spring AI Usage，避免供应商框架类型泄漏到业务层。
 * 当供应商没有返回 totalTokens，但同时返回了输入和输出用量时，自动计算总量。</p>
 *
 * @param inputTokens  输入 Token 数
 * @param outputTokens 输出 Token 数
 * @param totalTokens  总 Token 数
 */
public record TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {

    public TokenUsage {
        if (Objects.isNull(totalTokens) && Objects.nonNull(inputTokens) && Objects.nonNull(outputTokens)) {
            totalTokens = inputTokens + outputTokens;
        }
    }

    /** 创建供应商未返回 Token 用量时的空快照。 */
    public static TokenUsage empty() {
        return new TokenUsage(null, null, null);
    }

    /** 创建一次模型响应的 Token 用量，缺少总量时自动计算。 */
    public static TokenUsage of(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        return new TokenUsage(inputTokens, outputTokens, totalTokens);
    }

    /** 判断供应商是否返回了任意 Token 用量。 */
    public boolean hasUsage() {
        return Objects.nonNull(inputTokens) || Objects.nonNull(outputTokens) || Objects.nonNull(totalTokens);
    }

    /** 返回当前快照与另一个快照逐项累计后的新快照。 */
    public TokenUsage plus(TokenUsage other) {
        if (Objects.isNull(other)) {
            return this;
        }
        return TokenUsage.of(sum(inputTokens, other.inputTokens), sum(outputTokens, other.outputTokens), sum(totalTokens, other.totalTokens));
    }

    private static Integer sum(Integer left, Integer right) {
        if (Objects.isNull(left) && Objects.isNull(right)) {
            return null;
        }
        return Objects.requireNonNullElse(left, 0) + Objects.requireNonNullElse(right, 0);
    }
}
