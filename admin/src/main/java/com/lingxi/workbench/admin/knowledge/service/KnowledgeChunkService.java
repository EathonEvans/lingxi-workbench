package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeChunkPageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.UpdateKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.framework.convention.PageResult;

/**
 * 知识库文档异步处理业务。
 */
public interface KnowledgeChunkService {

    /**
     * 提交解析、分块、向量化和入库任务。
     *
     * @param documentId 文档ID
     * @param request    分块策略和参数
     * @param operatorId 操作人ID
     * @return 异步任务状态
     */
    KnowledgeChunkResultVO execute(Long documentId, ExecuteKnowledgeChunkRequest request, Long operatorId);

    /**
     * 查询任务状态。
     */
    KnowledgeChunkResultVO task(Long taskId);

    /**
     * 查询文档最近一次任务；尚未执行时返回null。
     */
    KnowledgeChunkResultVO latestTask(Long documentId);

    /**
     * 分页查询指定任务生成的Chunk。
     */
    PageResult<KnowledgeChunkVO> page(KnowledgeChunkPageQuery query);

    /**
     * 修订当前生效任务中的单个Chunk，并同步刷新向量。
     */
    KnowledgeChunkVO update(Long chunkId, UpdateKnowledgeChunkRequest request, Long operatorId);
}
