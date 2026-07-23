package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Chunk分页查询参数。 */
@Getter
@Setter
public class KnowledgeChunkPageQuery extends PageQuery {
    @NotNull(message = "任务ID不能为空")
    private Long taskId;
}
