package com.lingxi.workbench.application.runtime.model.vo;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** 刷新页面后恢复展示的待处理 HITL 交互。 */
@Data
@Builder
public class PendingInteractionVO {

    private String interactionId;
    private String assistantMessageId;
    private InteractionSessionType interactionType;
    private String title;
    private String summary;
    private String riskLevel;
    private String resourceScope;
    private String fingerprint;
    private Integer revision;
    private OffsetDateTime expiresAt;
}
