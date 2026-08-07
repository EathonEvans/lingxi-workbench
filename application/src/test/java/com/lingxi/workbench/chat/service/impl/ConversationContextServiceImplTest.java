package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.impl.RuntimeVariablesInitializer;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.MemoryContextService;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationContextServiceImplTest {

    @Test
    void shouldPrepareSharedContextFromSingleMemorySnapshot() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        MemoryContextService memoryContextService = mock(MemoryContextService.class);
        QueryContextualizer queryContextualizer = mock(QueryContextualizer.class);
        TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
        when(tokenCountEstimator.estimate(any(String.class))).thenAnswer(invocation -> invocation.<String>getArgument(0).length());
        List<ChatMemoryMessageBO> loadedMessages = List.of(
                message(5, MessageRole.USER, "第三问"),
                message(6, MessageRole.ASSISTANT, "第三答"));
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 7);
        when(memoryService.loadContextMemory(eq(7L), eq(turn), any(ContextBudget.class)))
                .thenReturn(new ConversationMemorySnapshot("已有摘要", loadedMessages));
        MemoryConfig memoryConfig = MemoryConfig.builder()
                .profile(UserProfileMemoryConfig.builder().enabled(true).build())
                .summary(ConversationSummaryMemoryConfig.builder().enabled(true).build())
                .build();
        when(memoryContextService.build(7L, "继续处理", memoryConfig)).thenReturn("1234567890");
        when(queryContextualizer.contextualize(any(), eq("继续处理"), any(), eq(loadedMessages)))
                .thenReturn(new QueryContextualizer.ContextualizationResult("明确后的问题", TokenUsage.of(12, 3, 15)));
        ConversationContextServiceImpl service = new ConversationContextServiceImpl(memoryService, memoryContextService,
                new RuntimeVariablesInitializer("Lingxi Workbench"), queryContextualizer, tokenCountEstimator);
        ApplicationRuntimeSnapshot application = application(memoryConfig);
        LoginUser loginUser = new LoginUser(7L, "tester", "测试用户", null);

        ConversationContext context = service.prepare(application, "继续处理", loginUser, turn);

        assertEquals("已有摘要", context.getSummary());
        assertEquals(loadedMessages, context.getRecentMessages());
        assertEquals("1234567890", context.getLongTermMemory());
        assertEquals("明确后的问题", context.routingQuery());
        assertEquals(TokenUsage.of(12, 3, 15), context.getPreparationTokenUsage());
        assertEquals("继续处理", context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        verify(memoryService).loadContextMemory(eq(7L), eq(turn), any(ContextBudget.class));
        verify(memoryContextService).build(7L, "继续处理", memoryConfig);
        verify(queryContextualizer).contextualize(any(), eq("继续处理"), any(), eq(loadedMessages));
    }

    private static ChatMemoryMessageBO message(int sequenceNo, MessageRole role, String content) {
        return new ChatMemoryMessageBO(sequenceNo, role, content);
    }

    private static ApplicationRuntimeSnapshot application(MemoryConfig memoryConfig) {
        WorkflowDefinition workflow = new WorkflowDefinition(1, "legacy", List.of(), List.of());
        ApplicationDefinition definition = ApplicationDefinition.builder().schemaVersion(2).workflow(workflow).memory(memoryConfig).build();
        return new ApplicationRuntimeSnapshot(1L, "support", 2L, definition);
    }
}
