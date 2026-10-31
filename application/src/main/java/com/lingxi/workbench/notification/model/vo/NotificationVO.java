package com.lingxi.workbench.notification.model.vo;

import com.lingxi.workbench.notification.model.bo.NotificationPayloadBO;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 站内通知响应对象。 */
@Getter
@Setter
public class NotificationVO {

    /** 通知主键（字符串形式，避免前端精度丢失）。 */
    private String id;

    /** 通知类型编码。 */
    private String type;

    /** 通知标题。 */
    private String title;

    /** 通知摘要。 */
    private String content;

    /** 结构化跳转参数。 */
    private NotificationPayloadBO payload;

    /** 是否已读。 */
    private Boolean read;

    /** 阅读时间。 */
    private OffsetDateTime readAt;

    /** 创建时间。 */
    private OffsetDateTime createdAt;
}
