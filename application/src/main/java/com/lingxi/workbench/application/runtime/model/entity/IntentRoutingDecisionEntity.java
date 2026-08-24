package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.model.IntentRoutingDecisionCandidate;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionSnapshot;
import com.lingxi.workbench.framework.persistence.handler.Fastjson2JsonbTypeHandler;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 每次意图路由调用产生的不可变决策事实。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "lingxi_core.ai_intent_routing_decision", autoResultMap = true)
public class IntentRoutingDecisionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private Long userId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long applicationId;
    private Long applicationVersionId;
    private Long intentTreeId;
    private Long intentTreeVersionId;
    private Long embeddingModelInstanceId;
    private Long chatModelInstanceId;
    private Long promptVersionId;
    private IntentRoutingAction action;
    private IntentRoutingReason reason;
    private String selectedIntentCode;
    private String capabilityCode;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<IntentRoutingDecisionCandidate> candidateEvidence;
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentSemanticDecisionSnapshot semanticDecision;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer modelCalls;
    private Integer retryCount;
    private Long durationMillis;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
