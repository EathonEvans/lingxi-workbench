package com.lingxi.workbench.agentruntime.checkpoint;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Application 在进入 Runtime 前冻结的检查点定位上下文。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCheckpointContext {

    private String query;
    private String capabilityCode;
    private Long applicationVersionId;
    private Long userId;
    private Long conversationId;
    private Long assistantMessageId;
    private List<String> explicitSkillCodes;
    private TokenUsage applicationUsageBeforeAgent;
    private Integer applicationModelCallsBeforeAgent;
    private Integer applicationToolCallsBeforeAgent;
}
