package com.lingxi.workbench.chat.controller;

import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.chat.model.request.ConversationPageQuery;
import com.lingxi.workbench.chat.model.request.RenameConversationRequest;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import com.lingxi.workbench.chat.model.vo.ConversationVO;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用端会话及消息接口。
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService service;

    /**
     * 创建新会话。
     */
    @PostMapping("/create")
    public ApiResponse<ConversationVO> create() {
        return ApiResponse.success(service.create(UserContext.userId()));
    }

    /**
     * 分页查询当前用户的会话。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<ConversationVO>> list(@Valid ConversationPageQuery query) {
        return ApiResponse.success(service.page(UserContext.userId(), query));
    }

    /**
     * 分页查询指定会话的消息历史。
     */
    @GetMapping("/message-list")
    public ApiResponse<PageResult<ConversationMessageVO>> messageList(
            @RequestParam Long conversationId, @Valid PageQuery query) {
        return ApiResponse.success(service.messagePage(UserContext.userId(), conversationId, query));
    }

    /**
     * 修改会话标题。
     */
    @PostMapping("/rename")
    public ApiResponse<Void> rename(
            @RequestParam Long conversationId,
            @Valid @RequestBody RenameConversationRequest request) {
        service.rename(UserContext.userId(), conversationId, request);
        return ApiResponse.success();
    }

    /**
     * 归档会话。
     */
    @PostMapping("/archive")
    public ApiResponse<Void> archive(@RequestParam Long conversationId) {
        service.archive(UserContext.userId(), conversationId);
        return ApiResponse.success();
    }

    /**
     * 软删除会话。
     */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam Long conversationId) {
        service.delete(UserContext.userId(), conversationId);
        return ApiResponse.success();
    }
}
