package com.lingxi.workbench.admin.prompt.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Prompt 变量声明。 */
public record PromptVariableRequest(
        @NotBlank(message = "变量名称不能为空")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$", message = "变量名称格式不正确")
        String name,
        boolean required,
        @Size(max = 200, message = "变量说明不能超过200个字符") String description,
        @Size(max = 2000, message = "变量默认值不能超过2000个字符") String defaultValue) {}
