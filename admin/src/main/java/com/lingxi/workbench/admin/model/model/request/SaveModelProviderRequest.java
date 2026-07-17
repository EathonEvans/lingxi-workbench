package com.lingxi.workbench.admin.model.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增或修改模型供应商请求。
 */
public record SaveModelProviderRequest(
        /** 供应商名称。 */
        @NotBlank(message = "请输入供应商名称")
        @Size(max = 64, message = "供应商名称不能超过 64 个字符")
        String name,
        /** 供应商编码，仅支持小写字母、数字、中划线和下划线。 */
        @NotBlank(message = "请输入供应商编码")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "供应商编码仅支持小写字母、数字、中划线和下划线")
        String code,
        /** 接口基础地址。 */
        @NotBlank(message = "请输入接口基础地址")
        @Size(max = 512, message = "接口基础地址不能超过 512 个字符")
        String baseUrl,
        /** 供应商状态。 */
        @NotNull(message = "请选择供应商状态")
        EnableStatus status,
        /** 备注说明。 */
        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark
) {
}
