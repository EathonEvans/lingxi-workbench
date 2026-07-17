package com.lingxi.workbench.admin.prompt.model.request;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableOverflowStrategy;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableScope;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableSource;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Prompt变量声明。 */
public record PromptVariableRequest(
        /** 变量名称。 */
        @NotBlank(message = "变量名称不能为空")
        @Pattern(
                regexp = "^(system|app|agent|user|input|session|workflow|node|tools|knowledge)(\\.[a-z][a-z0-9_]*)+$",
                message = "变量名称必须使用合法命名空间和小写英文路径")
        String name,
        /** 变量展示名称。 */
        @NotBlank(message = "变量展示名称不能为空")
        @Size(max = 100, message = "变量展示名称不能超过100个字符")
        String displayName,
        /** 变量数据类型。 */
        @NotNull(message = "请选择变量类型") PromptVariableType type,
        /** 变量作用域。 */
        @NotNull(message = "请选择变量作用域") PromptVariableScope scope,
        /** 变量来源。 */
        @NotNull(message = "请选择变量来源") PromptVariableSource source,
        /** 是否必填。 */
        boolean required,
        /** 变量默认值。 */
        Object defaultValue,
        /** 变量说明。 */
        @Size(max = 500, message = "变量说明不能超过500个字符") String description,
        /** 测试渲染示例值。 */
        Object example,
        /** 最大字符数。 */
        @Min(value = 1, message = "变量最大字符数必须大于0")
        @Max(value = 1000000, message = "变量最大字符数不能超过1000000")
        Integer maxLength,
        /** 数组最大元素数。 */
        @Min(value = 1, message = "变量最大元素数必须大于0")
        @Max(value = 10000, message = "变量最大元素数不能超过10000")
        Integer maxItems,
        /** Token预算裁剪优先级。 */
        @NotNull(message = "请设置变量优先级")
        @Min(value = 0, message = "变量优先级不能小于0")
        @Max(value = 100, message = "变量优先级不能大于100")
        Integer priority,
        /** 内容超限处理策略。 */
        @NotNull(message = "请选择变量超限策略")
        PromptVariableOverflowStrategy overflowStrategy,
        /** 是否包含敏感信息。 */
        boolean sensitive,
        /** 是否允许最终用户编辑。 */
        boolean userEditable) {}
