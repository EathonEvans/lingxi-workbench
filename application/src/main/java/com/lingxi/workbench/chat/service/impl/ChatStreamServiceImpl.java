package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.model.request.StreamChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.infra.ai.chat.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 流式对话服务实现。
 */
@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamServiceImpl.class);

    private final StreamingChatGateway streamingChatGateway;
    private final ModelRuntimeConfigService modelRuntimeConfigService;

    @Override
    public Flux<ChatChunkVO> stream(StreamChatRequest request) {
        return Flux.defer(() -> streamingChatGateway.stream(new ChatStreamCommand(
                        request.prompt(), modelRuntimeConfigService.getDefaultChatModel())))
                .map(chunk -> new ChatChunkVO(chunk.content()))
                .doOnError(exception -> LOGGER.error("模型流式对话调用失败", exception));
    }
}
