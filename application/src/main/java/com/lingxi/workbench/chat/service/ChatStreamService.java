package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.request.StartChatRequest;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import reactor.core.publisher.Flux;

/**
 * 流式对话服务。
 */
public interface ChatStreamService {

    /**
     * 持久化当前问答轮次并启动独立于 SSE 连接的模型生成任务。
     *
     * @param request 用户输入、会话标识和客户端幂等消息标识
     * @param userId 当前登录用户主键
     * @return 已落库的会话、用户消息和 Assistant 消息主键
     */
    ChatStartVO start(StartChatRequest request, Long userId);

    /**
     * 从指定增量序号之后重新订阅模型生成任务。
     */
    Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId);

    /**
     * 用户主动取消模型生成任务。
     */
    void cancel(Long assistantMessageId, Long userId);
}
