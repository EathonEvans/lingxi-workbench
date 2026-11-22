package com.lingxi.workbench.notification.controller;

import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.notification.model.request.MarkNotificationsReadRequest;
import com.lingxi.workbench.notification.model.vo.NotificationVO;
import com.lingxi.workbench.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应用端站内通知接口。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询当前用户的通知。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<NotificationVO>> list(@Valid PageQuery query) {
        return ApiResponse.success(notificationService.page(UserContext.userId(), query));
    }

    /**
     * 查询当前用户的未读通知数。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", notificationService.unreadCount(UserContext.userId())));
    }

    /**
     * 批量标记通知已读。
     */
    @PostMapping("/read")
    public ApiResponse<Void> markRead(@Valid @RequestBody MarkNotificationsReadRequest request) {
        notificationService.markRead(UserContext.userId(), request.getIds());
        return ApiResponse.success();
    }

    /**
     * 全部标记已读。
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(UserContext.userId());
        return ApiResponse.success();
    }
}
