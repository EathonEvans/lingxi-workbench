package com.lingxi.workbench.admin.appmonitor.model.request;

import com.lingxi.workbench.admin.appmonitor.common.enums.AppExecutionMode;
import com.lingxi.workbench.admin.appmonitor.common.enums.AppRunStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 应用监控查询条件，最多查询最近七天。 */
@Getter
@Setter
public class AppMonitorQuery extends PageQuery {
    private Long applicationId;
    @Min(value = 1, message = "时间范围不能小于1小时")
    @Max(value = 168, message = "时间范围不能超过168小时")
    private int rangeHours = 24;
    private AppExecutionMode mode;
    private AppRunStatus status;
    @Size(max = 64, message = "查询关键字不能超过64个字符")
    private String keyword;
    @Size(max = 32, message = "阶段编码不能超过32个字符")
    private String stage;
    @Size(max = 128, message = "异常类型不能超过128个字符")
    private String errorType;
}
