package com.lingxi.workbench.workflow.model.intent;

/**
 * 意图未命中或澄清失败时的用户可见兜底文案。
 *
 * @param unmatchedResponse 未召回任何可信意图时的答复
 * @param clarificationFailureResponse 澄清超限、取消或过期后的答复
 */
public record IntentFallbackPolicy(
        String unmatchedResponse,
        String clarificationFailureResponse) {
}
