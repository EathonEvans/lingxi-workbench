package com.lingxi.workbench.chat.repo;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.repo.mapper.ConversationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 会话持久化测试。 */
@ExtendWith(MockitoExtension.class)
class ConversationRepoTest {

    @Mock
    private ConversationMapper mapper;

    @InjectMocks
    private ConversationRepo repo;

    @Test
    void shouldUseDatabaseIdentityAndFirstMessageForNewConversationIdempotency() {
        ConversationEntity persisted = new ConversationEntity();
        persisted.setId(100L);
        persisted.setUserId(7L);
        when(mapper.insertIfAbsent(org.mockito.ArgumentMatchers.any(ConversationEntity.class))).thenReturn(1);
        when(mapper.selectOwnedByInitialMessageIdForUpdate(7L, "message-001")).thenReturn(persisted);

        repo.createAndLockForFirstMessage(7L, "message-001", "新会话");

        ArgumentCaptor<ConversationEntity> entityCaptor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(mapper).insertIfAbsent(entityCaptor.capture());
        ConversationEntity entity = entityCaptor.getValue();
        assertNull(entity.getId());
        assertEquals(7L, entity.getUserId());
        assertEquals("message-001", entity.getInitialClientMessageId());
        assertEquals(ConversationStatus.ACTIVE, entity.getStatus());
    }
}
