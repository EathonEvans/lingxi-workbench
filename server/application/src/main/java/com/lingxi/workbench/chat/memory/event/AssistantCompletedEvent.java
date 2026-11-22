package com.lingxi.workbench.chat.memory.event;

import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.workflow.model.MemoryConfig;

import java.util.Objects;

/**
 * Assistant 消息完成事件。携带会话标识和记忆配置供 MemoryPostProcessor 消费。
 *
 * @param userId 当前用户主键
 * @param turn 已完成的对话轮次
 * @param applicationVersionId 本轮冻结的ApplicationVersion主键
 * @param memoryConfig ApplicationVersion 冻结的记忆配置
 */
public record AssistantCompletedEvent(
        Long userId,
        ChatTurnBO turn,
        Long applicationVersionId,
        MemoryConfig memoryConfig
) {

    public AssistantCompletedEvent {
        Objects.requireNonNull(userId, "用户主键不能为空");
        Objects.requireNonNull(turn, "对话轮次不能为空");
        Objects.requireNonNull(applicationVersionId, "ApplicationVersion不能为空");
        Objects.requireNonNull(memoryConfig, "记忆配置不能为空");
    }
}
