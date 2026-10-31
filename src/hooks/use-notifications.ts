"use client";

import { useCallback, useEffect, useState } from "react";
import type { AuthSession } from "@/lib/auth";
import { getUnreadNotificationCount } from "@/lib/notifications";

type UseNotificationsOptions = {
  session: AuthSession | null;
};

type UseNotificationsReturn = {
  unreadCount: number;
  refreshUnreadCount: () => void;
};

const POLL_INTERVAL_MS = 30_000;

/**
 * 未读通知数轮询。
 *
 * <p>未读数是辅助信息：页面隐藏时暂停轮询，重新获得焦点时立即刷新一次；
 * 单次轮询失败静默跳过本轮，不打断用户，也不在后台标签页反复弹出错误。</p>
 */
export function useNotifications({ session }: UseNotificationsOptions): UseNotificationsReturn {
  const [unreadCount, setUnreadCount] = useState(0);

  const refreshUnreadCount = useCallback(() => {
    if (!session) return;
    getUnreadNotificationCount(session)
      .then((count) => setUnreadCount(count))
      .catch(() => undefined);
  }, [session]);

  useEffect(() => {
    if (!session) return;
    refreshUnreadCount();
    const timer = setInterval(() => {
      if (typeof document !== "undefined" && document.visibilityState !== "visible") return;
      refreshUnreadCount();
    }, POLL_INTERVAL_MS);
    const handleFocus = () => refreshUnreadCount();
    window.addEventListener("focus", handleFocus);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", handleFocus);
    };
  }, [refreshUnreadCount, session]);

  return { refreshUnreadCount, unreadCount };
}
