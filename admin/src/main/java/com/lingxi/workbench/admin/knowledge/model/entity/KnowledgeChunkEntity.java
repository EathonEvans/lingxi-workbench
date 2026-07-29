package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

/**
 * 已持久化的文档 Chunk。
 *
 * <p>每个 Chunk 属于一个分块任务，包含分块正文、Token 统计和检索上下文元数据。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_knowledge_chunk", autoResultMap = true)
public class KnowledgeChunkEntity extends TimeEntity {
    /** Chunk 主键。 */
    private Long id;
    /** 所属分块任务 ID。 */
    private Long taskId;
    /** 所属知识库 ID。 */
    private Long knowledgeBaseId;
    /** 所属文档 ID。 */
    private Long documentId;
    /** 分块顺序号，从 1 开始。 */
    private Integer chunkNo;
    /** 分块正文内容。 */
    private String content;
    /** 正文内容的 SHA-256 哈希，用于检测人工修改。 */
    private String contentHash;
    /** 字符数量。 */
    private Integer charCount;
    /** Token 数量。 */
    private Integer tokenCount;
    /** 检索上下文元数据，使用 JSONB 存储。 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private KnowledgeChunkMetadata metadata;
    /** 是否启用，用于检索过滤。 */
    private Boolean enabled;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}
