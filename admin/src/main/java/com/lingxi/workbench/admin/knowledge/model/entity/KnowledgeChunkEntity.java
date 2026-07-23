package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

/** 已持久化的文档Chunk。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_knowledge_chunk", autoResultMap = true)
public class KnowledgeChunkEntity extends TimeEntity {
    private Long id;
    private Long taskId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Integer chunkNo;
    private String content;
    private String contentHash;
    private Integer charCount;
    private Integer tokenCount;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private KnowledgeChunkMetadata metadata;
    private Boolean enabled;
    private Long createdBy;
    private Long updatedBy;
}
