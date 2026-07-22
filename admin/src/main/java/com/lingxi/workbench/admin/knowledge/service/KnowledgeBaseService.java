package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeBaseRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.framework.convention.PageResult;

/**
 * 知识库管理业务。
 */
public interface KnowledgeBaseService {
    /** 分页查询知识库。 */
    PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query);

    /** 查询知识库详情。 */
    KnowledgeBaseVO detail(Long id);

    /** 新增或修改知识库，id为空时新增否则修改。 */
    KnowledgeBaseVO save(Long id, SaveKnowledgeBaseRequest request, Long operatorId);

    /** 删除无文档的知识库。 */
    void delete(Long id, Long operatorId);
}
