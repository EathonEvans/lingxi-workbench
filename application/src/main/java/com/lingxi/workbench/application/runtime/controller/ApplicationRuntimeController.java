package com.lingxi.workbench.application.runtime.controller;

import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;
import com.lingxi.workbench.application.runtime.service.ApplicationRuntimeService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 应用运行端接口。
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationRuntimeController {

    private final ApplicationRuntimeService service;

    /**
     * 查询当前用户端可选择的已发布应用。
     *
     * @return 可用应用的稳定标识与展示信息
     */
    @GetMapping("/available")
    public ApiResponse<List<AvailableApplicationVO>> listAvailable() {
        return ApiResponse.success(service.listAvailableApplications());
    }
}
