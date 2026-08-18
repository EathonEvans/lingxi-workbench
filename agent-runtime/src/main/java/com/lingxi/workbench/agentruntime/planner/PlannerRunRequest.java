package com.lingxi.workbench.agentruntime.planner;

import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Application 完成版本、Prompt 和能力目录准备后提交给 Planner Runtime 的可信请求。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerRunRequest {

    private final String runId;
    private final Long agentId;
    private final Long agentVersionId;
    private final Integer planNo;
    private final String systemPrompt;
    private final String userPrompt;
    private final List<ChatHistoryMessage> history;
    private final ChatModelRuntimeConfig modelConfig;
    private final PlannerDecisionValidationContext validationContext;
    private final int maxProtocolRepairAttempts;
    private final ExecutionPolicy policy;
    private final RuntimeEventSink eventSink;
    private final long initialSequenceNo;

    public List<ChatHistoryMessage> getHistory() {
        return List.copyOf(java.util.Objects.requireNonNullElseGet(history, List::of));
    }
}
