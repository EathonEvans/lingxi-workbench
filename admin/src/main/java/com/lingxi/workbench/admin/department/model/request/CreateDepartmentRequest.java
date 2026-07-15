package com.lingxi.workbench.admin.department.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增部门请求。
 */
public record CreateDepartmentRequest(
        @NotNull(message = "上级部门不能为空")
        Long parentId,

        @NotBlank(message = "部门名称不能为空")
        @Size(max = 64, message = "部门名称长度不能超过64个字符")
        String name,

        @NotBlank(message = "部门编码不能为空")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "部门编码必须使用大写字母、数字和下划线")
        @Size(max = 64, message = "部门编码长度不能超过64个字符")
        String code,

        Integer sortOrder,

        @NotNull(message = "部门状态不能为空")
        EnableStatus status
) {
}
