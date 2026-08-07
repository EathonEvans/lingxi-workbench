package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.bo.ConversationSummaryTrigger;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationHistoryNodeTest {

    @Test
    void shouldPublishWindowHistoryToRuntimeVariables() {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.SESSION_ID, 10L);
        AtomicBoolean memoryLoaded = new AtomicBoolean();
        ConversationMemoryService memoryService = new ConversationMemoryService() {
            @Override
            public ConversationMemorySnapshot loadContextMemory(Long userId, ChatTurnBO turn, ContextBudget budget) {
                throw new AssertionError("旧节点兼容测试不准备共享上下文");
            }

            @Override
            public Optional<ConversationSummarySource> loadSummarySource(
                    Long userId, ChatTurnBO turn, ConversationSummaryTrigger trigger) {
                throw new AssertionError("旧节点兼容测试不执行会话摘要");
            }

            @Override
            public List<ChatMemoryMessageBO> getWindowMemory(
                    Long userId,
                    Long conversationId,
                    Integer beforeSequenceNo) {
                throw new AssertionError("测试使用Agent冻结的窗口参数");
            }

            @Override
            public List<ChatMemoryMessageBO> getWindowMemory(
                    Long userId,
                    Long conversationId,
                    Integer beforeSequenceNo,
                    int maxRounds,
                    int maxTokens) {
                assertEquals(7L, userId);
                assertEquals(10L, conversationId);
                assertEquals(3, beforeSequenceNo);
                assertEquals(6, maxRounds);
                assertEquals(2000, maxTokens);
                memoryLoaded.set(true);
                return List.of(
                        new ChatMemoryMessageBO(
                                MessageRole.USER,
                                "历史问题"),
                        new ChatMemoryMessageBO(
                                MessageRole.ASSISTANT,
                                "历史回答"));
            }

            @Override
            public List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages) {
                throw new AssertionError("兼容路径不应执行内存裁剪");
            }

            @Override
            public List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages, int maxRounds, int maxTokens) {
                throw new AssertionError("兼容路径不应执行内存裁剪");
            }
        };
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(variables)
                .memoryEnabled(true)
                .memoryMaxRounds(6)
                .memoryMaxTokens(2000)
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();

        new ConversationHistoryNode(memoryService).process(context);

        List<ChatHistoryMessage> history = variables.require(
                RuntimeVariableAccessors.SESSION_HISTORY);
        assertTrue(memoryLoaded.get());
        assertEquals(2, history.size());
        assertEquals(ChatHistoryMessage.Role.USER, history.get(0).role());
        assertEquals("历史回答", history.get(1).content());
        assertEquals(
                List.of(),
                variables.require(
                        RuntimeVariableAccessors.QUERY_REWRITE_HISTORY));
    }

    @Test
    void shouldLoadRewriteHistoryWhenAnswerMemoryIsDisabled() {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.SESSION_ID, 10L);
        ConversationMemoryService memoryService = new ConversationMemoryService() {
            @Override
            public ConversationMemorySnapshot loadContextMemory(Long userId, ChatTurnBO turn, ContextBudget budget) {
                throw new AssertionError("旧节点兼容测试不准备共享上下文");
            }

            @Override
            public Optional<ConversationSummarySource> loadSummarySource(
                    Long userId, ChatTurnBO turn, ConversationSummaryTrigger trigger) {
                throw new AssertionError("旧节点兼容测试不执行会话摘要");
            }

            @Override
            public List<ChatMemoryMessageBO> getWindowMemory(
                    Long userId,
                    Long conversationId,
                    Integer beforeSequenceNo) {
                throw new AssertionError("测试使用Agent冻结的改写窗口参数");
            }

            @Override
            public List<ChatMemoryMessageBO> getWindowMemory(
                    Long userId,
                    Long conversationId,
                    Integer beforeSequenceNo,
                    int maxRounds,
                    int maxTokens) {
                assertEquals(4, maxRounds);
                assertEquals(1000, maxTokens);
                return List.of(
                        new ChatMemoryMessageBO(
                                MessageRole.USER,
                                "iPhone 16 Pro 支持双卡吗"),
                        new ChatMemoryMessageBO(
                                MessageRole.ASSISTANT,
                                "支持"));
            }

            @Override
            public List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages) {
                throw new AssertionError("兼容路径不应执行内存裁剪");
            }

            @Override
            public List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages, int maxRounds, int maxTokens) {
                throw new AssertionError("兼容路径不应执行内存裁剪");
            }
        };
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(variables)
                .memoryEnabled(false)
                .queryRewriteConfig(QueryRewriteConfig.builder()
                        .enabled(true)
                        .maxHistoryRounds(4)
                        .maxHistoryTokens(1000)
                        .build())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();

        new ConversationHistoryNode(memoryService).process(context);

        assertEquals(
                List.of(),
                variables.require(RuntimeVariableAccessors.SESSION_HISTORY));
        assertEquals(
                2,
                variables.require(
                        RuntimeVariableAccessors.QUERY_REWRITE_HISTORY)
                        .size());
    }

    @Test
    void shouldSelectPreparedHistoryWithoutLoadingConversationAgain() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        List<ChatMemoryMessageBO> preparedMessages = List.of(
                new ChatMemoryMessageBO(1, MessageRole.USER, "历史问题"),
                new ChatMemoryMessageBO(2, MessageRole.ASSISTANT, "历史回答"));
        when(memoryService.selectWindow(preparedMessages, 1, 100)).thenReturn(preparedMessages);
        LoginUser loginUser = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 3);
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.SESSION_ID, 10L);
        variables.put(RuntimeVariableAccessors.USER_QUERY, "当前问题");
        WorkflowDefinition workflow = new WorkflowDefinition(1, "legacy", List.of(), List.of());
        ApplicationRuntimeSnapshot application = new ApplicationRuntimeSnapshot(1L, "support", 2L, new ApplicationDefinition(1, workflow));
        ConversationContext conversationContext = ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery("当前问题")
                .contextualizedQuery("当前问题")
                .recentMessages(preparedMessages)
                .summary("")
                .longTermMemory("")
                .budget(ContextBudget.builder().maxHistoryRounds(10).maxSystemPromptTokens(1000).maxCapabilityTokens(1000)
                        .maxHistoryTokens(1000).maxMemoryTokens(1000).maxInputTokens(1000).maxObservationTokens(1000)
                        .reservedOutputTokens(1000).build())
                .runtimeVariables(variables)
                .build();
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(variables)
                .conversationContext(conversationContext)
                .memoryEnabled(true)
                .memoryMaxRounds(1)
                .memoryMaxTokens(100)
                .queryRewriteConfig(QueryRewriteConfig.disabled())
                .turn(turn)
                .build();

        new ConversationHistoryNode(memoryService).process(context);

        assertEquals(2, variables.require(RuntimeVariableAccessors.SESSION_HISTORY).size());
        verify(memoryService).selectWindow(preparedMessages, 1, 100);
        verify(memoryService, never()).getWindowMemory(7L, 10L, 3, 1, 100);
    }
}
