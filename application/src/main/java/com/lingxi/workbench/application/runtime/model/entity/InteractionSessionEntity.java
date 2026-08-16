package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** Planner 澄清与 HITL 决策使用的通用跨消息会话。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_interaction_session", autoResultMap = true)
public class InteractionSessionEntity extends TimeEntity {

    private Long id;
    private Long conversationId;
    private Long userId;
    private Long applicationVersionId;
    private Long assistantMessageId;
    private String runId;
    private Long checkpointId;
    private String resumeCapabilityCode;
    private Long orchestratorAgentId;
    private Long orchestratorAgentVersionId;
    private InteractionSessionType interactionType;
    private InteractionSessionStatus status;
    private Integer roundNo;
    private Integer revision;
    private String approvalFingerprint;
    private String decision;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String idempotencyKey;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private InteractionSessionContext context;
    private OffsetDateTime expiresAt;
}
