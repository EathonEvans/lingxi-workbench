package com.lingxi.workbench.admin.sys.department.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增部门请求。
 *
 * @param parentId  上级部门主键
 * @param name      部门名称
 * @param code      部门编码，仅支持大写字母、数字和下划线
 * @param sortOrder 同级排序序号
 * @param status    部门状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDepartmentRequest {
    /** 上级部门主键。 */
    @NotNull(message = "上级部门不能为空")
    private Long parentId;

    /** 部门名称。 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称长度不能超过64个字符")
    private String name;

    /** 部门编码，仅支持大写字母、数字和下划线。 */
    @NotBlank(message = "部门编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "部门编码必须使用大写字母、数字和下划线")
    @Size(max = 64, message = "部门编码长度不能超过64个字符")
    private String code;

    /** 同级排序序号。 */
    private Integer sortOrder;

    /** 部门状态。 */
    @NotNull(message = "部门状态不能为空")
    private EnableStatus status;
}