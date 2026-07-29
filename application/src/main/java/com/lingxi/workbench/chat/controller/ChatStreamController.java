package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.request.CancelChatRequest;
import com.lingxi.workbench.chat.model.request.ResumeChatRequest;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.chat.model.vo.ChatErrorVO;
import com.lingxi.workbench.chat.model.vo.ChatSnapshotVO;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.GENERATION_CANCELLED_CODE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.GENERATION_CANCELLED_MESSAGE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.MODEL_ERROR_CLIENT_MESSAGE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.MODEL_ERROR_CODE;

/**
 * AI 流式对话接口。
 */
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    /**
     * 创建独立模型生成任务并返回稳定任务标识。
     * 后续是否订阅 SSE 不影响该任务继续执行。
     */
    @PostMapping("/start")
    public ApiResponse<ChatStartVO> start(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatStreamService.start(request, UserContext.get()));
    }

    /**
     * 浏览器刷新或网络恢复后，继续订阅原模型生成任务。
     */
    @PostMapping(
            value = "/resume",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE}
    )
    public Flux<ServerSentEvent<?>> resume(@Valid @RequestBody ResumeChatRequest request) {
        return chatStreamService.resume(
                        Long.valueOf(request.assistantMessageId()),
                        request.afterSequence(),
                        UserContext.userId())
                .map(ChatStreamController::toSseEvent);
    }

    /**
     * 用户显式停止模型生成，普通 SSE 断开不会调用此接口。
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody CancelChatRequest request) {
        chatStreamService.cancel(
                Long.valueOf(request.assistantMessageId()), UserContext.userId());
        return ApiResponse.success();
    }

    /** 将生成事件转换为 SSE 事件，每个事件类型对应一个 SSE event 名称。 */
    private static ServerSentEvent<?> toSseEvent(ChatGenerationEventBO event) {
        return switch (event.type()) {
            case CHUNK -> messageEvent(event);
            case SNAPSHOT -> snapshotEvent(event.content());
            case COMPLETED -> doneEvent();
            case FAILED -> errorEvent();
            case CANCELLED -> cancelledEvent();
        };
    }

    /** 构建文本增量 SSE 事件，携带自增序号用于客户端恢复订阅。 */
    private static ServerSentEvent<ChatChunkVO> messageEvent(ChatGenerationEventBO event) {
        return ServerSentEvent.<ChatChunkVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("message")
                .data(new ChatChunkVO(event.sequence(), event.content()))
                .build();
    }

    /** 构建完整回答快照 SSE 事件，用于续接时快速恢复已生成文本。 */
    private static ServerSentEvent<ChatSnapshotVO> snapshotEvent(String content) {
        return ServerSentEvent.<ChatSnapshotVO>builder()
                .event("snapshot")
                .data(new ChatSnapshotVO(content))
                .build();
    }

    /** 构建生成完成 SSE 事件，通知客户端模型流已正常结束。 */
    private static ServerSentEvent<Void> doneEvent() {
        return ServerSentEvent.<Void>builder()
                .event("done")
                .build();
    }

    /** 构建模型调用失败 SSE 事件。 */
    private static ServerSentEvent<ChatErrorVO> errorEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("error")
                .data(new ChatErrorVO(
                        MODEL_ERROR_CODE,
                        MODEL_ERROR_CLIENT_MESSAGE))
                .build();
    }

    /** 构建用户取消生成 SSE 事件。 */
    private static ServerSentEvent<ChatErrorVO> cancelledEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("cancelled")
                .data(new ChatErrorVO(
                        GENERATION_CANCELLED_CODE,
                        GENERATION_CANCELLED_MESSAGE))
                .build();
    }
}
