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

/**
 * 跨消息恢复的意图澄清会话持久化实体，映射到 {@code lingxi_core.ai_intent_routing_session} 表。
 *
 * <p>核心设计原则：</p>
 * <ol>
 *   <li>会话创建时锁定 {@code versionId} 和 {@code intentTreeVersionId}，
 *       避免澄清过程中任一资源发布新版本造成选项或执行目标漂移。</li>
 *   <li>会话以 PENDING 状态存在，仅在状态变为 RESOLVED / COMPLETED / CANCELLED / EXPIRED / FAILED
 *       后才不再拦截后续消息。</li>
 *   <li>同一次对话（conversationId + userId + versionId）中同一时间
 *       最多只有一个 PENDING 状态的会话。</li>
 *   <li>过期时间（expiresAt）默认为创建后 10 分钟，超时后状态变为 EXPIRED，
 *       下一条消息重新识别意图。</li>
 *   <li>澄清轮次上限为 2，超限后状态变为 FAILED，触发兜底文案。</li>
 * </ol>
 *
 * <p>关联字段均为带索引的逻辑外键，遵循项目约定不使用数据库外键约束。</p>
 */
@Data
@TableName(value = "lingxi_core.ai_intent_routing_session", autoResultMap = true)
public class IntentRoutingSessionEntity {

    /** 主键，自增 */
    private Long id;

    /** 所属会话 ID（逻辑外键 → ai_conversation.id） */
    private Long conversationId;

    /** 用户 ID（逻辑外键 → sys_user.id） */
    private Long userId;

    /** 澄清开始时锁定的 ApplicationVersion ID（逻辑外键 → ai_application_version.id） */
    private Long applicationVersionId;

    /** 澄清开始时锁定的 IntentTreeVersion ID（逻辑外键 → ai_intent_tree_version.id） */
    private Long intentTreeVersionId;

    /** 会话状态 */
    private IntentRoutingSessionStatus status;

    /** 澄清类型：歧义消解 或 缺失槽位 */
    private IntentClarificationType clarificationType;

    /** 当前澄清轮次，从 1 开始，上限为 2 */
    private Integer roundNo;

    /** 持久化的运行时上下文（JSONB），包含候选编码、目标意图、槽位进度 */
    @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private IntentClarificationContext context;

    /** 过期时间，PENDING 状态下超过此时间后状态变为 EXPIRED */
    private OffsetDateTime expiresAt;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 最后更新时间 */
    private OffsetDateTime updatedAt;
}