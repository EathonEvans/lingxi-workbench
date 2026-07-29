package com.lingxi.workbench.admin.prompt.model.vo;

import java.util.List;

/**
 * 运行时变量目录。
 *
 * @param groups 后端已经完成排序和归属计算的变量分组
 */
public record AvailablePromptVariableCatalogVO(
        List<AvailablePromptVariableGroupVO> groups) {

    /** 对分组列表做防御性复制。 */
    public AvailablePromptVariableCatalogVO {
        groups = List.copyOf(groups);
    }
}
