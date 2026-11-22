package com.lingxi.workbench.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.workflow.common.enums.WorkflowNodeRunStatus;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeStatus;
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
    private WorkflowNodeRunStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Long durationMillis;
    private String errorCode;
    private IntentClassifierNodeStatus decisionStatus;
    private String reasonCode;
    private Long modelInstanceId;
    private Long promptVersionId;
    private Integer modelCalls;
    private Integer retryCount;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
