package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.MemoryContextService;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.bo.ConversationContextPrepareBO;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class ConversationContextServiceImplTest {

    @Test
    void shouldPrepareSharedContextFromSingleMemorySnapshot() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        MemoryContextService memoryContextService = mock(MemoryContextService.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
        when(tokenCountEstimator.estimate(any(String.class))).thenAnswer(invocation -> invocation.<String>getArgument(0).length());
        List<ChatMemoryMessageBO> loadedMessages = List.of(
                message(5, MessageRole.USER, "第三问"),
                message(6, MessageRole.ASSISTANT, "第三答"));
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 7);
        when(memoryService.loadContextMemory(eq(7L), eq(turn), any(ContextBudgetPolicy.class)))
                .thenReturn(new ConversationMemorySnapshot("已有摘要", loadedMessages));
        MemoryConfig memoryConfig = MemoryConfig.builder()
                .profile(UserProfileMemoryConfig.builder().enabled(true).build())
                .summary(ConversationSummaryMemoryConfig.builder().enabled(true).build())
                .build();
        when(memoryContextService.build(7L, "继续处理", memoryConfig)).thenReturn("1234567890");
        when(queryRewriter.rewrite(any(), eq("继续处理"), any(), eq(loadedMessages)))
                .thenReturn(new QueryRewriter.RewriteResult(
                        "明确后的问题", TokenUsage.of(12, 3, 15), 1));
        ConversationContextServiceImpl service = new ConversationContextServiceImpl(memoryService, memoryContextService,
                new RuntimeVariablesInitializer("Lingxi Workbench"), queryRewriter, tokenCountEstimator);
        AppRuntimeSnapshot application = application(memoryConfig);
        LoginUser loginUser = new LoginUser(7L, "tester", "测试用户", null);

        ConversationContext context = service.prepare(prepareInput(application, "继续处理", loginUser, turn));

        assertEquals("已有摘要", context.getSummary());
        assertEquals(loadedMessages, context.getRecentMessages());
        assertEquals("1234567890", context.getLongTermMemory());
        assertEquals("明确后的问题", context.routingQuery());
        assertEquals(TokenUsage.of(12, 3, 15), context.getPreparationTokenUsage());
        assertEquals(1, context.getPreparationModelCalls());
        assertEquals("继续处理", context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY));
        verify(memoryService).loadContextMemory(eq(7L), eq(turn), any(ContextBudgetPolicy.class));
        verify(memoryContextService).build(7L, "继续处理", memoryConfig);
        verify(queryRewriter).rewrite(any(), eq("继续处理"), any(), eq(loadedMessages));
    }

    @Test
    void shouldPreserveContextUsageBeyondHardBudgetForBoundaryFallback() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        when(memoryService.getWindowMemory(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        when(queryRewriter.rewrite(any(), any(), any(), any())).thenReturn(
                new QueryRewriter.RewriteResult("问题", TokenUsage.of(8, 4, 12), 1));
        ConversationContextServiceImpl service = new ConversationContextServiceImpl(
                memoryService, mock(MemoryContextService.class), new RuntimeVariablesInitializer("Lingxi Workbench"),
                queryRewriter, mock(TokenCountEstimator.class));
        AppRuntimeSnapshot application = application(MemoryConfig.disabled(), ApplicationRuntimePolicy.builder()
                .maxModelCalls(2).maxToolCalls(0).maxDelegations(0).totalTokenBudget(10).build());
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);

        ConversationContext context = service.prepare(
                prepareInput(application, "问题", new LoginUser(7L, "tester", "测试用户", null), turn));

        assertEquals("问题", context.routingQuery());
        assertEquals(TokenUsage.of(8, 4, 12), context.getPreparationTokenUsage());
    }

    private static ConversationContextPrepareBO prepareInput(AppRuntimeSnapshot application, String query,
                                                             LoginUser loginUser, ChatTurnBO turn) {
        return ConversationContextPrepareBO.builder()
                .application(application)
                .query(query)
                .loginUser(loginUser)
                .turn(turn)
                .build();
    }

    private static ChatMemoryMessageBO message(int sequenceNo, MessageRole role, String content) {
        return new ChatMemoryMessageBO(sequenceNo, role, content);
    }

    private static AppRuntimeSnapshot application(MemoryConfig memoryConfig) {
        return application(memoryConfig, ApplicationRuntimePolicy.defaults());
    }

    private static AppRuntimeSnapshot application(MemoryConfig memoryConfig, ApplicationRuntimePolicy runtimePolicy) {
        CapabilityDefinition capability = CapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(40L)
                .targetVersionId(41L)
                .build();
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(40L, 41L, "默认助手", "负责收口"),
                        List.of(), List.of(), List.of()))
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                .memoryConf(memoryConfig)
                .contextPolicy(ApplicationContextPolicy.defaults())
                .runtimePolicy(runtimePolicy)
                .build();
        return new AppRuntimeSnapshot(1L, "support", 2L, definition);
    }
}
