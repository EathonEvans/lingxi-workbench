package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationSummarySourceTest {

    @Test
    void shouldRejectCoverageThatDoesNotMatchLastCompleteRound() {
        List<ChatMemoryMessageBO> messages = List.of(
                new ChatMemoryMessageBO(5, MessageRole.USER, "问题"),
                new ChatMemoryMessageBO(6, MessageRole.ASSISTANT, "回答"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ConversationSummarySource("旧摘要", 3, 7, messages));

        assertEquals("摘要覆盖序号与最后完整问答不一致", error.getMessage());
    }

    @Test
    void shouldRejectCoverageThatDoesNotAdvance() {
        List<ChatMemoryMessageBO> messages = List.of(
                new ChatMemoryMessageBO(5, MessageRole.USER, "问题"),
                new ChatMemoryMessageBO(6, MessageRole.ASSISTANT, "回答"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ConversationSummarySource("旧摘要", 5, 5, messages));

        assertEquals("摘要覆盖序号必须向前推进", error.getMessage());
    }
}
