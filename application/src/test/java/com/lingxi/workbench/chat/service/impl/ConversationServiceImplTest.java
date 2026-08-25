package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.converter.ConversationConverter;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationTurnStartBO;
import com.lingxi.workbench.chat.model.bo.ConversationCreateResult;
import com.lingxi.workbench.chat.model.bo.StartConversationTurnBO;
import com.lingxi.workbench.chat.ops.ConversationOpsMetrics;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepo conversationRepo;
    @Mock private ConversationMessageRepo messageRepo;
    @Mock private ConversationConverter converter;
    @Mock private ConversationOpsMetrics opsMetrics;

    @InjectMocks private ConversationServiceImpl service;

    @Test
    void shouldCreateUserAndGeneratingAssistantMessagesInSequence() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setTitle("新会话");
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setMessageCount(0);
        when(conversationRepo.lockOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));
        when(messageRepo.nextSequence(10L)).thenReturn(3);
        doAnswer(invocation -> {
            ConversationMessageEntity entity = invocation.getArgument(0);
            entity.setId(entity.getRole() == MessageRole.USER ? 11L : 12L);
            return null;
        }).when(messageRepo).save(org.mockito.ArgumentMatchers.any());

        ConversationTurnStartBO result = service.startTurn(StartConversationTurnBO.builder()
                .userId(7L)
                .conversationId("10")
                .clientMessageId("client-001")
                .prompt("  这是一个用于生成会话标题的测试问题  ")
                .modelInstanceId(20L)
                .applicationId(21L)
                .applicationVersionId(30L)
                .build());

        ArgumentCaptor<ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(messageRepo, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ConversationMessageEntity userMessage = messageCaptor.getAllValues().get(0);
        ConversationMessageEntity assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals(MessageRole.USER, userMessage.getRole());
        assertEquals(MessageStatus.COMPLETED, userMessage.getStatus());
        assertEquals(3, userMessage.getSequenceNo());
        assertEquals(MessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(MessageStatus.GENERATING, assistantMessage.getStatus());
        assertEquals(4, assistantMessage.getSequenceNo());
        assertEquals(20L, assistantMessage.getModelInstanceId());
        assertEquals(21L, assistantMessage.getApplicationId());
        assertEquals(30L, assistantMessage.getApplicationVersionId());
        assertEquals(2, conversation.getMessageCount());
        assertEquals("这是一个用于生成会话标题的测试问题", conversation.getTitle());
        assertEquals(12L, result.turn().assistantMessageId());
        assertEquals(3, result.turn().userSequenceNo());
        assertEquals(true, result.isCreated());
    }

    @Test
    void shouldPersistPartialAnswerWhenModelFails() {
        TokenUsage tokenUsage = TokenUsage.of(20, 8, 28);

        service.failAssistant(
                12L, "部分回答", "AI_MODEL_ERROR", "模型服务调用失败", tokenUsage);

        verify(messageRepo).finish(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(MessageStatus.FAILED),
                org.mockito.ArgumentMatchers.eq("部分回答"),
                org.mockito.ArgumentMatchers.eq("AI_MODEL_ERROR"),
                org.mockito.ArgumentMatchers.eq("模型服务调用失败"),
                org.mockito.ArgumentMatchers.eq(tokenUsage),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldConfirmCancellationOnlyWhenDatabaseHasCancelledTerminal() {
        ConversationMessageEntity cancelled = new ConversationMessageEntity();
        cancelled.setStatus(MessageStatus.CANCELLED);
        when(messageRepo.findById(12L)).thenReturn(Optional.of(cancelled));

        boolean result = service.cancelAssistant(12L, "部分回答", TokenUsage.empty());

        assertTrue(result);
        verify(messageRepo).finish(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(MessageStatus.CANCELLED),
                org.mockito.ArgumentMatchers.eq("部分回答"),
                org.mockito.ArgumentMatchers.eq("CLIENT_CANCELLED"),
                org.mockito.ArgumentMatchers.eq("客户端中断了本次回答"),
                org.mockito.ArgumentMatchers.eq(TokenUsage.empty()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectCancellationSignalWhenAnotherTerminalAlreadyWon() {
        ConversationMessageEntity completed = new ConversationMessageEntity();
        completed.setStatus(MessageStatus.COMPLETED);
        when(messageRepo.findById(12L)).thenReturn(Optional.of(completed));

        boolean result = service.cancelAssistant(12L, "部分回答", TokenUsage.empty());

        assertFalse(result);
    }

    @Test
    void shouldCreateConversationOnlyWhenFirstMessageIsSent() {
        ConversationEntity conversation = activeConversation();
        when(conversationRepo.createAndLockForFirstMessage(7L, "message-local-001", "新会话"))
                .thenReturn(new ConversationCreateResult(conversation, true));
        when(messageRepo.nextSequence(10L)).thenReturn(1);
        doAnswer(invocation -> {
            ConversationMessageEntity entity = invocation.getArgument(0);
            entity.setId(entity.getRole() == MessageRole.USER ? 11L : 12L);
            return null;
        }).when(messageRepo).save(any());

        ConversationTurnStartBO result = service.startTurn(StartConversationTurnBO.builder()
                .userId(7L)
                .clientMessageId("message-local-001")
                .prompt("首次发送")
                .applicationId(21L)
                .applicationVersionId(30L)
                .build());

        verify(conversationRepo).createAndLockForFirstMessage(7L, "message-local-001", "新会话");
        assertEquals(10L, result.turn().conversationId());
        assertEquals(true, result.isCreated());
    }

    @Test
    void shouldReturnExistingTurnWithoutCreatingMessagesForSameClientMessage() {
        ConversationEntity conversation = activeConversation();
        ConversationMessageEntity userMessage = new ConversationMessageEntity();
        userMessage.setId(11L);
        userMessage.setSequenceNo(1);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent("重复发送");
        ConversationMessageEntity assistantMessage = new ConversationMessageEntity();
        assistantMessage.setId(12L);
        assistantMessage.setSequenceNo(2);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setApplicationId(21L);
        assistantMessage.setApplicationVersionId(30L);
        when(conversationRepo.lockOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));
        when(messageRepo.findUserMessage(10L, "message-local-001")).thenReturn(Optional.of(userMessage));
        when(messageRepo.findBySequence(10L, 2)).thenReturn(Optional.of(assistantMessage));

        ConversationTurnStartBO result = service.startTurn(StartConversationTurnBO.builder()
                .userId(7L)
                .conversationId("10")
                .clientMessageId("message-local-001")
                .prompt("重复发送")
                .applicationId(21L)
                .applicationVersionId(30L)
                .build());

        assertFalse(result.isCreated());
        assertEquals(new ChatTurnBO(10L, 11L, 12L, 1), result.turn());
        verify(messageRepo, never()).save(any());
    }

    @Test
    void shouldReuseOriginalApplicationVersionWhenPublishedVersionChangesBeforeRetry() {
        ConversationEntity conversation = activeConversation();
        ConversationMessageEntity userMessage = new ConversationMessageEntity();
        userMessage.setId(11L);
        userMessage.setSequenceNo(1);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent("重复发送");
        ConversationMessageEntity assistantMessage = new ConversationMessageEntity();
        assistantMessage.setId(12L);
        assistantMessage.setSequenceNo(2);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setApplicationId(21L);
        assistantMessage.setApplicationVersionId(30L);
        when(conversationRepo.lockOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));
        when(messageRepo.findUserMessage(10L, "message-local-001")).thenReturn(Optional.of(userMessage));
        when(messageRepo.findBySequence(10L, 2)).thenReturn(Optional.of(assistantMessage));

        ConversationTurnStartBO result = service.startTurn(StartConversationTurnBO.builder()
                .userId(7L)
                .conversationId("10")
                .clientMessageId("message-local-001")
                .prompt("重复发送")
                .applicationId(21L)
                .applicationVersionId(31L)
                .build());

        assertFalse(result.isCreated());
        assertEquals(new ChatTurnBO(10L, 11L, 12L, 1), result.turn());
        verify(messageRepo, never()).save(any());
    }

    @Test
    void shouldReuseInitialConversationWhenFirstResponseWasLost() {
        ConversationEntity conversation = activeConversation();
        ConversationMessageEntity userMessage = new ConversationMessageEntity();
        userMessage.setId(11L);
        userMessage.setSequenceNo(1);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent("首次发送");
        ConversationMessageEntity assistantMessage = new ConversationMessageEntity();
        assistantMessage.setId(12L);
        assistantMessage.setSequenceNo(2);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setApplicationId(21L);
        assistantMessage.setApplicationVersionId(30L);
        when(conversationRepo.createAndLockForFirstMessage(7L, "message-local-001", "新会话"))
                .thenReturn(new ConversationCreateResult(conversation, false));
        when(messageRepo.findUserMessage(10L, "message-local-001")).thenReturn(Optional.of(userMessage));
        when(messageRepo.findBySequence(10L, 2)).thenReturn(Optional.of(assistantMessage));

        ConversationTurnStartBO result = service.startTurn(StartConversationTurnBO.builder()
                .userId(7L)
                .clientMessageId("message-local-001")
                .prompt("首次发送")
                .applicationId(21L)
                .applicationVersionId(30L)
                .build());

        assertFalse(result.isCreated());
        assertEquals(new ChatTurnBO(10L, 11L, 12L, 1), result.turn());
        verify(opsMetrics, times(1)).recordLazyCreateConflict();
        verify(messageRepo, never()).save(any());
    }

    private static ConversationEntity activeConversation() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(10L);
        conversation.setUserId(7L);
        conversation.setTitle("新会话");
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setMessageCount(0);
        return conversation;
    }
}
