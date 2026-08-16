package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Application 单轮请求的总执行边界。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationRuntimePolicy {

    private int maxModelCalls;
    private int maxToolCalls;
    private int maxDelegations;
    private int totalTokenBudget;
    private Integer tokenGracePercent;
    private int timeoutSeconds;
    private Integer finalizationReserveSeconds;
    private Integer timeoutGracePercent;

    public static ApplicationRuntimePolicy defaults() {
        return ApplicationRuntimePolicy.builder()
                .maxModelCalls(16)
                .maxToolCalls(20)
                .maxDelegations(3)
                .totalTokenBudget(64000)
                .tokenGracePercent(10)
                .timeoutSeconds(300)
                .finalizationReserveSeconds(30)
                .timeoutGracePercent(10)
                .build();
    }

    /**
     * 返回仅供最终回答收口使用的Token缓冲比例。
     *
     * <p>普通规划、工具和委派不得使用该额度；未显式配置时使用10%。</p>
     */
    public int effectiveTokenGracePercent() {
        return java.util.Objects.requireNonNullElse(tokenGracePercent, 10);
    }

    /** 返回软预算与收口缓冲之和，并防止整数溢出。 */
    public int effectiveTokenHardBudget() {
        long graceTokens = ((long) totalTokenBudget * effectiveTokenGracePercent() + 99L) / 100L;
        return Math.toIntExact(Math.min((long) totalTokenBudget + graceTokens, Integer.MAX_VALUE));
    }

    /** 返回总超时前预留给禁用工具结果收口的时间。 */
    public int effectiveFinalizationReserveSeconds() {
        return java.util.Objects.requireNonNullElse(finalizationReserveSeconds, 30);
    }

    /** 返回流式输出仍活跃时允许使用的一次性硬超时宽限比例。 */
    public int effectiveTimeoutGracePercent() {
        return java.util.Objects.requireNonNullElse(timeoutGracePercent, 10);
    }

    /** 返回一次性流式宽限时长，向上取整到秒。 */
    public int effectiveTimeoutGraceSeconds() {
        long graceSeconds = ((long) timeoutSeconds * effectiveTimeoutGracePercent() + 99L) / 100L;
        return Math.toIntExact(Math.min(graceSeconds, Integer.MAX_VALUE));
    }
}
