package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 知识库文档分页查询条件。 */
@Getter
@Setter
public class KnowledgeDocumentPageQuery extends PageQuery {
    @NotNull(message = "知识库ID不能为空")
    private Long knowledgeBaseId;
    private String keyword;
    private KnowledgeDocumentStatus status;
}
