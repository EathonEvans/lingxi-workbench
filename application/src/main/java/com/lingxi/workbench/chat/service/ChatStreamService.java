package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.request.StreamChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import reactor.core.publisher.Flux;

/**
 * 流式对话服务。
 */
public interface ChatStreamService {

    Flux<ChatChunkVO> stream(StreamChatRequest request);
}
