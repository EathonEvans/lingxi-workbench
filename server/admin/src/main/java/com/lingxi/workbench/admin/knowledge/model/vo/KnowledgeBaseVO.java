package com.lingxi.workbench.admin.knowledge.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;
import java.time.OffsetDateTime;

/**
 * 知识库列表响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeBaseVO {
    /**
     * 知识库ID
     */
    private String id;
    /**
     * 知识库名称
     */
    private String name;
    /**
     * 知识库编码
     */
    private String code;
    /**
     * 知识库描述
     */
    private String description;
    /**
     * Embedding模型实例ID
     */
    private String embeddingModelInstanceId;
    /**
     * 知识库状态
     */
    private EnableStatus status;
    /** 应用端检索的实时访问范围。 */
    private KnowledgeAccessMode accessMode;
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
