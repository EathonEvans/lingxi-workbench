package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Application执行的完整结果或已持久化人工中断。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationChatResult {

    private final String answer;
    private final TokenUsage tokenUsage;
    private final List<WorkflowNodeTrace> traces;
    private final Long modelInstanceId;
    private final RuntimeInterruption interruption;

    public static ApplicationChatResult completed(String answer, TokenUsage usage,
                                                  List<WorkflowNodeTrace> traces, Long modelInstanceId) {
        return new ApplicationChatResult(answer, usage, traces == null ? List.of() : List.copyOf(traces),
                modelInstanceId, null);
    }

    public static ApplicationChatResult interrupted(TokenUsage usage, RuntimeInterruption interruption) {
        return new ApplicationChatResult("", usage, List.of(), null, interruption);
    }

    public boolean isInterrupted() {
        return interruption != null;
    }

    public String answer() {
        return answer;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public List<WorkflowNodeTrace> traces() {
        return traces;
    }

    public Long modelInstanceId() {
        return modelInstanceId;
    }
}
