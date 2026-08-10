package com.lingxi.workbench.observability.model.entity;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
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
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JSONObject metadata;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
