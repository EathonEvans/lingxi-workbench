package com.lingxi.workbench.observability.model.entity;

import com.lingxi.workbench.framework.json.JSONObject;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.observability.common.enums.AiObservationEventType;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.common.enums.AiObservationStatus;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/**
 * Agent 关键链路事件的追加式数据库实体。
 */
@Data
@TableName(value = "lingxi_core.ai_observability_event", autoResultMap = true)
public class AiObservationEventEntity {

    /** 观测事件主键由 MyBatis-Plus 分配，避免数据库表与运行时生成策略脱节。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private AiObservationEventType eventType;
    private AiObservationStage stage;
    /**
     * Application 逻辑外键。
     */
    private Long applicationId;
    /**
     * Planner 运行标识；直接 ReAct 路径允许为空。
     */
    private String runId;
    private String modelCode;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private AiObservationStatus status;
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject metadata;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
