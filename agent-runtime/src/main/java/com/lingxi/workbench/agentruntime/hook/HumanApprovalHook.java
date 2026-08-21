package com.lingxi.workbench.agentruntime.hook;

import com.lingxi.workbench.agentruntime.checkpoint.HumanApprovalRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointContext;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec;
import com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 在 Spring AI 执行原始 ToolCall 前创建持久检查点并中断 Runtime。
 */
@Component
public class HumanApprovalHook {

    private final SpringAiCheckpointCodec checkpointCodec;
    private final ToolApprovalFingerprint fingerprint;

    public HumanApprovalHook(SpringAiCheckpointCodec checkpointCodec, ToolApprovalFingerprint fingerprint) {
        this.checkpointCodec = checkpointCodec;
        this.fingerprint = fingerprint;
    }

    public Optional<RuntimeInterruption> beforeTool(Prompt prompt, ChatResponse response, ExecutionState state) {
        List<AssistantMessage.ToolCall> calls = Objects.requireNonNull(response.getResult(),
                "Tool响应结果不能为空").getOutput().getToolCalls();
        if (calls.size() != 1) {
            return Optional.empty();
        }
        AssistantMessage.ToolCall call = calls.getFirst();
        AgentToolDefinition definition = state.requireToolDefinition(call.name());
        if (!definition.getSecurityPolicy().isApprovalRequired()) {
            return Optional.empty();
        }
        RuntimeCheckpointContext context = Objects.requireNonNull(state.checkpointContext(),
                "需要人工审批的Agent调用必须提供Checkpoint上下文");
        RuntimeCheckpointStore store = Objects.requireNonNull(state.checkpointStore(),
                "需要人工审批的Agent调用必须提供Checkpoint存储器");
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(definition.getSecurityPolicy().getApprovalTtlSeconds());
        String configHash = fingerprint.configHash(definition);
        String approvalFingerprint = fingerprint.approval(state.getRunId(), state.getAgentVersionId(),
                definition.getCode(), configHash, call.arguments(),
                definition.getSecurityPolicy().getResourceScope(), expiresAt);
        RuntimeCheckpoint checkpoint = RuntimeCheckpoint.builder()
                .version(RuntimeCheckpoint.CURRENT_VERSION)
                .context(context)
                .state(state.snapshot())
                .messages(checkpointCodec.encodeMessages(prompt.getInstructions()))
                .pendingToolCall(checkpointCodec.encodeToolCall(call))
                .toolConfigurationHash(configHash)
                .resourceScope(definition.getSecurityPolicy().getResourceScope())
                .fingerprint(approvalFingerprint)
                .expiresAt(expiresAt)
                .build();
        RuntimeInterruption interruption = store.interrupt(new HumanApprovalRequest(
                checkpoint, definition, state.toolRuntimeConfig().getExecutionContext()));
        state.interrupt(interruption);
        return Optional.of(interruption);
    }

    /**
     * 恢复前重新绑定当前冻结Tool定义，任何配置或资源范围漂移都会拒绝旧审批。
     */
    public void validateResume(RuntimeCheckpoint checkpoint, ExecutionState state) {
        RuntimeCheckpoint.ToolCallSnapshot call = Objects.requireNonNull(checkpoint.getPendingToolCall(),
                "Checkpoint待执行ToolCall不能为空");
        AgentToolDefinition definition = state.requireToolDefinition(call.getName());
        String configHash = fingerprint.configHash(definition);
        String currentFingerprint = fingerprint.approval(state.getRunId(), state.getAgentVersionId(),
                definition.getCode(), configHash, call.getArguments(),
                definition.getSecurityPolicy().getResourceScope(), checkpoint.getExpiresAt());
        if (!definition.getSecurityPolicy().isApprovalRequired()
                || !Objects.equals(configHash, checkpoint.getToolConfigurationHash())
                || !Objects.equals(definition.getSecurityPolicy().getResourceScope(), checkpoint.getResourceScope())
                || !Objects.equals(currentFingerprint, checkpoint.getFingerprint())) {
            throw new IllegalArgumentException("Checkpoint Tool配置、资源范围或参数已经漂移");
        }
    }
}
