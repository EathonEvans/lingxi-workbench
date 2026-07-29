package com.lingxi.workbench.admin.agent.model.request;

import com.lingxi.workbench.admin.agent.common.enums.AgentType;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Agent 分页查询参数。 */
@Getter
@Setter
public class AgentPageQuery extends PageQuery {
    @Size(max = 64, message = "查询关键字不能超过64个字符")
    private String keyword;
    private AgentType agentType;
    private EnableStatus status;
}
