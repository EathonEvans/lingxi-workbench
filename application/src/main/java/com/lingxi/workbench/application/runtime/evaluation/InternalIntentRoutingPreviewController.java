package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.chat.ops.InternalOpsTokenVerifier;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供 Admin 后端调用的 Application 草稿意图测试接口。 */
@RestController
@RequestMapping("/internal/v1/intent-routing")
@RequiredArgsConstructor
public class InternalIntentRoutingPreviewController {

    private static final String OPS_TOKEN_HEADER = "X-Lingxi-Ops-Token";

    private final InternalOpsTokenVerifier tokenVerifier;
    private final IntentRoutingPreviewService previewService;

    @PostMapping("/preview")
    public ApiResponse<IntentRoutingPreviewResult> preview(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingPreviewRequest request) {
        tokenVerifier.verify(token);
        return ApiResponse.success(previewService.preview(request));
    }

    @PostMapping("/preview-version")
    public ApiResponse<IntentRoutingPreviewResult> previewVersion(
            @RequestHeader(value = OPS_TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody IntentRoutingVersionPreviewRequest request) {
        tokenVerifier.verify(token);
        return ApiResponse.success(previewService.previewVersion(request));
    }
}
