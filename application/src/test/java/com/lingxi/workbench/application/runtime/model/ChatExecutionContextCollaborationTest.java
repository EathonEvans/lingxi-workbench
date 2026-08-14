package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatExecutionContextCollaborationTest {

    @Test
    void shouldIsolatePlanStepTextAndShareTokenAccumulator() {
        AtomicInteger publishedTexts = new AtomicInteger();
        ChatExecutionContext parent = context(publishedTexts, 2);

        ChatExecutionContext child = parent.forkForPlanStep("核对退款规则", "run-1", 1, "step-1");
        child.getEventPublisher().publishText(new ChatStreamChunk("专家草稿"));
        child.recordUsage(TokenUsage.of(10, 5, 15));

        assertEquals(0, publishedTexts.get());
        assertEquals("核对退款规则", child.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        assertEquals(15, parent.tokenUsage().totalTokens());
    }

    @Test
    void shouldEnforceApplicationDelegationLimit() {
        ChatExecutionContext parent = context(new AtomicInteger(), 1);

        ChatExecutionContext child = parent.forkForQuery("子任务");
        parent.reserveDelegation();

        assertEquals(0, child.remainingDelegations());
        assertThrows(BusinessException.class, child::reserveDelegation);
    }

    @Test
    void shouldShareSoftTokenBudgetAndFinalizationGraceAcrossForks() {
        ChatExecutionContext parent = ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(new RuntimeVariables())
                .turn(new ChatTurnBO(8L, 9L, 10L, 1))
                .runtimePolicy(ApplicationRuntimePolicy.builder().totalTokenBudget(20).build())
                .build();
        ChatExecutionContext child = parent.forkForQuery("子任务");

        child.recordUsage(TokenUsage.of(8, 7, 15));

        assertEquals(5, parent.remainingTokenBudget());
        assertEquals(5, child.remainingTokenBudget());
        parent.recordUsage(TokenUsage.of(3, 3, 6));
        assertEquals(1, parent.remainingFinalizationTokenBudget());
        parent.recordUsage(TokenUsage.of(1, 1, 2));

        assertEquals(23, parent.tokenUsage().totalTokens());
        assertTrue(parent.shouldFinalize());
        assertFalse(parent.hasFinalizationCapacity());
        assertThrows(BusinessException.class, parent::remainingFinalizationTokenBudget);
    }

    @Test
    void shouldShareModelAndToolCallBudgetsAcrossForks() {
        ChatExecutionContext parent = ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(new RuntimeVariables())
                .turn(new ChatTurnBO(8L, 9L, 10L, 1))
                .runtimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2).maxToolCalls(3).totalTokenBudget(100).timeoutSeconds(60).build())
                .build();
        ChatExecutionContext child = parent.forkForQuery("子任务");

        parent.reserveModelCall();
        child.reserveFinalModelCall();
        child.reserveToolCalls(2);
        parent.reserveToolCalls(1);

        assertEquals(0, parent.remainingModelCalls());
        assertEquals(0, child.remainingToolCalls());
        assertThrows(BusinessException.class, parent::reserveModelCall);
        assertThrows(BusinessException.class, () -> child.reserveToolCalls(1));
    }

    private static ChatExecutionContext context(AtomicInteger publishedTexts, int maxDelegations) {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.USER_NAME, "测试用户");
        variables.put(RuntimeVariableAccessors.USER_QUERY, "原始问题");
        variables.put(RuntimeVariableAccessors.SESSION_ID, 8L);
        return ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(variables)
                .turn(new ChatTurnBO(8L, 9L, 10L, 1))
                .runtimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(4)
                        .maxToolCalls(4)
                        .maxDelegations(maxDelegations)
                        .totalTokenBudget(100)
                        .timeoutSeconds(60)
                        .build())
                .eventPublisher(new ChatExecutionEventPublisher() {
                    @Override
                    public void publishText(ChatStreamChunk chunk) {
                        publishedTexts.incrementAndGet();
                    }

                    @Override
                    public void publishTool(AgentToolExecutionEvent event) {
                    }
                })
                .build();
    }
}
