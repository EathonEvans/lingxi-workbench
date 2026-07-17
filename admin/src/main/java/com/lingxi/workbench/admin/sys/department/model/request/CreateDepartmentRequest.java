package com.lingxi.workbench.admin.sys.department.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增部门请求。
 */
public record CreateDepartmentRequest(
        /** 上级部门主键。 */
        @NotNull(message = "上级部门不能为空")
        Long parentId,

        /** 部门名称。 */
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 64, message = "部门名称长度不能超过64个字符")
        String name,

        /** 部门编码，仅支持大写字母、数字和下划线。 */
        @NotBlank(message = "部门编码不能为空")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "部门编码必须使用大写字母、数字和下划线")
        @Size(max = 64, message = "部门编码长度不能超过64个字符")
        String code,

        /** 同级排序序号。 */
        Integer sortOrder,

        /** 部门状态。 */
        @NotNull(message = "部门状态不能为空")
        EnableStatus status
) {
}
