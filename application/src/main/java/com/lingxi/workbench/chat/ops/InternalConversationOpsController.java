package com.lingxi.workbench.chat.ops;

import com.lingxi.workbench.chat.ops.model.request.ReplayConversationDlqRequest;
import com.lingxi.workbench.chat.ops.model.vo.ConversationDlqMessageVO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 仅供Admin后端调用的会话事件运维接口。 */
@Validated
@RestController
@RequestMapping("/internal/v1/conversation-ops")
@RequiredArgsConstructor
public class InternalConversationOpsController {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final ConversationOpsService opsService;
    private final InternalOpsTokenVerifier tokenVerifier;

    @GetMapping("/overview")
    public ApiResponse<ConversationOpsOverviewVO> overview(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token) {
        tokenVerifier.verify(token);
        return ApiResponse.success(opsService.overview());
    }

    @GetMapping("/dlq")
    public ApiResponse<List<ConversationDlqMessageVO>> dlq(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        tokenVerifier.verify(token);
        return ApiResponse.success(opsService.dlq(limit));
    }

    @PostMapping("/dlq/replay")
    public ApiResponse<Void> replay(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody ReplayConversationDlqRequest request) {
        tokenVerifier.verify(token);
        opsService.replay(request.group(), request.msgId());
        return ApiResponse.success();
    }

    @PostMapping("/reconcile")
    public ApiResponse<ConversationReconcileVO> reconcile(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token) {
        tokenVerifier.verify(token);
        return ApiResponse.success(opsService.reconcile());
    }
}
