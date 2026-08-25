package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Application 完成版本和可信上下文准备后提交给 Runtime 的强类型请求。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentRunRequest {

    private final String runId;
    private final Long agentId;
    private final Long agentVersionId;
    private final ExecutionScope scope;
    private final ChatStreamCommand modelCommand;
    private final RuntimeContext runtimeContext;
    private final SkillRuntimeCatalog skillCatalog;
    private final List<String> authorizedToolCodes;
    private final boolean finalizationOnly;
    private final ExecutionPolicy policy;
    private final RuntimeEventSink eventSink;
    private final long initialSequenceNo;
    private final int initialModelCalls;
    private final TokenUsage initialUsage;
    /** 仅包含审批归属的 Checkpoint 种子；动态运行事实由 Runtime 在 ASK 时补齐。 */
    private final AgentScopeCheckpoint checkpointSeed;
    private final AgentScopeCheckpointStore checkpointStore;
    /** AgentScope 的跨实例会话运行态；业务审批与执行终态不写入该存储。 */
    private final AgentStateStore agentStateStore;

    public SkillRuntimeCatalog getSkillCatalog() {
        return java.util.Objects.requireNonNullElseGet(skillCatalog, SkillRuntimeCatalog::empty);
    }

    public List<String> getAuthorizedToolCodes() {
        return List.copyOf(java.util.Objects.requireNonNullElseGet(authorizedToolCodes, List::of));
    }

    public ExecutionScope getScope() {
        return java.util.Objects.requireNonNullElse(scope, ExecutionScope.RUN);
    }
}
