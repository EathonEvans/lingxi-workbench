package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentRecognitionStatus;

import java.util.List;

/**
 * 与能力映射无关的意图识别结果。
 *
 * @param status     识别状态
 * @param candidates 命中候选；明确命中时仅包含第一候选
 */
public record IntentRecognitionResult(IntentRecognitionStatus status, List<IntentRecognitionCandidate> candidates) {

    public IntentRecognitionResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
