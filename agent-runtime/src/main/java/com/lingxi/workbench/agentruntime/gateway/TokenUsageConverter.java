package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Objects;

/**
 * 将 Spring AI Usage 转换为项目内部 TokenUsage。
 */
public final class TokenUsageConverter {

    private TokenUsageConverter() {
    }

    /** 转换模型响应中的用量；响应没有 Usage 时返回空快照。 */
    public static TokenUsage convert(Usage usage) {
        if (Objects.isNull(usage)) {
            return TokenUsage.empty();
        }
        return TokenUsage.of(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /** 从完整 ChatResponse 转换用量，供 typed structured output 调用边界复用。 */
    public static TokenUsage convert(ChatResponse response) {
        if (Objects.isNull(response) || Objects.isNull(response.getMetadata())) {
            return TokenUsage.empty();
        }
        return convert(response.getMetadata().getUsage());
    }

    /**
     * 转换流式分片中的真实用量。
     *
     * <p>部分 OpenAI 兼容服务会在普通中间分片返回全零 Usage，该值不代表最终计费用量。</p>
     */
    public static TokenUsage convertActual(Usage usage) {
        TokenUsage tokenUsage = convert(usage);
        return hasPositiveValue(tokenUsage) ? tokenUsage : TokenUsage.empty();
    }

    private static boolean hasPositiveValue(TokenUsage usage) {
        return positive(usage.inputTokens()) || positive(usage.outputTokens()) || positive(usage.totalTokens());
    }

    private static boolean positive(Integer value) {
        return Objects.nonNull(value) && value > 0;
    }
}
