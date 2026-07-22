package com.lingxi.workbench.admin.prompt.model.request;

import cn.hutool.core.collection.CollUtil;
import com.lingxi.workbench.admin.prompt.common.enums.PromptRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新增或修改Prompt请求。 */
public record SavePromptRequest(
        /** Prompt名称。 */
        @NotBlank(message = "请输入Prompt名称")
        @Size(max = 64, message = "Prompt名称不能超过64个字符")
        String name,
        /** Prompt编码，仅支持小写字母、数字、中划线和下划线。 */
        @NotBlank(message = "请输入Prompt编码")
        @Pattern(
                regexp = "^[a-z][a-z0-9_-]{1,63}$",
                message = "Prompt编码仅支持小写字母、数字、中划线和下划线")
        String code,
        /** Prompt角色。 */
        @NotNull(message = "请选择Prompt角色") PromptRole role,
        /** Prompt描述说明。 */
        @Size(max = 500, message = "Prompt描述不能超过500个字符") String description,
        /** Prompt正文内容。 */
        @NotBlank(message = "请输入Prompt内容")
        @Size(max = 32768, message = "Prompt内容不能超过32768个字符")
        String promptContent,
        /** 变量声明列表。 */
        @Size(max = 50, message = "Prompt变量不能超过50个")
        List<@Valid PromptVariableRequest> variables,
        /** 版本变更说明。 */
        @Size(max = 255, message = "版本说明不能超过255个字符") String changeNote) {

    public SavePromptRequest {
        // 防御性拷贝，避免外部修改影响内部状态
        variables = CollUtil.isEmpty(variables) ? List.of() : List.copyOf(variables);
    }
}
