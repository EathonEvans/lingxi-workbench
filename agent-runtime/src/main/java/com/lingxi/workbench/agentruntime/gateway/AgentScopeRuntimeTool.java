package com.lingxi.workbench.agentruntime.gateway;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import org.springframework.ai.chat.model.ToolContext;
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
    private final ExecutionState state;

    AgentScopeRuntimeTool(AgentTool delegate, AgentToolExecutionContext executionContext,
                          int maxResultCharacters, ExecutionState state) {
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
        this.state = state;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) {
        AgentToolDefinition definition = delegate.definition();
        String invocationId = UUID.randomUUID().toString();
        AgentScopeApprovedInvocation approved = approvedInvocation(param.getRuntimeContext());
        String input = JSON.toJSONString(new LinkedHashMap<>(param.getInput()));
        validateApprovedInvocation(approved, param, definition);
        boolean started = false;
        long startedAt = 0L;
        try {
            state.beforeToolCall(definition);
            startedAt = System.nanoTime();
            state.toolStarted(invocationId, definition);
            started = true;
            String result = delegate.callback().call(input, new ToolContext(toolContext(approved)));
            if (result.length() > maxResultCharacters) {
                throw new AgentToolException("工具返回结果超过字符上限：" + definition.getCode()
                        + "，limit=" + maxResultCharacters);
            }
            if (approved != null) {
                state.checkpointStore().complete(approved.invocationId(), result);
            }
            state.toolCompleted(invocationId, definition, elapsedMillis(startedAt));
            return ToolResultBlock.text(result).withIdAndName(param.getToolUseBlock().getId(), definition.getCode());
        } catch (RuntimeException exception) {
            if (approved != null) {
                try {
                    state.checkpointStore().fail(approved.invocationId(), exception);
                } catch (RuntimeException reconciliationException) {
                    state.reconciliationPending(approved.invocationId(), reconciliationException);
                    throw new AgentReconciliationPendingException(reconciliationException);
                }
            }
            if (started) {
                state.toolFailed(invocationId, definition, elapsedMillis(startedAt), TOOL_EXECUTION_FAILED_CODE);
            }
            throw exception;
        }
    }

    private Map<String, Object> toolContext(AgentScopeApprovedInvocation approved) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("executionId", executionContext.getExecutionId());
        context.put("userId", executionContext.getUserId());
        context.put("conversationId", executionContext.getConversationId());
        context.put("assistantMessageId", executionContext.getAssistantMessageId());
        if (approved != null) {
            context.put("idempotencyKey", approved.idempotencyKey());
        }
        return context;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inputSchema(AgentTool delegate) {
        Object parsed = JSON.parse(delegate.callback().getToolDefinition().inputSchema());
        if (!(parsed instanceof Map<?, ?> schema)) {
            throw new IllegalArgumentException("Tool输入Schema必须是JSON对象：" + delegate.definition().getCode());
        }
        return (Map<String, Object>) schema;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
