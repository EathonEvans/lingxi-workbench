package com.lingxi.workbench.chat.repo;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.repo.mapper.ConversationMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话消息持久化测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationMessageRepoTest {

    @Mock
    private ConversationMessageMapper mapper;

    @InjectMocks
    private ConversationMessageRepo repo;

    @Test
    void shouldPersistTokenUsageWhenAssistantMessageFinishes() {
        when(mapper.update(
                any(ConversationMessageEntity.class),
                ArgumentMatchers.<Wrapper<ConversationMessageEntity>>any()))
                .thenReturn(1);
        OffsetDateTime completedAt = OffsetDateTime.now();

        boolean updated = repo.finish(
                12L,
                MessageStatus.COMPLETED,
                "完整回答",
                null,
                null,
                TokenUsage.of(120, 30, 150),
                completedAt);

        ArgumentCaptor<ConversationMessageEntity> entityCaptor =
                ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(mapper).update(
                entityCaptor.capture(),
                ArgumentMatchers.<Wrapper<ConversationMessageEntity>>any());
        ConversationMessageEntity entity = entityCaptor.getValue();
        assertTrue(updated);
        assertEquals(120, entity.getInputTokens());
        assertEquals(30, entity.getOutputTokens());
        assertEquals(150, entity.getTotalTokens());
        assertEquals(completedAt, entity.getCompletedAt());
    }
}
