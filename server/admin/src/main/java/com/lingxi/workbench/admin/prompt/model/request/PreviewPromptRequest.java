package com.lingxi.workbench.admin.prompt.model.request;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Prompt渲染预览请求。
 *
 * @param promptContent Prompt正文内容
 * @param variables     变量声明列表
 * @param values        按命名空间组织的测试变量值
 */
public record PreviewPromptRequest(

        @NotBlank(message = "请输入Prompt内容")
        @Size(max = 32768, message = "Prompt内容不能超过32768个字符")
        String promptContent,

        @Size(max = 50, message = "Prompt变量不能超过50个")
        List<@Valid PromptVariableRequest> variables,

        Map<String, Object> values) {

    public PreviewPromptRequest {
        // 防御性拷贝，避免外部修改影响内部状态
        variables = CollUtil.isEmpty(variables) ? List.of() : List.copyOf(variables);
        values = MapUtil.isEmpty(values) ? Map.of() : Map.copyOf(values);
    }
}
