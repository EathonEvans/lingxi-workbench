package com.lingxi.workbench.admin.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.admin.workflow.model.config.WorkflowCanvasState;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

/** Workflow 可编辑草稿数据库实体。definition 以 JSONB 存储 WorkflowDefinition。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_workflow_draft", autoResultMap = true)
public class WorkflowDraftEntity extends TimeEntity {
    private Long id;
    private Long workflowId;
    private Long baseVersionId;
    private Integer schemaVersion;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private WorkflowDefinition definition;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private WorkflowCanvasState canvasState;
    private Long revision;
    private String changeNote;
    private Long createdBy;
    private Long updatedBy;
}
