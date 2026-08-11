package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.memory.config.MemoryChatModelResolver;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryCompletionServiceTest {

    private final PromptRuntimeService promptRuntimeService = mock(PromptRuntimeService.class);
    private final MemoryChatModelResolver modelResolver = mock(MemoryChatModelResolver.class);
    private final ChatCompletionGateway completionGateway = mock(ChatCompletionGateway.class);
    private final MemoryCompletionService service = new MemoryCompletionService(
            promptRuntimeService, modelResolver, completionGateway);

    @Test
    void shouldPreserveTemplateLikeTextFromConversationHistory() {
        preparePrompt("提取以下对话：\n{{conversation_history}}");
        when(completionGateway.complete(any())).thenReturn(Mono.just(new ChatCompletionResult<>("  {\"summary\":\"ok\"}  ", null)));

        String result = service.complete(
                11L,
                21L,
                Map.of("conversation_history", "user: 请解释 {{user.query}} 的用途"));

        assertEquals("{\"summary\":\"ok\"}", result);
        ArgumentCaptor<ChatCompletionCommand> commandCaptor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        verify(completionGateway).complete(commandCaptor.capture());
        assertEquals("提取以下对话：\nuser: 请解释 {{user.query}} 的用途", commandCaptor.getValue().prompt());
    }

    @Test
    void shouldRejectMissingTemplateVariableBeforeCallingModel() {
        preparePrompt("已有画像：{{existing_profile}}\n对话：{{conversation_history}}");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.complete(
                11L,
                21L,
                Map.of("conversation_history", "user: 你好")));

        assertEquals("记忆提取Prompt存在未解析变量：existing_profile", exception.getMessage());
        verify(completionGateway, never()).complete(any());
    }

    private void preparePrompt(String content) {
        when(promptRuntimeService.resolvePublished(11L))
                .thenReturn(new PromptRuntimeSnapshot(11L, content, List.of(), PromptRole.SYSTEM));
        when(modelResolver.resolve(21L))
                .thenReturn(ChatModelRuntimeConfig.builder().instanceId(21L).maxTokens(2048).build());
    }
}
