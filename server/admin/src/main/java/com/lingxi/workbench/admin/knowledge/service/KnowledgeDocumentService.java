package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.framework.convention.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档上传与管理业务。
 */
public interface KnowledgeDocumentService {
    /**
     * 分页查询知识库文档及处理状态。
     */
    PageResult<KnowledgeDocumentVO> page(KnowledgeDocumentPageQuery query);

    /**
     * 上传文档文件，仅保存原文件和元数据。
     */
    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long operatorId);

    /**
     * 录入文本内容，仅保存原文件和元数据。
     */
    KnowledgeDocumentVO createText(CreateTextDocumentRequest request, Long operatorId);

    /**
     * 获取原始文档的临时预览地址。
     */
    String previewUrl(Long id);

    /**
     * 删除非处理中的文档及原始文件。
     */
    void delete(Long id, Long operatorId);
}
