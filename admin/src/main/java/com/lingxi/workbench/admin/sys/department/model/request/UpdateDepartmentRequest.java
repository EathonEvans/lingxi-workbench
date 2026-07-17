package com.lingxi.workbench.admin.sys.department.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改部门请求。部门编码创建后保持稳定。
 */
public record UpdateDepartmentRequest(
        /** 上级部门主键。 */
        Long parentId,

        /** 部门名称。 */
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 64, message = "部门名称长度不能超过64个字符")
        String name,

        /** 同级排序序号。 */
        Integer sortOrder,

        /** 部门状态。 */
        @NotNull(message = "部门状态不能为空")
        EnableStatus status
) {
}
