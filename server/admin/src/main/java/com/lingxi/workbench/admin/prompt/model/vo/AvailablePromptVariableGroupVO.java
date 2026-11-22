package com.lingxi.workbench.admin.prompt.model.vo;

import java.util.List;

/**
 * 管理端可直接渲染的运行时变量分组。
 *
 * @param code        稳定分组编码
 * @param displayName 中文展示名称
 * @param sort        展示顺序
 * @param variables   分组内的变量定义
 */
public record AvailablePromptVariableGroupVO(
        String code,
        String displayName,
        Integer sort,
        List<AvailablePromptVariableVO> variables) {

    /** 对变量列表做防御性复制。 */
    public AvailablePromptVariableGroupVO {
        variables = List.copyOf(variables);
    }
}
