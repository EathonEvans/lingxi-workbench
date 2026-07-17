package com.lingxi.workbench.chat.model.converter;

import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationConverterTest {

    private final ConversationConverter converter =
            Mappers.getMapper(ConversationConverter.class);

    @Test
    void shouldConvertLargeMessageIdsToStrings() {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(2077574496283791361L);
        entity.setConversationId(2077574496283791362L);
        entity.setModelInstanceId(2077574496283791363L);

        ConversationMessageVO result = converter.toMessageVO(entity);

        assertEquals("2077574496283791361", result.id());
        assertEquals("2077574496283791362", result.conversationId());
        assertEquals("2077574496283791363", result.modelInstanceId());
    }
}
