package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationHistoryNodeTest {

    @Test
    void shouldPublishWindowHistoryToRuntimeVariables() {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_ID, 7L);
        variables.put(RuntimeVariableAccessors.SESSION_ID, 10L);
        AtomicBoolean memoryLoaded = new AtomicBoolean();
        ConversationMemoryService memoryService = new ConversationMemoryService() {
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
}
