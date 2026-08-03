package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationLimits;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatExecutionContextCollaborationTest {

    @Test
    void shouldIsolateChildTextAndShareTokenAccumulator() {
        AtomicInteger publishedTexts = new AtomicInteger();
        ChatExecutionContext parent = context(publishedTexts, 2);
        ApplicationAgentReference member = member();

        ChatExecutionContext child = parent.forkForDelegation(member, "核对退款规则");
        child.getEventPublisher().publishText(new ChatStreamChunk("专家草稿"));
        child.recordUsage(TokenUsage.of(10, 5, 15));

        assertEquals(0, publishedTexts.get());
        assertEquals("核对退款规则", child.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        assertEquals(15, parent.tokenUsage().totalTokens());
    }

    @Test
    void shouldEnforceApplicationDelegationLimit() {
        ChatExecutionContext parent = context(new AtomicInteger(), 1);

        parent.forkForDelegation(member(), "第一次委派");

        assertThrows(BusinessException.class, () -> parent.forkForDelegation(member(), "第二次委派"));
    }

    private static ChatExecutionContext context(AtomicInteger publishedTexts, int maxDelegations) {
        ApplicationAgentReference supervisor = new ApplicationAgentReference(10L, 20L, "协调Agent", "负责规划");
        ApplicationAgentReference member = member();
        AgentCollaborationDefinition definition = new AgentCollaborationDefinition(true, supervisor, List.of(member), new AgentCollaborationLimits(maxDelegations, 1));
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.USER_NAME, "测试用户");
        variables.put(RuntimeVariableAccessors.USER_QUERY, "原始问题");
        variables.put(RuntimeVariableAccessors.SESSION_ID, 8L);
        return ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(variables)
                .turn(new ChatTurnBO(8L, 9L, 10L, 1))
                .eventPublisher(new ChatExecutionEventPublisher() {
                    @Override
                    public void publishText(ChatStreamChunk chunk) {
                        publishedTexts.incrementAndGet();
                    }

                    @Override
                    public void publishTool(AgentToolExecutionEvent event) {
                    }
                })
                .collaborationScope(AgentCollaborationScope.root(definition))
                .build();
    }

    private static ApplicationAgentReference member() {
        return new ApplicationAgentReference(30L, 40L, "售后专家", "核对售后规则");
    }
}
