package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Application 路由前问题重写策略，随 ApplicationVersion 一起冻结。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContextualizationPolicy {

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 256;
    private static final int DEFAULT_MAX_OUTPUT_CHARACTERS = 1000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private boolean enabled;
    private Long chatModelInstanceId;
    private Long promptVersionId;
    private boolean fallbackToOriginal;
    private int maxOutputTokens;
    private int maxOutputCharacters;
    private int timeoutSeconds;

    public int effectiveMaxOutputTokens() {
        return maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
    }

    public int effectiveMaxOutputCharacters() {
        return maxOutputCharacters > 0 ? maxOutputCharacters : DEFAULT_MAX_OUTPUT_CHARACTERS;
    }

    public int effectiveTimeoutSeconds() {
        return timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    public static ContextualizationPolicy disabled() {
        return ContextualizationPolicy.builder()
                .enabled(false)
                .fallbackToOriginal(true)
                .maxOutputTokens(DEFAULT_MAX_OUTPUT_TOKENS)
                .maxOutputCharacters(DEFAULT_MAX_OUTPUT_CHARACTERS)
                .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
                .build();
    }
}
