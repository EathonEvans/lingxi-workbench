package com.lingxi.workbench.workflow.model.intent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 意图候选召回与结构化语义决策的不可变发布策略。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingPolicy {

    /** 候选叶子数量上限。 */
    private int topK;

    /** 最低召回相似度。 */
    private BigDecimal recallThreshold;

    /** 结构化语义决策使用的 Chat 模型实例。 */
    private Long chatModelInstanceId;

    /** 结构化语义决策使用的已发布 SYSTEM PromptVersion。 */
    private Long decisionPromptVersionId;

    /** 单次语义决策最大输出 Token。 */
    private int maxOutputTokens;

    /** 单次语义决策超时秒数。 */
    private int timeoutSeconds;

    /** 结构化输出校验失败后的修复次数，平台最多一次。 */
    private int maxRepeatAttempts;

    /** 语义决策最终失败后的显式处理动作。 */
    private IntentDecisionFailureAction decisionFailureAction;

    public int topK() {
        return topK;
    }

    public BigDecimal recallThreshold() {
        return recallThreshold;
    }

    public Long chatModelInstanceId() {
        return chatModelInstanceId;
    }

    public Long decisionPromptVersionId() {
        return decisionPromptVersionId;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public int maxRepeatAttempts() {
        return maxRepeatAttempts;
    }

    public IntentDecisionFailureAction decisionFailureAction() {
        return decisionFailureAction;
    }
}
