package com.lingxi.workbench.knowledge.model.bo;

import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/** 本次检索的分阶段诊断，只包含资源标识、排名、预算和耗时。 */
@Getter
@Builder
public class KnowledgeSearchDiagnostics {
    private final Map<AiObservationStage, Long> stageMillis;
    private final List<KnowledgeSearchHit> candidates;
    private final List<KnowledgeSearchHit> selected;
    private final int truncatedDocuments;
    private final int droppedDocuments;
    private final int estimatedRerankTokens;
    private final long totalMillis;
}
