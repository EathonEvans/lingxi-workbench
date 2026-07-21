package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentContentVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.framework.convention.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档管理与解析业务。
 */
public interface KnowledgeDocumentService {
    PageResult<KnowledgeDocumentVO> page(KnowledgeDocumentPageQuery query);

    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long operatorId);

    KnowledgeDocumentVO createText(CreateTextDocumentRequest request, Long operatorId);

    KnowledgeDocumentContentVO content(Long id);

    void retry(Long id, Long operatorId);

    void delete(Long id, Long operatorId);
}
