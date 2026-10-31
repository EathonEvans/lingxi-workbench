package com.lingxi.workbench.notification.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知的结构化跳转参数，前端点击通知后据此定位会话与消息。
 *
 * <p>不同通知类型使用的字段不同：HITL 待确认使用 conversationId、messageId、interactionId；
 * 后续定时任务结果类型扩展 triggerId 等字段，全部可空。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayloadBO {

    /** 关联会话主键。 */
    private Long conversationId;

    /** 关联 Assistant 消息主键。 */
    private Long messageId;

    /** 关联 HITL 交互会话主键。 */
    private Long interactionId;

    /** 关联定时触发器主键。 */
    private Long triggerId;
}
