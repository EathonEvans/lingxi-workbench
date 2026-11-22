package com.lingxi.workbench.admin.evaluation.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.evaluation.common.enums.EvaluationRunStatus;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 一次锁定 ApplicationVersion 和 Langfuse 数据集快照的评测任务。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_evaluation_run", autoResultMap = true)
public class EvaluationRunEntity extends TimeEntity {

    private Long id;
    private String name;
    private Long applicationId;
    private Long applicationVersionId;
    private String datasetName;
    private OffsetDateTime datasetVersion;
    private EvaluationRunStatus status;
    private String executionToken;
    private Integer sampleCount;
    private Integer completedCount;
    private Integer passedCount;
    private Integer failedCount;
    private Boolean productionGatePassed;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject datasetSnapshot;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject reportSnapshot;
    private String errorMessage;
    private Long createdBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
