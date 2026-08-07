package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Redis 中保存的对话生成事件。
 *
 * <p>缓存模型显式展开工具安全字段，不序列化工具参数、原始结果或异常堆栈。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatGenerationEventCacheBO {

    private ChatGenerationEventType type;
    private long sequence;
    private String content;
    private String invocationId;
    private String toolCode;
    private String displayName;
    private AgentToolExecutionStatus toolStatus;
    private Long durationMillis;
    private String errorCode;
    private ChatClarificationEventBO clarification;
    private ChatExecutionTimelineEventBO execution;

    /**
     * 将运行时事件转换为可安全缓存的结构。
     */
    public static ChatGenerationEventCacheBO from(ChatGenerationEventBO event) {
        AgentToolExecutionEvent tool = event.tool();
        ChatGenerationEventCacheBOBuilder builder = ChatGenerationEventCacheBO.builder()
                .type(event.type())
                .sequence(event.sequence())
                .content(event.content())
                .execution(event.execution());
        if (Objects.nonNull(event.clarification())) {
            builder.clarification(event.clarification());
        }
        if (Objects.nonNull(tool)) {
            builder.invocationId(tool.getInvocationId())
                    .toolCode(tool.getToolCode())
                    .displayName(tool.getDisplayName())
                    .toolStatus(tool.getStatus())
                    .durationMillis(tool.getDurationMillis())
                    .errorCode(tool.getErrorCode());
        }
        return builder.build();
    }

    /**
     * 将 Redis 缓存恢复为应用层生成事件。
     */
    public ChatGenerationEventBO toEvent() {
        return switch (type) {
            case CHUNK -> ChatGenerationEventBO.chunk(sequence, Objects.requireNonNullElse(content, ""));
            case TOOL -> ChatGenerationEventBO.tool(sequence, AgentToolExecutionEvent.builder()
                    .invocationId(invocationId)
                    .toolCode(toolCode)
                    .displayName(displayName)
                    .status(toolStatus)
                    .durationMillis(durationMillis)
                    .errorCode(errorCode)
                    .build());
            case CLARIFICATION -> ChatGenerationEventBO.clarification(
                    sequence,
                    Objects.requireNonNull(clarification, "澄清事件缓存内容不能为空"));
            case PLANNING, PLAN, STEP, REPLANNING -> ChatGenerationEventBO.execution(
                    sequence, type, Objects.requireNonNull(execution, "执行时间线缓存内容不能为空"));
            default -> throw new IllegalStateException("Redis中存在不支持的生成事件类型：" + type);
        };
    }
}
