package com.lingxi.workbench.agentruntime.checkpoint;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可跨进程恢复的 Agent ExecutionState 快照，不保存框架对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionStateSnapshot {

    private String runId;
    private Long agentId;
    private Long agentVersionId;
    private Integer maxModelCalls;
    private Integer maxToolCalls;
    private Integer maxTotalTokens;
    private Integer maxFinalizationTokens;
    private Integer modelCalls;
    private Integer toolCalls;
    private TokenUsage tokenUsage;
    private Long lastSequenceNo;
    private Boolean finalizationRequested;
    private String finalizationReason;
    private String output;
    private List<ToolCallCount> toolCallCounts;
    private List<ToolLedgerEntry> toolLedger;
    private List<SkillState> skills;
    private List<String> authorizedToolCodes;

    /**
     * @param toolCode Tool稳定编码 @param calls 已占用调用次数
     */
    public record ToolCallCount(String toolCode, int calls) {
    }

    /**
     * @param fingerprint Tool与规范化参数指纹 @param result 可复用结果
     */
    public record ToolLedgerEntry(String fingerprint, String result) {
    }

    /**
     * @param skillVersionId SkillVersion主键 @param checksum 冻结内容摘要 @param reason 本轮激活原因
     */
    public record SkillState(Long skillVersionId, String checksum, SkillActivationReason reason) {
    }
}
