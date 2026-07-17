package com.lingxi.workbench.admin.prompt.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Prompt 渲染预览请求。 */
public record PreviewPromptRequest(
        @NotBlank(message = "请输入 Prompt 内容")
        @Size(max = 32768, message = "Prompt 内容不能超过32768个字符")
        String promptContent,
        @Size(max = 50, message = "Prompt 变量不能超过50个")
        List<@Valid PromptVariableRequest> variables,
        Map<String, String> values) {

    public PreviewPromptRequest {
        variables = variables == null ? List.of() : List.copyOf(variables);
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
