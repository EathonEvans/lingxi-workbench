package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.request.StreamChatRequest;
import com.lingxi.workbench.chat.model.bo.ChatStreamResult;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import reactor.core.publisher.Flux;

/**
 * 流式对话服务。
 */
public interface ChatStreamService {

    ChatStreamResult stream(StreamChatRequest request, Long userId);

    /**
     * 从指定增量序号之后重新订阅模型生成任务。
     */
    Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId);

    /**
     * 用户主动取消模型生成任务。
     */
    void cancel(Long assistantMessageId, Long userId);
}
