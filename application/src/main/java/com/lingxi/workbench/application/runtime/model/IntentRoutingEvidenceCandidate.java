package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 经过安全裁剪的意图候选证据，不包含可变资源主键或 Prompt。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentRoutingEvidenceCandidate {
    private final String intentCode;
    private final String intentName;
    private final double confidence;
    private final String capabilityCode;
    private final String capabilityDescription;
}
