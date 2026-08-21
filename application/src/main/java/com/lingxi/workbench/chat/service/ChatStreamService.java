package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.framework.context.LoginUser;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

/**
 * 流式对话服务。
 */
public interface ChatStreamService {

    /**
     * 持久化当前问答轮次并启动独立于 SSE 连接的模型生成任务。
     *
     * @param request   用户输入、会话标识和客户端幂等消息标识
     * @param loginUser 请求开始时冻结的登录用户快照
     * @return 已落库的会话、用户消息和 Assistant 消息主键
     */
    ChatStartVO start(ChatRequest request, LoginUser loginUser);

    /**
     * 从指定增量序号之后重新订阅模型生成任务。
     */
    Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId);

    /**
     * 用户主动取消模型生成任务。
     */
    void cancel(Long assistantMessageId, Long userId);

    /**
     * RocketMQ 消费者根据已提交的人工作业恢复同一 Assistant 与执行 Run。
     */
    void resumeInteraction(Long interactionId);

    /**
     * 修复当前实例已无后台任务但数据库仍为生成中的过期占位消息。
     */
    int reconcileStaleGenerations(OffsetDateTime before, int limit);
}
