package com.lingxi.workbench.workflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.workflow.common.enums.WorkflowNodeRunStatus;
import com.lingxi.workbench.workflow.common.enums.WorkflowRunEventType;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import lombok.Data;

import java.time.OffsetDateTime;

/** 可恢复且不含敏感执行内容的 Workflow 时间线事件。 */
@Data
@TableName("lingxi_core.ai_workflow_run_event")
public class WorkflowRunEventEntity {

    private Long id;
    private String runId;
    private Long sequenceNo;
    private WorkflowRunEventType eventType;
    private String nodeId;
    private WorkflowNodeType nodeType;
    private String displayName;
    private WorkflowNodeRunStatus nodeStatus;
    private String message;
    private IntentClassifierNodeStatus decisionStatus;
    private String reasonCode;
    private Long modelInstanceId;
    private Long promptVersionId;
    private Integer modelCalls;
    private Integer retryCount;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private OffsetDateTime occurredAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
