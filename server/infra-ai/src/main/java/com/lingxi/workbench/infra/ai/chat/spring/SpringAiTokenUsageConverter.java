package com.lingxi.workbench.infra.ai.chat.spring;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Objects;

/** Spring AI Usage 到 Lingxi 稳定用量值的供应商边界转换器。 */
public final class SpringAiTokenUsageConverter {

    private SpringAiTokenUsageConverter() {
    }

    public static TokenUsage convert(Usage usage) {
        if (usage == null) {
            return TokenUsage.empty();
        }
        return TokenUsage.of(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    public static TokenUsage convert(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return TokenUsage.empty();
        }
        return convert(response.getMetadata().getUsage());
    }

    /** 普通流式分片中的全零 Usage 不是最终计费用量。 */
    public static TokenUsage convertActual(Usage usage) {
        TokenUsage converted = convert(usage);
        return hasPositiveValue(converted) ? converted : TokenUsage.empty();
    }

    private static boolean hasPositiveValue(TokenUsage usage) {
        return positive(usage.inputTokens()) || positive(usage.outputTokens()) || positive(usage.totalTokens());
    }

    private static boolean positive(Integer value) {
        return Objects.nonNull(value) && value > 0;
    }
}
