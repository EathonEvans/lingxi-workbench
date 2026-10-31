package com.lingxi.workbench.notification.service;

import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.notification.model.bo.HitlPendingNotificationBO;
import com.lingxi.workbench.notification.model.bo.RunResultNotificationBO;
import com.lingxi.workbench.notification.model.vo.NotificationVO;

import java.util.List;

/** 站内通知服务：承载主动触达消息的写入、查询与已读推进。 */
public interface NotificationService {

    /**
     * 幂等创建 HITL 待确认通知。
     *
     * <p>以 hitl:{interactionId} 为去重键，交互重试或恢复重放不会产生重复通知。
     * 决策落库与过期关闭会同步将对应通知置为已读。</p>
     */
    void createHitlPending(HitlPendingNotificationBO bo);

    /**
     * 创建定时触发器主动运行的结果通知（成功、失败或自动暂停）。
     */
    void createRunResult(RunResultNotificationBO bo);

    /**
     * 将 HITL 交互对应的通知置为已读（去重键 hitl:{interactionId}）。
     */
    void markInteractionNotificationsRead(Long userId, Long interactionId);

    /**
     * 分页查询本人通知。
     */
    PageResult<NotificationVO> page(Long userId, PageQuery query);

    /**
     * 统计本人未读通知数。
     */
    long unreadCount(Long userId);

    /**
     * 批量将本人通知置为已读。
     */
    void markRead(Long userId, List<Long> ids);

    /**
     * 将本人全部未读通知置为已读。
     */
    void markAllRead(Long userId);
}
