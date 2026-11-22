package com.lingxi.workbench.admin.prompt.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Prompt分页查询参数。 */
@Getter
@Setter
public class PromptPageQuery extends PageQuery {

    /** Prompt名称，支持模糊匹配。 */
    @Size(max = 64, message = "Prompt名称不能超过64个字符")
    private String name;

    /** Prompt编码，支持模糊匹配。 */
    @Size(max = 64, message = "Prompt编码不能超过64个字符")
    private String code;

    /** Prompt角色筛选。 */
    private PromptRole role;

    /** 启用状态筛选。 */
    private EnableStatus status;
}
