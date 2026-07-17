package com.lingxi.workbench.admin.prompt.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Prompt 分页查询参数。 */
@Getter
@Setter
public class PromptPageQuery extends PageQuery {

    @Size(max = 64, message = "Prompt 名称不能超过64个字符")
    private String name;

    @Size(max = 64, message = "Prompt 编码不能超过64个字符")
    private String code;

    private EnableStatus status;
}
