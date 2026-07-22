package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;

/**
 * 知识库文档手动分块业务。
 */
public interface KnowledgeChunkService {

    /**
     * 基于已解析正文执行分块并返回确认结果，当前阶段不持久化Chunk。
     *
     * @param documentId 文档ID
     * @param request    分块策略和参数
     * @param operatorId 操作人ID
     * @return 分块结果
     */
    KnowledgeChunkResultVO execute(Long documentId, ExecuteKnowledgeChunkRequest request, Long operatorId);
}
