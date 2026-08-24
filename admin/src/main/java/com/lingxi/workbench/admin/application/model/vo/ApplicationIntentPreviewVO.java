package com.lingxi.workbench.admin.application.model.vo;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.Data;

import java.util.List;

/** Application 草稿意图测试结果；只展示决策证据，不包含能力执行结果。 */
@Data
public class ApplicationIntentPreviewVO {

    private List<ApplicationIntentPreviewCandidateVO> candidates;
    private String semanticAction;
    private String semanticReasonCode;
    private String selectedIntentCode;
    private List<String> alternativeIntentCodes;
    private List<ApplicationIntentPreviewSlotVO> recognizedSlots;
    private String finalAction;
    private String finalReasonCode;
    private String targetType;
    private String capabilityCode;
    private Long modelInstanceId;
    private Long promptVersionId;
    private TokenUsage tokenUsage;
    private int modelCalls;
    private int retryCount;
    private long durationMillis;
}
