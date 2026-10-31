"use client";

import { ActionIcon } from "@lobehub/ui";
import { Badge, Empty, Popover, Spin } from "antd";
import { Bell } from "lucide-react";
import { useCallback, useState } from "react";
import type { AuthSession } from "@/lib/auth";
import {
  listNotifications,
  markNotificationsRead,
  type WorkbenchNotification,
} from "@/lib/notifications";
import styles from "./notification-center.module.css";

type NotificationCenterProps = {
  /** 点击带会话跳转的通知时触发，参数为通知 payload 中的会话主键。 */
  onOpenConversation?: (conversationId: string) => void;
  /** 通知标记已读后回调，用于刷新未读角标。 */
  onMarkedRead?: () => void;
  /** 打开面板或标记已读失败时的错误提示回调。 */
  onRequestError: (error: unknown, fallback: string) => void;
  session: AuthSession;
  unreadCount: number;
};

function formatNotificationTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  if (sameDay) {
    return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  }
  return date.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
}

/** 顶栏通知中心：铃铛入口 + 未读角标 + 通知列表面板。 */
export default function NotificationCenter({
  onOpenConversation,
  onMarkedRead,
  onRequestError,
  session,
  unreadCount,
}: NotificationCenterProps) {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<WorkbenchNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listNotifications(session, { current: 1, size: 20 });
      setNotifications(page.records);
      setLoaded(true);
    } catch (error) {
      onRequestError(error, "查询通知失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }, [onRequestError, session]);

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      setOpen(nextOpen);
      if (nextOpen) void loadNotifications();
    },
    [loadNotifications],
  );

  const handleSelect = useCallback(
    async (notification: WorkbenchNotification) => {
      setOpen(false);
      try {
        if (!notification.read) {
          await markNotificationsRead([notification.id], session);
          onMarkedRead?.();
        }
      } catch (error) {
        onRequestError(error, "标记已读失败，请稍后重试");
      }
      const conversationId = notification.payload?.conversationId;
      if (conversationId) onOpenConversation?.(conversationId);
    },
    [onMarkedRead, onOpenConversation, onRequestError, session],
  );

  const content = (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <strong>通知</strong>
      </div>
      <div className={styles.panelBody}>
        {loading && (
          <div className={styles.panelLoading}>
            <Spin />
          </div>
        )}
        {!loading && loaded && notifications.length === 0 && (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" />
        )}
        {!loading &&
          notifications.map((notification) => (
            <button
              className={`${styles.notification} ${notification.read ? "" : styles.notificationUnread}`}
              key={notification.id}
              onClick={() => void handleSelect(notification)}
              type="button"
            >
              <span className={styles.notificationTitle}>
                {!notification.read && <i className={styles.unreadDot} aria-hidden="true" />}
                {notification.title}
              </span>
              {notification.content && (
                <span className={styles.notificationContent}>{notification.content}</span>
              )}
              <span className={styles.notificationTime}>
                {formatNotificationTime(notification.createdAt)}
              </span>
            </button>
          ))}
      </div>
    </div>
  );

  return (
    <Popover
      arrow={false}
      content={content}
      onOpenChange={handleOpenChange}
      open={open}
      placement="bottomRight"
      trigger="click"
    >
      <Badge count={unreadCount} size="small">
        <ActionIcon
          aria-label="通知中心"
          icon={Bell}
          size="small"
          title="通知中心"
        />
      </Badge>
    </Popover>
  );
}
