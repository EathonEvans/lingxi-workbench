package com.lingxi.workbench.admin.intent.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

/** 意图树可编辑草稿，使用 revision 防止并发覆盖。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_intent_tree_draft", autoResultMap = true)
public class IntentTreeDraftEntity extends TimeEntity {

    private Long id;
    private Long intentTreeId;
    private Long baseVersionId;
    private Integer schemaVersion;
    private Long embeddingModelInstanceId;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentTreeDefinition definition;
    private Long revision;
    private String changeNote;
    private Long createdBy;
    private Long updatedBy;
}
