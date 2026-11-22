package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.lingxi.workbench.infra.ai.tool.common.constants.AgentToolErrorConstants.TOOL_EXECUTION_FAILED_CODE;

/**
 * 将 Application 锁定的 Tool 执行器接入 AgentScope Toolkit 与 Permission Engine。
 */
final class AgentScopeRuntimeTool extends ToolBase {

    private final AgentTool delegate;
    private final AgentToolExecutionContext executionContext;
    private final int maxResultCharacters;
    private final AgentScopeCheckpointStore checkpointStore;

    AgentScopeRuntimeTool(AgentTool delegate, AgentToolExecutionContext executionContext,
                          int maxResultCharacters, AgentScopeCheckpointStore checkpointStore) {
        super(ToolBase.builder()
                .name(delegate.definition().getCode())
                .description(delegate.definition().getDescription())
                .inputSchema(inputSchema(delegate))
                .readOnly(delegate.definition().getExecutionMode().canReuseResult())
                // 有副作用 Tool 必须串行，Toolkit 也以 parallel=false 装配。
                .concurrencySafe(delegate.definition().getExecutionMode().canReuseResult()));
        this.delegate = delegate;
        this.executionContext = executionContext;
        this.maxResultCharacters = maxResultCharacters;
        this.checkpointStore = checkpointStore;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        AgentToolDefinition definition = delegate.definition();
        String invocationId = UUID.randomUUID().toString();
        AgentScopeApprovedInvocation approved = approvedInvocation(param.getRuntimeContext());
        ExecutionBudget budget = require(param.getRuntimeContext(), ExecutionBudget.class);
        ExecutionTools tools = require(param.getRuntimeContext(), ExecutionTools.class);
        ExecutionEvents events = require(param.getRuntimeContext(), ExecutionEvents.class);
        String input = JSON.toJSONString(new LinkedHashMap<>(param.getInput()));
        validateApprovedInvocation(approved, param, definition);
        boolean started = false;
        long startedAt = 0L;
        try {
            if (events.isCancelled()) {
                throw new com.lingxi.workbench.agentruntime.model.AgentRunCancelledException();
            }
            boolean wasFinalizing = budget.isFinalizationRequested();
            try {
                tools.reserve(definition, budget);
            } catch (com.lingxi.workbench.agentruntime.model.AgentRuntimeLimitException exception) {
                if (!wasFinalizing && budget.isFinalizationRequested()) {
                    events.finalizing(budget.finalizationReason());
                }
                throw exception;
            }
            startedAt = System.nanoTime();
            events.toolStarted(invocationId, definition);
            started = true;
            String result = delegate.execute(new AgentToolRequest(
                    input, executionContext, approved == null ? null : approved.idempotencyKey()));
            if (result.length() > maxResultCharacters) {
                throw new AgentToolException("工具返回结果超过字符上限：" + definition.getCode()
                        + "，limit=" + maxResultCharacters);
            }
            if (approved != null) {
                requireCheckpointStore().complete(approved.invocationId(), result);
            }
            events.toolCompleted(invocationId, definition, elapsedMillis(startedAt));
            return ToolResultBlock.text(result).withIdAndName(param.getToolUseBlock().getId(), definition.getCode());
        } catch (RuntimeException exception) {
            if (approved != null) {
                try {
                    requireCheckpointStore().fail(approved.invocationId(), exception);
                } catch (RuntimeException reconciliationException) {
                    requireCheckpointStore().markReconciliationPending(approved.invocationId(),
                            reconciliationException);
                    events.reconciliationPending();
                    throw new AgentReconciliationPendingException(reconciliationException);
                }
            }
            if (started) {
                events.toolFailed(invocationId, definition, elapsedMillis(startedAt), TOOL_EXECUTION_FAILED_CODE);
            }
            throw exception;
        }
    }

    private AgentScopeCheckpointStore requireCheckpointStore() {
        return Objects.requireNonNull(checkpointStore, "Checkpoint存储器不能为空");
    }

    private static <T> T require(RuntimeContext context, Class<T> type) {
        T value = context == null ? null : context.get(type);
        if (value == null) {
            throw new IllegalStateException("AgentScope调用缺少可信" + type.getSimpleName());
        }
        return value;
    }

    private static AgentScopeApprovedInvocation approvedInvocation(RuntimeContext context) {
        return context == null ? null : context.get(AgentScopeApprovedInvocation.class);
    }

    private static void validateApprovedInvocation(AgentScopeApprovedInvocation approved, ToolCallParam param,
                                                   AgentToolDefinition definition) {
        if (approved == null) {
            return;
        }
        if (!Objects.equals(approved.toolCall().id(), param.getToolUseBlock().getId())
                || !Objects.equals(approved.toolCall().name(), definition.getCode())
                || !Objects.equals(approved.toolCall().input(), param.getInput())) {
            throw new IllegalStateException("恢复的AgentScope Tool调用与已批准参数不一致");
        }
    }

    private static Map<String, Object> inputSchema(AgentTool delegate) {
        Map<String, Object> schema = delegate.inputSchema();
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("Tool输入Schema必须是JSON对象：" + delegate.definition().getCode());
        }
        return schema;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
