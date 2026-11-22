package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Application 共享上下文各分区的 Token 上限，随 ApplicationVersion 一起冻结。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContextBudgetPolicy {

    public static final int DEFAULT_MAX_HISTORY_ROUNDS = 20;
    public static final int DEFAULT_SYSTEM_PROMPT_TOKENS = 4000;
    public static final int DEFAULT_CAPABILITY_TOKENS = 4000;
    public static final int DEFAULT_HISTORY_TOKENS = 12000;
    public static final int DEFAULT_MEMORY_TOKENS = 4000;
    public static final int DEFAULT_INPUT_TOKENS = 4000;
    public static final int DEFAULT_OBSERVATION_TOKENS = 16000;
    public static final int DEFAULT_RESERVED_OUTPUT_TOKENS = 4096;

    private int maxHistoryRounds;
    private int maxSystemPromptTokens;
    private int maxCapabilityTokens;
    private int maxHistoryTokens;
    private int maxMemoryTokens;
    private int maxInputTokens;
    private int maxObservationTokens;
    private int reservedOutputTokens;

    public static ContextBudgetPolicy defaults() {
        return ContextBudgetPolicy.builder()
                .maxHistoryRounds(DEFAULT_MAX_HISTORY_ROUNDS)
                .maxSystemPromptTokens(DEFAULT_SYSTEM_PROMPT_TOKENS)
                .maxCapabilityTokens(DEFAULT_CAPABILITY_TOKENS)
                .maxHistoryTokens(DEFAULT_HISTORY_TOKENS)
                .maxMemoryTokens(DEFAULT_MEMORY_TOKENS)
                .maxInputTokens(DEFAULT_INPUT_TOKENS)
                .maxObservationTokens(DEFAULT_OBSERVATION_TOKENS)
                .reservedOutputTokens(DEFAULT_RESERVED_OUTPUT_TOKENS)
                .build();
    }
}
