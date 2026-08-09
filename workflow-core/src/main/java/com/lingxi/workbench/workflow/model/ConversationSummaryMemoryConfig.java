package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 会话滚动摘要配置。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConversationSummaryMemoryConfig {

    private static final int DEFAULT_MIN_ROUNDS = 12;
    private static final int DEFAULT_MIN_TOKENS = 4_000;

    /** 是否启用滚动摘要。 */
    private boolean enabled;
    /** Chat 模型实例主键。 */
    private Long chatModelInstanceId;
    /** 已发布 PromptVersion 主键。 */
    private Long promptVersionId;
    /** 触发摘要的最小新增轮次数，默认 12。 */
    private int minRounds;
    /** 触发摘要的最小新增 token 数，默认 4000。 */
    private int minTokens;

    public int effectiveMinRounds() {
        return minRounds > 0 ? minRounds : DEFAULT_MIN_ROUNDS;
    }

    public int effectiveMinTokens() {
        return minTokens > 0 ? minTokens : DEFAULT_MIN_TOKENS;
    }

    public static ConversationSummaryMemoryConfig disabled() {
        return ConversationSummaryMemoryConfig.builder()
                .enabled(false)
                .minRounds(DEFAULT_MIN_ROUNDS)
                .minTokens(DEFAULT_MIN_TOKENS)
                .build();
    }
}
