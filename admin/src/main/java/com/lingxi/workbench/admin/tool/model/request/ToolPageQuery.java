package com.lingxi.workbench.admin.tool.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Tool 管理分页查询参数。 */
@Getter
@Setter
public class ToolPageQuery extends PageQuery {

    /** 名称或编码关键字。 */
    @Size(max = 64, message = "搜索关键字不能超过 64 个字符")
    private String keyword;

    /** Tool 来源类型。 */
    private ToolSourceType sourceType;

    /** 启停状态。 */
    private EnableStatus status;
}
