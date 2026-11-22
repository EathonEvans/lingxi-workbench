package com.lingxi.workbench.admin.workflow.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Workflow 分页查询参数。 */
@Getter
@Setter
public class WorkflowPageQuery extends PageQuery {
    @Size(max = 64, message = "查询关键字不能超过64个字符")
    private String keyword;
    private EnableStatus status;
}
