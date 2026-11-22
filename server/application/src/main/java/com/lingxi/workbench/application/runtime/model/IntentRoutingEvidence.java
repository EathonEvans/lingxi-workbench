package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * 路由决策证据：fallback Agent 可选消费，同时作为 INTENT_ROUTE 观测事件
 * 的质量数据（候选编码 + 相似度 + 门控原因），用于意图树调优。
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingEvidence {
    private final IntentRoutingReason reason;
    private final List<IntentRoutingEvidenceCandidate> candidates;

    public List<IntentRoutingEvidenceCandidate> candidates() {
        return List.copyOf(Objects.requireNonNullElse(candidates, List.of()));
    }
}
