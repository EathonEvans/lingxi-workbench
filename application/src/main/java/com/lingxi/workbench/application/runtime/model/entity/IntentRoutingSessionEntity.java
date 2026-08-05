package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 跨消息恢复的意图澄清事实。 */
@Data
@TableName(value = "lingxi_core.ai_intent_routing_session", autoResultMap = true)
public class IntentRoutingSessionEntity {
    private Long id;
    private Long conversationId;
    private Long userId;
    private Long applicationVersionId;
    private Long intentTreeVersionId;
    private IntentRoutingSessionStatus status;
    private IntentClarificationType clarificationType;
    private Integer roundNo;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentClarificationContext context;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
