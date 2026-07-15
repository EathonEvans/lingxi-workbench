package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.chat.model.request.StreamChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.chat.model.vo.ChatErrorVO;
import com.lingxi.workbench.chat.service.ChatStreamService;
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

    private final ChatStreamService chatStreamService;

    /**
     * 调用 DeepSeek 并以 SSE 形式返回增量内容。
     *
     * @param request 对话请求
     * @return SSE 事件流
     */
    @PostMapping(
            value = "/stream",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE}
    )
    public Flux<ServerSentEvent<?>> stream(@Valid @RequestBody StreamChatRequest request) {
        Flux<ServerSentEvent<?>> chunks = chatStreamService.stream(request)
                .map(ChatStreamController::messageEvent);
        return chunks
                .concatWithValues(doneEvent())
                .onErrorResume(exception -> Flux.just(errorEvent()));
    }

    private static ServerSentEvent<ChatChunkVO> messageEvent(ChatChunkVO chunk) {
        return ServerSentEvent.<ChatChunkVO>builder()
                .event("message")
                .data(chunk)
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
}
