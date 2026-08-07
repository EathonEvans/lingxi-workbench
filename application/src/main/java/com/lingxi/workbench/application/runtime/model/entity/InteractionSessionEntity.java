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

/** Planner澄清和风险确认使用的通用跨消息会话。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "lingxi_core.ai_interaction_session", autoResultMap = true)
public class InteractionSessionEntity extends TimeEntity {

    private Long id;
    private Long conversationId;
    private Long userId;
    private Long applicationVersionId;
    private String runId;
    private InteractionSessionType interactionType;
    private InteractionSessionStatus status;
    private Integer roundNo;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private InteractionSessionContext context;
    private OffsetDateTime expiresAt;
}
