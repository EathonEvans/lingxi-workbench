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
    /**
     * 分页查询知识库文档及处理状态。
     */
    PageResult<KnowledgeDocumentVO> page(KnowledgeDocumentPageQuery query);

    /**
     * 上传文档文件并创建异步解析任务。
     */
    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long operatorId);

    /**
     * 录入文本内容并创建异步解析任务。
     */
    KnowledgeDocumentVO createText(CreateTextDocumentRequest request, Long operatorId);

    /**
     * 查询已解析文档的纯文本正文。
     */
    KnowledgeDocumentContentVO content(Long id);

    /**
     * 重试失败的文档解析任务。
     */
    void retry(Long id, Long operatorId);

    /**
     * 删除非解析中的文档及原始文件。
     */
    void delete(Long id, Long operatorId);
}
