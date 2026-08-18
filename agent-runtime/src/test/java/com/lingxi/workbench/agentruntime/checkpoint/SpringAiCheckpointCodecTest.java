package com.lingxi.workbench.agentruntime.checkpoint;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SpringAiCheckpointCodecTest {

    @Test
    void shouldRoundTripSpringAiConversationAndToolCall() {
        SpringAiCheckpointCodec codec = new SpringAiCheckpointCodec();
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall(
                "call-1", "function", "search_docs", "{\"query\":\"退款\"}");
        List<Message> messages = List.of(
                new SystemMessage("系统"),
                new UserMessage("问题"),
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build(),
                ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse("call-1", "search_docs", "结果"))).build());

        List<Message> restored = codec.decodeMessages(codec.encodeMessages(messages));

        assertInstanceOf(SystemMessage.class, restored.get(0));
        assertInstanceOf(UserMessage.class, restored.get(1));
        AssistantMessage assistant = assertInstanceOf(AssistantMessage.class, restored.get(2));
        assertEquals(call, assistant.getToolCalls().getFirst());
        ToolResponseMessage tool = assertInstanceOf(ToolResponseMessage.class, restored.get(3));
        assertEquals("结果", tool.getResponses().getFirst().responseData());
    }
}
