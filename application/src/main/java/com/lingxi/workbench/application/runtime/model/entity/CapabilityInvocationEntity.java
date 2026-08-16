package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 一次精确 Tool 调用意图及其实际执行结果索引。 */
@Data
@TableName(value = "lingxi_core.ai_capability_invocation", autoResultMap = true)
public class CapabilityInvocationEntity {

    private Long id;
    private String runId;
    private Long checkpointId;
    private Long interactionId;
    private Long assistantMessageId;
    private String toolCode;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private com.alibaba.fastjson2.JSONObject toolInput;
    private String resourceScope;
    private String approvalFingerprint;
    private CapabilityInvocationStatus status;
    private String idempotencyKey;
    private String resultDigest;
    private String errorCode;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
