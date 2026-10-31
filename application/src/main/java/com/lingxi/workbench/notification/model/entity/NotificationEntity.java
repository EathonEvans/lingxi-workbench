package com.lingxi.workbench.notification.model.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.framework.persistence.handler.GsonJsonbTypeHandler;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.notification.common.enums.NotificationType;
import com.lingxi.workbench.notification.model.bo.NotificationPayloadBO;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

/** 站内通知实体。 */
@Getter
@Setter
@TableName(value = "lingxi_core.ai_notification", autoResultMap = true)
public class NotificationEntity extends TimeEntity {

    private Long id;

    /** 归属用户主键，通知只投递给本人。 */
    private Long userId;

    /** 通知类型。 */
    @EnumValue
    private NotificationType type;

    /** 通知标题。 */
    private String title;

    /** 通知摘要，不承载敏感业务内容。 */
    private String content;

    /** 结构化跳转参数。 */
    @TableField(typeHandler = GsonJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private NotificationPayloadBO payload;

    /** 幂等去重键，如 hitl:{interactionId}；为空的记录不去重。 */
    private String dedupKey;

    /** 阅读时间；为空表示未读。 */
    private OffsetDateTime readAt;

    /** 软删除时间。 */
    private OffsetDateTime deletedAt;
}
