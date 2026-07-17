package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.auth.context.UserContext;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatStreamResult;
import com.lingxi.workbench.chat.model.request.CancelChatRequest;
import com.lingxi.workbench.chat.model.request.ResumeChatRequest;
import com.lingxi.workbench.chat.model.request.StreamChatRequest;
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

/**
 * AI 流式对话接口。
 */
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class ChatStreamController {

    private static final String MODEL_ERROR_CODE = "AI_MODEL_ERROR";
    private static final String MODEL_ERROR_MESSAGE = "模型服务暂时不可用，请稍后重试";
    private static final String CANCELLED_CODE = "GENERATION_CANCELLED";
    private static final String CANCELLED_MESSAGE = "已停止生成";

    private final ChatStreamService chatStreamService;

    /**
     * 创建独立模型生成任务并返回稳定任务标识。
     * 后续是否订阅 SSE 不影响该任务继续执行。
     */
    @PostMapping("/start")
    public ApiResponse<ChatStartVO> start(@Valid @RequestBody StreamChatRequest request) {
        ChatStreamResult result = chatStreamService.stream(request, UserContext.get().id());
        return ApiResponse.success(result.start());
    }

    /**
     * 兼容旧客户端：创建任务后立即在同一个响应中订阅增量内容。
     *
     * @param request 对话请求
     * @return SSE 事件流
     */
    @PostMapping(
            value = "/stream",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE}
    )
    public Flux<ServerSentEvent<?>> stream(@Valid @RequestBody StreamChatRequest request) {
        ChatStreamResult result = chatStreamService.stream(request, UserContext.get().id());
        return Flux.concat(
                Flux.just(startEvent(result.start())),
                result.events().map(ChatStreamController::toSseEvent));
    }

    /** 浏览器刷新或网络恢复后，继续订阅原模型生成任务。 */
    @PostMapping(
            value = "/resume",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE}
    )
    public Flux<ServerSentEvent<?>> resume(@Valid @RequestBody ResumeChatRequest request) {
        return chatStreamService.resume(
                        Long.valueOf(request.assistantMessageId()),
                        request.afterSequence(),
                        UserContext.get().id())
                .map(ChatStreamController::toSseEvent);
    }

    /** 用户显式停止模型生成，普通 SSE 断开不会调用此接口。 */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody CancelChatRequest request) {
        chatStreamService.cancel(
                Long.valueOf(request.assistantMessageId()), UserContext.get().id());
        return ApiResponse.success();
    }

    private static ServerSentEvent<ChatStartVO> startEvent(ChatStartVO start) {
        return ServerSentEvent.<ChatStartVO>builder()
                .event("start")
                .data(start)
                .build();
    }

    private static ServerSentEvent<?> toSseEvent(ChatGenerationEventBO event) {
        return switch (event.type()) {
            case CHUNK -> messageEvent(event);
            case SNAPSHOT -> snapshotEvent(event.content());
            case COMPLETED -> doneEvent();
            case FAILED -> errorEvent();
            case CANCELLED -> cancelledEvent();
        };
    }

    private static ServerSentEvent<ChatChunkVO> messageEvent(ChatGenerationEventBO event) {
        return ServerSentEvent.<ChatChunkVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("message")
                .data(new ChatChunkVO(event.sequence(), event.content()))
                .build();
    }

    private static ServerSentEvent<ChatSnapshotVO> snapshotEvent(String content) {
        return ServerSentEvent.<ChatSnapshotVO>builder()
                .event("snapshot")
                .data(new ChatSnapshotVO(content))
                .build();
    }

    private static ServerSentEvent<Void> doneEvent() {
        return ServerSentEvent.<Void>builder()
                .event("done")
                .build();
    }

    private static ServerSentEvent<ChatErrorVO> errorEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("error")
                .data(new ChatErrorVO(MODEL_ERROR_CODE, MODEL_ERROR_MESSAGE))
                .build();
    }

    private static ServerSentEvent<ChatErrorVO> cancelledEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("cancelled")
                .data(new ChatErrorVO(CANCELLED_CODE, CANCELLED_MESSAGE))
                .build();
    }
}
