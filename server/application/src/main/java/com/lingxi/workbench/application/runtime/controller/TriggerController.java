package com.lingxi.workbench.application.runtime.controller;

import com.lingxi.workbench.application.runtime.service.trigger.TriggerService;
import com.lingxi.workbench.application.runtime.service.trigger.model.request.CreateTriggerRequest;
import com.lingxi.workbench.application.runtime.service.trigger.model.request.UpdateTriggerRequest;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerCapabilitiesVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerFireVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerVO;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用端定时触发器接口。
 */
@RestController
@RequestMapping("/api/v1/triggers")
@RequiredArgsConstructor
@Validated
public class TriggerController {

    private final TriggerService triggerService;

    /**
     * 创建定时触发器。
     */
    @PostMapping("/create")
    public ApiResponse<TriggerVO> create(@Valid @RequestBody CreateTriggerRequest request) {
        return ApiResponse.success(triggerService.create(UserContext.userId(), request));
    }

    /**
     * 列出应用当前发布版本中可绑定的能力。
     */
    @GetMapping("/capabilities")
    public ApiResponse<TriggerCapabilitiesVO> capabilities(
            @RequestParam @NotBlank(message = "应用编码不能为空") String applicationCode) {
        return ApiResponse.success(triggerService.listCapabilities(UserContext.userId(), applicationCode));
    }

    /**
     * 分页查询当前用户的触发器。
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<TriggerVO>> list(@Valid PageQuery query) {
        return ApiResponse.success(triggerService.page(UserContext.userId(), query));
    }

    /**
     * 更新触发器可编辑字段。
     */
    @PostMapping("/update/{id}")
    public ApiResponse<Void> update(@PathVariable @NotNull Long id, @Valid @RequestBody UpdateTriggerRequest request) {
        triggerService.update(UserContext.userId(), id, request);
        return ApiResponse.success();
    }

    /**
     * 暂停调度。
     */
    @PostMapping("/pause/{id}")
    public ApiResponse<Void> pause(@PathVariable @NotNull Long id) {
        triggerService.pause(UserContext.userId(), id);
        return ApiResponse.success();
    }

    /**
     * 恢复调度。
     */
    @PostMapping("/resume/{id}")
    public ApiResponse<Void> resume(@PathVariable @NotNull Long id) {
        triggerService.resume(UserContext.userId(), id);
        return ApiResponse.success();
    }

    /**
     * 逻辑删除触发器。
     */
    @PostMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable @NotNull Long id) {
        triggerService.delete(UserContext.userId(), id);
        return ApiResponse.success();
    }

    /**
     * 分页查询触发器运行历史。
     */
    @GetMapping("/fire-list")
    public ApiResponse<PageResult<TriggerFireVO>> fireList(
            @RequestParam @NotBlank @Pattern(regexp = "^[1-9]\\d*$", message = "触发器ID格式不正确") String triggerId,
            @Valid PageQuery query) {
        return ApiResponse.success(triggerService.firePage(UserContext.userId(), Long.valueOf(triggerId), query));
    }
}
