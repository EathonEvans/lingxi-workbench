package com.lingxi.workbench.agentruntime.checkpoint;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * AgentScope ASK 中断时持久化的业务恢复事实。
 *
 * <p>模型消息、待确认 Tool 和 Permission 状态由 {@code agentStateJson} 承载；Lingxi 只额外保存
 * 发布版本、预算、审批指纹和数据库审计所需事实，避免重新维护框架消息编解码或第二层运行时快照。</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentScopeCheckpoint {

    public static final int CURRENT_VERSION = 4;

    private Integer version;
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
    private String runId;
    private String agentSessionId;
    private Long agentId;
    private Long agentVersionId;
    private Integer maxModelCalls;
    private Integer maxToolCalls;
    private Integer maxTotalTokens;
    private Integer maxFinalizationTokens;
    private Integer modelCalls;
    private Integer toolCalls;
    private Integer toolCallsInCurrentModelRound;
    private TokenUsage tokenUsage;
    private Long lastSequenceNo;
    private Boolean finalizationRequested;
    private String finalizationReason;
    private String output;
    private List<ToolCallCount> toolCallCounts;
    private List<String> authorizedToolCodes;
    private String agentStateJson;
    private ToolCallSnapshot pendingToolCall;
    private String toolConfigurationHash;
    private String resourceScope;
    private String fingerprint;
    private OffsetDateTime expiresAt;

    /**
     * AgentScope 待确认 Tool 的稳定审计投影。
     *
     * @param id AgentScope ToolUseBlock 标识
     * @param name 受版本锁定的 Tool 编码
     * @param input 经 AgentScope 解析后的 Tool 参数
     */
    public record ToolCallSnapshot(String id, String name, Map<String, Object> input) {
    }

    /**
     * @param toolCode Tool稳定编码
     * @param calls 已占用调用次数
     */
    public record ToolCallCount(String toolCode, int calls) {
    }
}
