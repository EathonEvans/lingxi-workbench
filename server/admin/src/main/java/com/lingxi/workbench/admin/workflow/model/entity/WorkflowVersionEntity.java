package com.lingxi.workbench.admin.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.admin.workflow.model.config.WorkflowCanvasState;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** Workflow 不可变发布版本数据库实体。 */
@Data
@TableName(value = "lingxi_core.ai_workflow_version", autoResultMap = true)
public class WorkflowVersionEntity {
    private Long id;
    private Long workflowId;
    private Integer versionNo;
    private String versionName;
    private Integer schemaVersion;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private WorkflowDefinition definitionSnapshot;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private WorkflowCanvasState canvasSnapshot;
    private String snapshotChecksum;
    private String changeNote;
    private Long publishedBy;
    private OffsetDateTime publishedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
