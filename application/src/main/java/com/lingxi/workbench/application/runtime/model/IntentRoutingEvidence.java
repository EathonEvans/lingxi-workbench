package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingFallbackReason;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/** fallback Agent 可选消费的结构化路由证据。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingEvidence {
    private final IntentRoutingFallbackReason reason;
    private final List<IntentRoutingEvidenceCandidate> candidates;

    public List<IntentRoutingEvidenceCandidate> candidates() {
        return List.copyOf(Objects.requireNonNullElse(candidates, List.of()));
    }
}
