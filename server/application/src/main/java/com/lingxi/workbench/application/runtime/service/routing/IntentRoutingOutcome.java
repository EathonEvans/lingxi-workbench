package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 尚未提交的路由计算结果；会话实体只在路由内部流转，提交后仅返回对外结果。 */
@Getter
@Builder(toBuilder = true)
public class IntentRoutingOutcome {
    private final IntentRoutingResult result;
    private final IntentRoutingSessionEntity session;
    private final PendingState expectedState;
    @Builder.Default
    private final List<IntentCandidate> candidates = List.of();
    private final IntentSemanticDecisionResult semanticResult;

    public static IntentRoutingOutcome completed(IntentRoutingResult result) {
        return builder().result(result).build();
    }

    /**
     * 恢复时读到的会话进度，提交时用于拒绝重复或过期的回复。
     * @param type 回复对应的澄清类型
     * @param round 回复对应的轮次
     */
    public record PendingState(IntentClarificationType type, int round) { }
}
