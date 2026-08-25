package com.lingxi.workbench.agentruntime.context;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentScopeConversationStateFactoryTest {

    @Test
    void shouldSeedOnlyPreparedMessagesWithoutEnablingDefaultSummaryModelCall() {
        ChatStreamCommand command = ChatStreamCommand.builder()
                .prompt("当前问题")
                .systemPrompt("已渲染的系统提示词")
                .history(List.of(
                        new ChatHistoryMessage(ChatHistoryMessage.Role.USER, "历史问题"),
                        new ChatHistoryMessage(ChatHistoryMessage.Role.ASSISTANT, "历史回答")))
                .modelConfig(ChatModelRuntimeConfig.builder().build())
                .build();

        AgentState state = AgentScopeConversationStateFactory.create(command, "7", "run-7");

        assertEquals("7", state.getUserId());
        assertEquals("run-7", state.getSessionId());
        assertEquals("", state.getSummary());
        assertEquals(List.of(MsgRole.USER, MsgRole.ASSISTANT, MsgRole.USER),
                state.getContext().stream().map(message -> message.getRole()).toList());
        assertEquals("当前问题", state.getContext().getLast().getTextContent());
    }
}
