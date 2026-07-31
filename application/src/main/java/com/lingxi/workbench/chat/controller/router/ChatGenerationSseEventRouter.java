package com.lingxi.workbench.chat.controller.router;

import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.chat.model.vo.ChatErrorVO;
import com.lingxi.workbench.chat.model.vo.ChatSnapshotVO;
import com.lingxi.workbench.chat.model.vo.ChatToolEventVO;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import org.springframework.http.codec.ServerSentEvent;

import java.util.Objects;

import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.GENERATION_CANCELLED_CODE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.GENERATION_CANCELLED_MESSAGE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.MODEL_ERROR_CLIENT_MESSAGE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.MODEL_ERROR_CODE;

/**
 * 将内部生成事件转换为对外 SSE 协议事件。
 *
 * <p>该转换器只处理传输协议映射，不查询数据、不修改生成状态，也不承担接口权限校验。</p>
 */
public final class ChatGenerationSseEventRouter {

    private ChatGenerationSseEventRouter() {
    }

    /**
     * 根据内部事件类型构建对应的 SSE 事件。
     */
    public static ServerSentEvent<?> rout(ChatGenerationEventBO event) {
        return switch (event.type()) {
            case CHUNK -> messageEvent(event);
            case TOOL -> toolEvent(event);
            case SNAPSHOT -> snapshotEvent(event.content());
            case COMPLETED -> doneEvent();
            case FAILED -> errorEvent();
            case CANCELLED -> cancelledEvent();
        };
    }

    /**
     * 构建文本增量事件，携带自增序号用于客户端恢复订阅。
     */
    private static ServerSentEvent<ChatChunkVO> messageEvent(ChatGenerationEventBO event) {
        return ServerSentEvent.<ChatChunkVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("message")
                .data(new ChatChunkVO(event.sequence(), event.content()))
                .build();
    }

    /**
     * 构建工具执行状态事件，不暴露工具参数和原始结果。
     */
    private static ServerSentEvent<ChatToolEventVO> toolEvent(ChatGenerationEventBO event) {
        AgentToolExecutionEvent tool = Objects.requireNonNull(event.tool(), "工具事件内容不能为空");
        ChatToolEventVO payload = ChatToolEventVO.builder()
                .sequence(event.sequence())
                .invocationId(tool.getInvocationId())
                .toolCode(tool.getToolCode())
                .displayName(tool.getDisplayName())
                .status(tool.getStatus().getCode())
                .durationMillis(tool.getDurationMillis())
                .errorCode(tool.getErrorCode())
                .build();
        return ServerSentEvent.<ChatToolEventVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("tool")
                .data(payload)
                .build();
    }

    /**
     * 构建完整回答快照事件，用于续接时快速恢复已生成文本。
     */
    private static ServerSentEvent<ChatSnapshotVO> snapshotEvent(String content) {
        return ServerSentEvent.<ChatSnapshotVO>builder()
                .event("snapshot")
                .data(new ChatSnapshotVO(content))
                .build();
    }

    /**
     * 构建生成完成事件。
     */
    private static ServerSentEvent<Void> doneEvent() {
        return ServerSentEvent.<Void>builder().event("done").build();
    }

    /**
     * 构建模型调用失败事件。
     */
    private static ServerSentEvent<ChatErrorVO> errorEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("error")
                .data(new ChatErrorVO(MODEL_ERROR_CODE, MODEL_ERROR_CLIENT_MESSAGE))
                .build();
    }

    /**
     * 构建用户取消生成事件。
     */
    private static ServerSentEvent<ChatErrorVO> cancelledEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("cancelled")
                .data(new ChatErrorVO(GENERATION_CANCELLED_CODE, GENERATION_CANCELLED_MESSAGE))
                .build();
    }
}
