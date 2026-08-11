package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;

/** 模型生成任务向观察者发布的安全事件。 */
@EqualsAndHashCode
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatGenerationEventBO {

    private final ChatGenerationEventType type;
    private final long sequence;
    private final String content;
    private final AgentToolExecutionEvent tool;
    private final ChatClarificationEventBO clarification;
    private final ChatExecutionTimelineEventBO execution;
    private final ChatStepOutputEventBO stepOutput;
    private final ChatErrorBO error;

    public static ChatGenerationEventBO chunk(long sequence, String content) {
        return ChatGenerationEventBO.builder()
                .type(ChatGenerationEventType.CHUNK)
                .sequence(sequence)
                .content(content)
                .build();
    }

    public static ChatGenerationEventBO tool(long sequence, AgentToolExecutionEvent tool) {
        return ChatGenerationEventBO.builder()
                .type(ChatGenerationEventType.TOOL)
                .sequence(sequence)
                .tool(tool)
                .build();
    }

    public static ChatGenerationEventBO clarification(long sequence, ChatClarificationEventBO clarification) {
        return ChatGenerationEventBO.builder()
                .type(ChatGenerationEventType.CLARIFICATION)
                .sequence(sequence)
                .clarification(clarification)
                .build();
    }

    public static ChatGenerationEventBO execution(long sequence, ChatGenerationEventType type,
                                                  ChatExecutionTimelineEventBO execution) {
        if (type != ChatGenerationEventType.PLANNING && type != ChatGenerationEventType.PLAN
                && type != ChatGenerationEventType.STEP && type != ChatGenerationEventType.REPLANNING) {
            throw new IllegalArgumentException("执行时间线事件类型不正确");
        }
        return ChatGenerationEventBO.builder()
                .type(type)
                .sequence(sequence)
                .execution(execution)
                .build();
    }

    public static ChatGenerationEventBO stepOutput(long sequence, ChatStepOutputEventBO stepOutput) {
        return ChatGenerationEventBO.builder()
                .type(ChatGenerationEventType.STEP_OUTPUT)
                .sequence(sequence)
                .stepOutput(stepOutput)
                .build();
    }

    public static ChatGenerationEventBO snapshot(String content) {
        return ChatGenerationEventBO.builder()
                .type(ChatGenerationEventType.SNAPSHOT)
                .content(content)
                .build();
    }

    public static ChatGenerationEventBO completed() {
        return ChatGenerationEventBO.builder().type(ChatGenerationEventType.COMPLETED).build();
    }

    public static ChatGenerationEventBO failed() {
        return failed("AI_MODEL_ERROR", "模型服务暂时不可用，请稍后重试");
    }

    public static ChatGenerationEventBO failed(String code, String message) {
        return ChatGenerationEventBO.builder()
                .type(ChatGenerationEventType.FAILED)
                .error(new ChatErrorBO(code, message))
                .build();
    }

    public static ChatGenerationEventBO cancelled() {
        return ChatGenerationEventBO.builder().type(ChatGenerationEventType.CANCELLED).build();
    }

    public ChatGenerationEventType type() {
        return type;
    }

    public long sequence() {
        return sequence;
    }

    public String content() {
        return content;
    }

    public AgentToolExecutionEvent tool() {
        return tool;
    }

    public ChatClarificationEventBO clarification() {
        return clarification;
    }

    public ChatExecutionTimelineEventBO execution() {
        return execution;
    }

    public ChatStepOutputEventBO stepOutput() {
        return stepOutput;
    }

    public ChatErrorBO error() {
        return error;
    }
}
