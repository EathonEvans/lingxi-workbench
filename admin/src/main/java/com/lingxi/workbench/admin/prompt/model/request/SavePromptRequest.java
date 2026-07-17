package com.lingxi.workbench.admin.prompt.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新增或修改 Prompt 请求。 */
public record SavePromptRequest(
        @NotBlank(message = "请输入 Prompt 名称")
        @Size(max = 64, message = "Prompt 名称不能超过64个字符")
        String name,
        @NotBlank(message = "请输入 Prompt 编码")
        @Pattern(
                regexp = "^[a-z][a-z0-9_-]{1,63}$",
                message = "Prompt 编码仅支持小写字母、数字、中划线和下划线")
        String code,
        @Size(max = 500, message = "Prompt 描述不能超过500个字符") String description,
        @NotBlank(message = "请输入 Prompt 内容")
        @Size(max = 32768, message = "Prompt 内容不能超过32768个字符")
        String promptContent,
        @Size(max = 50, message = "Prompt 变量不能超过50个")
        List<@Valid PromptVariableRequest> variables,
        @Size(max = 255, message = "版本说明不能超过255个字符") String changeNote) {

    public SavePromptRequest {
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
