package com.lingxi.workbench.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.workflow.model.WorkflowNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import lombok.Data;

import java.time.OffsetDateTime;

/** Workflow 节点一次终态尝试，不保存节点输入和模型原文。 */
@Data
@TableName("lingxi_core.ai_workflow_node_attempt")
public class WorkflowNodeAttemptEntity {

    private Long id;
    private String runId;
    private String nodeId;
    private WorkflowNodeType nodeType;
    private Integer attemptNo;
    private WorkflowNodeStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Long durationMillis;
    private String errorCode;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
