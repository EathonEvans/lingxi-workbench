package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
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

    /** 主键 */
    private Long id;
    /** 会话主键 */
    private Long conversationId;
    /** 用户主键 */
    private Long userId;
    /** 锁定的应用发布版本主键，人工交互期间防止版本漂移 */
    private Long applicationVersionId;
    /** 触发本次交互的 Assistant 消息主键 */
    private Long assistantMessageId;
    /** 所属执行 Run 标识 */
    private String runId;
    /** 关联的不可变执行检查点主键 */
    private Long checkpointId;
    /** 人工响应后恢复执行的能力编码 */
    private String resumeCapabilityCode;
    /** 编排 Agent（Plan Executor）主键 */
    private Long orchestratorAgentId;
    /** 编排 Agent 锁定的版本主键 */
    private Long orchestratorAgentVersionId;
    /** 交互类型：信息澄清 / 动作审批 / 计划审阅 */
    private InteractionSessionType interactionType;
    /** 会话状态：待处理 / 已解决 / 已拒绝 / 已取消 / 已过期 / 已完成 / 失败 */
    private InteractionSessionStatus status;
    /** 澄清轮次，从 1 开始 */
    private Integer roundNo;
    /** 乐观锁修订号，并发更新会话时校验 */
    private Integer revision;
    /** 待审批动作指纹，响应时校验防止篡改 */
    private String approvalFingerprint;
    /** 人工决策内容 */
    private String decision;
    /** 决策人主键 */
    private Long decidedBy;
    /** 决策时间 */
    private OffsetDateTime decidedAt;
    /** 幂等键，防止重复创建会话 */
    private String idempotencyKey;
    /** 交互上下文快照（JSONB），含澄清内容、候选能力、资源范围等 */
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private InteractionSessionContext context;
    /** 过期时间，超时未响应进入过期终态 */
    private OffsetDateTime expiresAt;
}
