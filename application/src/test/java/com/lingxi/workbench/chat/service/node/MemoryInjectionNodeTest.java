package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.chat.memory.MemoryContextService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MemoryInjectionNodeTest {

    @Test
    void shouldInjectPreparedMemoryWithoutLoadingItAgain() {
        MemoryContextService memoryContextService = mock(MemoryContextService.class);
        RuntimeVariables runtimeVariables = new RuntimeVariables();
        LoginUser loginUser = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 3);
        WorkflowDefinition legacyWorkflow = new WorkflowDefinition(1, "legacy", List.of(), List.of());
        ApplicationRuntimeSnapshot application = new ApplicationRuntimeSnapshot(1L, "support", 2L, ApplicationDefinition.builder().schemaVersion(2).workflow(legacyWorkflow).build());
        ConversationContext conversationContext = ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery("继续处理")
                .contextualizedQuery("继续处理")
                .recentMessages(List.of())
                .summary("已有摘要")
                .longTermMemory("长期记忆")
                .budget(ContextBudget.builder().maxHistoryRounds(10).maxSystemPromptTokens(1000).maxCapabilityTokens(1000)
                        .maxHistoryTokens(1000).maxMemoryTokens(1000).maxInputTokens(1000).maxObservationTokens(1000)
                        .reservedOutputTokens(1000).build())
                .runtimeVariables(runtimeVariables)
                .build();
        ModelChatContext modelContext = ModelChatContext.builder()
                .runtimeVariables(runtimeVariables)
                .conversationContext(conversationContext)
                .memoryConfig(MemoryConfig.builder()
                        .profile(UserProfileMemoryConfig.builder().enabled(true).build())
                        .build())
                .build();

        new MemoryInjectionNode(memoryContextService).process(modelContext);

        assertEquals("已有摘要", runtimeVariables.require(RuntimeVariableAccessors.SESSION_SUMMARY));
        assertEquals("长期记忆", runtimeVariables.require(RuntimeVariableAccessors.LONG_TERM_MEMORY));
        verifyNoInteractions(memoryContextService);
    }
}
