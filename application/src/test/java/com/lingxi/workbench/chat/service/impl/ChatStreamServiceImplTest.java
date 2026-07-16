package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.model.request.StreamChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.infra.ai.chat.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatStreamServiceImplTest {

    @Test
    void shouldStartModelCallLazilyAndMapChunks() {
        StreamingChatGateway gateway = mock(StreamingChatGateway.class);
        ModelRuntimeConfigService configService = mock(ModelRuntimeConfigService.class);
        ChatModelRuntimeConfig config = new ChatModelRuntimeConfig(
                1L, "https://api.deepseek.com", "secret", "deepseek-chat",
                0.7, null, null, List.of(), 60, 1);
        when(configService.getDefaultChatModel()).thenReturn(config);
        when(gateway.stream(any())).thenReturn(Flux.just(
                new ChatStreamChunk("你"),
                new ChatStreamChunk("好")
        ));
        ChatStreamServiceImpl service = new ChatStreamServiceImpl(gateway, configService);

        Flux<ChatChunkVO> stream = service.stream(new StreamChatRequest("你好"));
        verifyNoInteractions(gateway, configService);

        assertEquals(List.of(new ChatChunkVO("你"), new ChatChunkVO("好")), stream.collectList().block());
    }
}
