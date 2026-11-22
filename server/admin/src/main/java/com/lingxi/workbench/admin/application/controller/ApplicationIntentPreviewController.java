package com.lingxi.workbench.admin.application.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.application.model.request.PreviewApplicationIntentRequest;
import com.lingxi.workbench.admin.application.model.vo.ApplicationIntentPreviewVO;
import com.lingxi.workbench.admin.application.service.ApplicationIntentPreviewService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Application 草稿意图测试入口。 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationIntentPreviewController {

    private final ApplicationIntentPreviewService previewService;

    @PostMapping("/preview-intent/{id}")
    @SaCheckPermission("application:manage")
    public ApiResponse<ApplicationIntentPreviewVO> preview(
            @PathVariable Long id, @Valid @RequestBody PreviewApplicationIntentRequest request) {
        return ApiResponse.success(previewService.preview(id, request));
    }
}
