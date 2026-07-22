package com.lingxi.workbench.admin.prompt.model.request;

import com.lingxi.workbench.admin.prompt.common.constants.PromptVariableConstants;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Prompt变量声明。 */
public record PromptVariableRequest(
        /** 变量名称。 */
        @NotBlank(message = "变量名称不能为空")
        @Pattern(
                regexp = PromptVariableConstants.VARIABLE_NAME_REGEXP,
                message = "变量名称必须使用合法命名空间和小写英文路径")
        String name,
        /** 变量数据类型。 */
        @NotNull(message = "请选择变量类型") PromptVariableType type,
        /** 是否必填。 */
        boolean required,
        /** 变量说明。 */
        @Size(max = 500, message = "变量说明不能超过500个字符") String description,
        /** 测试渲染示例值。 */
        Object example) {}
