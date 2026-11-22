package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档及其处理配置数据库实体。
 *
 * <p>每个文档记录一份原始文件或录入文本，以及其分块处理状态与配置。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_document")
public class KnowledgeDocumentEntity extends TimeEntity {
    /** 文档主键。 */
    private Long id;
    /** 所属知识库 ID。 */
    private Long knowledgeBaseId;
    /** 文档名称。 */
    private String name;
    /** 文档来源类型：文件上传或文本录入。 */
    private KnowledgeDocumentSourceType sourceType;
    /** 原始文件名。 */
    private String fileName;
    /** 文件类型扩展名。 */
    private String fileType;
    /** 文件大小（字节）。 */
    private Long fileSize;
    /** 对象存储中的文件 Key。 */
    private String objectKey;
    /** 文件内容的 SHA-256 哈希，用于知识库内去重。 */
    private String contentHash;
    /** 文档处理状态。 */
    private KnowledgeDocumentStatus status;
    /** 文档总页数，PDF 等格式由 Tika 提取。 */
    private Integer pageCount;
    /** 文档级分块策略，重新执行时默认沿用上次配置。 */
    private KnowledgeChunkStrategy chunkStrategy;
    /** Chunk 目标 Token 数。 */
    private Integer chunkSize;
    /** Chunk 重叠 Token 数。 */
    private Integer chunkOverlap;
    /** 当前生效版本的分块数量，供列表统计使用。 */
    private Integer chunkCount;
    /** 处理失败时的错误码。 */
    private String errorCode;
    /** 处理失败时的错误描述。 */
    private String errorMessage;
    /** 当前已完成向量化并正式生效的分块任务 ID。 */
    private Long activeChunkTaskId;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}
