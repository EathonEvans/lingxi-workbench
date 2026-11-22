package com.lingxi.workbench.admin.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;

/**
 * 知识库数据库实体。
 *
 * <p>每个知识库固定绑定一个 Embedding 模型实例，所有文档的向量化均使用该模型。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_knowledge_base")
public class KnowledgeBaseEntity extends TimeEntity {
    /** 知识库主键。 */
    private Long id;
    /** 知识库名称。 */
    private String name;
    /** 知识库编码，全局唯一。 */
    private String code;
    /** 知识库描述。 */
    private String description;
    /** 知识库固定使用的 Embedding 模型实例 ID。 */
    private Long embeddingModelInstanceId;
    /** 当前生效的不可变知识索引版本 ID。 */
    private Long publishedIndexVersionId;
    /** 启用状态。 */
    private EnableStatus status;
    /** 应用端检索的实时访问范围。 */
    private KnowledgeAccessMode accessMode;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}
