package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.config.ChatMemoryProperties;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceImplTest {

    @Test
    void shouldKeepLatestCompleteRoundWithinTokenBudget() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ChatMemoryProperties properties = new ChatMemoryProperties();
        properties.setMaxRounds(10);
        properties.setMaxTokens(30);
        when(conversationRepo.findOwnedById(10L, 7L))
                .thenReturn(Optional.of(new ConversationEntity()));
        when(messageRepo.findRecentCompletedRounds(10L, 5, 10)).thenReturn(List.of(
                message(MessageRole.USER, "第一问"),
                message(MessageRole.ASSISTANT, "第一答"),
                message(MessageRole.USER, "第二问"),
                message(MessageRole.ASSISTANT, "第二答")
        ));
        when(estimator.estimate(anyString())).thenReturn(5);
        ConversationMemoryServiceImpl service = new ConversationMemoryServiceImpl(
                conversationRepo, messageRepo, properties, estimator);

        List<ChatMemoryMessageBO> result = service.getWindowMemory(7L, 10L, 5);

        assertEquals(List.of(
                new ChatMemoryMessageBO(MessageRole.USER, "第二问"),
                new ChatMemoryMessageBO(MessageRole.ASSISTANT, "第二答")
        ), result);
    }

    private static ConversationMessageEntity message(MessageRole role, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        return entity;
    }
}
