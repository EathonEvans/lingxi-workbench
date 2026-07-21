package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** 知识库分页查询条件。 */
@Getter
@Setter
public class KnowledgeBasePageQuery extends PageQuery {
    private String keyword;
    private EnableStatus status;
}
