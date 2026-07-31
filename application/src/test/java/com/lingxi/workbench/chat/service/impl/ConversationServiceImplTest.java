package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.converter.ConversationConverter;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepo conversationRepo;
    @Mock private ConversationMessageRepo messageRepo;
    @Mock private ConversationConverter converter;

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

        ChatTurnBO result = service.startTurn(
                7L, 10L, "client-001", "  这是一个用于生成会话标题的测试问题  ", 20L);

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
        assertEquals(2, conversation.getMessageCount());
        assertEquals("这是一个用于生成会话标题的测试问题", conversation.getTitle());
        assertEquals(12L, result.assistantMessageId());
        assertEquals(3, result.userSequenceNo());
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
                org.mockito.ArgumentMatchers.any());
    }
}
