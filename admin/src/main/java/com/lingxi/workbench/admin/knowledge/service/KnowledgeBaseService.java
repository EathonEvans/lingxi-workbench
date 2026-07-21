package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeBaseRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.framework.convention.PageResult;

/**
 * 知识库管理业务。
 */
public interface KnowledgeBaseService {
    PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query);

    KnowledgeBaseVO detail(Long id);

    KnowledgeBaseVO save(Long id, SaveKnowledgeBaseRequest request, Long operatorId);

    void delete(Long id, Long operatorId);
}
