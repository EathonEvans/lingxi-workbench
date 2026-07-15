package com.lingxi.workbench.infra.ai.chat;

import reactor.core.publisher.Flux;

/**
 * 面向业务模块的流式对话能力。
 */
public interface StreamingChatGateway {

    /**
     * 流式生成模型回复。
     *
     * @param command 对话命令
     * @return 模型增量输出
     */
    Flux<ChatStreamChunk> stream(ChatStreamCommand command);
}
