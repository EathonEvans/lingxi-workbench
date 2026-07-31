package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.chat.controller.router.ChatGenerationSseEventRouter;
import com.lingxi.workbench.chat.model.request.CancelChatRequest;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.model.request.ResumeChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.context.UserContext;
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
        return chatStreamService.resume(Long.valueOf(request.assistantMessageId()), request.afterSequence(), UserContext.userId())
                .map(ChatGenerationSseEventRouter::rout);
    }

    /**
     * 用户显式停止模型生成，普通 SSE 断开不会调用此接口。
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody CancelChatRequest request) {
        chatStreamService.cancel(Long.valueOf(request.assistantMessageId()), UserContext.userId());
        return ApiResponse.success();
    }
}
