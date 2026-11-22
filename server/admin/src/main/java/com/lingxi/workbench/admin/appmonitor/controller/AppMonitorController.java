package com.lingxi.workbench.admin.appmonitor.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import com.lingxi.workbench.admin.appmonitor.model.request.ReplayConversationDlqRequest;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationDlqResultVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppMonitorOverviewVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppOptionVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceDetailVO;
import com.lingxi.workbench.admin.appmonitor.model.vo.AppTraceVO;
import com.lingxi.workbench.admin.appmonitor.service.AppMonitorService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Application 运行监控只读接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/app-monitor")
@RequiredArgsConstructor
public class AppMonitorController {

    private final AppMonitorService monitorService;

    @GetMapping("/options")
    @SaCheckPermission("application:list")
    public ApiResponse<List<AppOptionVO>> options() {
        return ApiResponse.success(monitorService.options());
    }

    @GetMapping("/overview")
    @SaCheckPermission("application:list")
    public ApiResponse<AppMonitorOverviewVO> overview(@Valid AppMonitorQuery query) {
        return ApiResponse.success(monitorService.overview(query));
    }

    @GetMapping("/traces")
    @SaCheckPermission("application:list")
    public ApiResponse<PageResult<AppTraceVO>> traces(@Valid AppMonitorQuery query) {
        return ApiResponse.success(monitorService.traces(query));
    }

    @GetMapping("/traces/{traceId}")
    @SaCheckPermission("application:list")
    public ApiResponse<AppTraceDetailVO> traceDetail(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F]{16,64}", message = "Trace ID格式不正确") String traceId) {
        return ApiResponse.success(monitorService.traceDetail(traceId));
    }

    @GetMapping("/conversation-ops/overview")
    @SaCheckPermission("application:list")
    public ApiResponse<ConversationOpsOverviewVO> conversationOpsOverview() {
        return ApiResponse.success(monitorService.conversationOpsOverview());
    }

    @GetMapping("/conversation-ops/dlq")
    @SaCheckPermission("application:list")
    public ApiResponse<ConversationDlqResultVO> conversationDlq(
            @RequestParam(defaultValue = "50") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(200) int limit) {
        return ApiResponse.success(monitorService.conversationDlq(limit));
    }

    @PostMapping("/conversation-ops/reconcile")
    @SaCheckPermission("application:manage")
    public ApiResponse<ConversationReconcileVO> reconcileConversations() {
        return ApiResponse.success(monitorService.reconcileConversations());
    }

    @PostMapping("/conversation-ops/dlq/replay")
    @SaCheckPermission("application:manage")
    public ApiResponse<Void> replayConversationDlq(
            @Valid @RequestBody ReplayConversationDlqRequest request) {
        monitorService.replayConversationDlq(request);
        return ApiResponse.success();
    }
}
