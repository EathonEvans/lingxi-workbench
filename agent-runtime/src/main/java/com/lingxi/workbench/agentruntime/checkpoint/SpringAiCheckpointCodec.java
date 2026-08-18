package com.lingxi.workbench.agentruntime.checkpoint;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Spring AI Message 与稳定 Runtime Checkpoint 消息协议之间的唯一编解码边界。 */
@Component
public class SpringAiCheckpointCodec {

    public List<RuntimeCheckpoint.MessageSnapshot> encodeMessages(List<Message> messages) {
        return Objects.requireNonNull(messages, "Checkpoint消息不能为空").stream()
                .map(this::encodeMessage)
                .toList();
    }

    public List<Message> decodeMessages(List<RuntimeCheckpoint.MessageSnapshot> messages) {
        return Objects.requireNonNull(messages, "Checkpoint消息不能为空").stream()
                .map(this::decodeMessage)
                .toList();
    }

    public RuntimeCheckpoint.ToolCallSnapshot encodeToolCall(AssistantMessage.ToolCall call) {
        return RuntimeCheckpoint.ToolCallSnapshot.builder()
                .id(call.id())
                .type(call.type())
                .name(call.name())
                .arguments(call.arguments())
                .build();
    }

    public AssistantMessage.ToolCall decodeToolCall(RuntimeCheckpoint.ToolCallSnapshot call) {
        requireToolCall(call);
        return new AssistantMessage.ToolCall(call.getId(), call.getType(), call.getName(), call.getArguments());
    }

    public ChatResponse pendingToolResponse(RuntimeCheckpoint.ToolCallSnapshot call) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(decodeToolCall(call)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private RuntimeCheckpoint.MessageSnapshot encodeMessage(Message message) {
        if (message instanceof SystemMessage system) {
            return plain(RuntimeCheckpoint.MessageKind.SYSTEM, system.getText());
        }
        if (message instanceof UserMessage user) {
            return plain(RuntimeCheckpoint.MessageKind.USER, user.getText());
        }
        if (message instanceof AssistantMessage assistant) {
            return RuntimeCheckpoint.MessageSnapshot.builder()
                    .kind(RuntimeCheckpoint.MessageKind.ASSISTANT)
                    .text(Objects.requireNonNullElse(assistant.getText(), ""))
                    .toolCalls(assistant.getToolCalls().stream().map(this::encodeToolCall).toList())
                    .toolResponses(List.of())
                    .build();
        }
        if (message instanceof ToolResponseMessage tool) {
            return RuntimeCheckpoint.MessageSnapshot.builder()
                    .kind(RuntimeCheckpoint.MessageKind.TOOL)
                    .text("")
                    .toolCalls(List.of())
                    .toolResponses(tool.getResponses().stream()
                            .map(response -> RuntimeCheckpoint.ToolResponseSnapshot.builder()
                                    .id(response.id())
                                    .name(response.name())
                                    .responseData(response.responseData())
                                    .build())
                            .toList())
                    .build();
        }
        throw new IllegalArgumentException("Checkpoint不支持Spring AI消息类型：" + message.getClass().getName());
    }

    private Message decodeMessage(RuntimeCheckpoint.MessageSnapshot message) {
        if (Objects.isNull(message) || Objects.isNull(message.getKind())) {
            throw new IllegalArgumentException("Checkpoint消息类型不能为空");
        }
        return switch (message.getKind()) {
            case SYSTEM -> new SystemMessage(Objects.requireNonNullElse(message.getText(), ""));
            case USER -> new UserMessage(Objects.requireNonNullElse(message.getText(), ""));
            case ASSISTANT -> AssistantMessage.builder()
                    .content(Objects.requireNonNullElse(message.getText(), ""))
                    .toolCalls(safeToolCalls(message).stream()
                            .map(this::decodeToolCall)
                            .toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(safeToolResponses(message).stream()
                            .map(response -> new ToolResponseMessage.ToolResponse(
                                    response.getId(), response.getName(), response.getResponseData()))
                            .toList())
                    .build();
        };
    }

    private static List<RuntimeCheckpoint.ToolCallSnapshot> safeToolCalls(
            RuntimeCheckpoint.MessageSnapshot message) {
        return message.getToolCalls() == null ? List.of() : message.getToolCalls();
    }

    private static List<RuntimeCheckpoint.ToolResponseSnapshot> safeToolResponses(
            RuntimeCheckpoint.MessageSnapshot message) {
        return message.getToolResponses() == null ? List.of() : message.getToolResponses();
    }

    private static RuntimeCheckpoint.MessageSnapshot plain(RuntimeCheckpoint.MessageKind kind, String text) {
        return RuntimeCheckpoint.MessageSnapshot.builder()
                .kind(kind)
                .text(Objects.requireNonNullElse(text, ""))
                .toolCalls(List.of())
                .toolResponses(List.of())
                .build();
    }

    private static void requireToolCall(RuntimeCheckpoint.ToolCallSnapshot call) {
        if (Objects.isNull(call) || Objects.isNull(call.getId()) || call.getId().isBlank()
                || Objects.isNull(call.getName()) || call.getName().isBlank()
                || Objects.isNull(call.getArguments())) {
            throw new IllegalArgumentException("Checkpoint ToolCall不完整");
        }
    }
}
