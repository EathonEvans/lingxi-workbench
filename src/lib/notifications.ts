import { API_BASE_URL, notifyUnauthorized, type AuthSession } from "./auth";

// ─── 类型定义 ───────────────────────────────────────────────

export type NotificationPayload = {
  conversationId?: string;
  interactionId?: string;
  messageId?: string;
};

export type WorkbenchNotification = {
  content?: string;
  createdAt: string;
  id: string;
  payload?: NotificationPayload;
  read: boolean;
  readAt?: string;
  title: string;
  type: "HITL_PENDING" | "RUN_RESULT";
};

export type PageResult<T> = {
  current: number;
  records: T[];
  size: number;
  total: number;
};

type ApiEnvelope<T> = {
  code: number;
  data?: T;
  msg?: string;
};

function authHeaders(session: AuthSession): Record<string, string> {
  return { [session.tokenName]: session.tokenValue };
}

async function readApiData<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (response.status === 401) {
    notifyUnauthorized();
  }
  let payload: ApiEnvelope<T> | null;
  try {
    payload = (await response.json()) as ApiEnvelope<T> | null;
  } catch {
    throw new Error(fallbackMessage);
  }
  if (!response.ok || !payload || payload.code !== 200 || payload.data === undefined) {
    throw new Error(payload?.msg || fallbackMessage);
  }
  return payload.data;
}

async function readApiVoid(response: Response, fallbackMessage: string): Promise<void> {
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<unknown> | null;
  if (!response.ok || !payload || payload.code !== 200) {
    throw new Error(payload?.msg || fallbackMessage);
  }
}

// ─── 通知接口 ────────────────────────────────────────────────

/** 分页查询当前用户的通知。 */
export async function listNotifications(
  session: AuthSession,
  query: { current?: number; size?: number } = {},
): Promise<PageResult<WorkbenchNotification>> {
  const params = new URLSearchParams({
    current: String(query.current ?? 1),
    size: String(query.size ?? 20),
  });
  const response = await fetch(`${API_BASE_URL}/v1/notifications/list?${params.toString()}`, {
    headers: authHeaders(session),
  });
  return readApiData(response, "查询通知失败，请稍后重试");
}

/** 查询当前用户的未读通知数。 */
export async function getUnreadNotificationCount(
  session: AuthSession,
): Promise<number> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/unread-count`, {
    headers: authHeaders(session),
  });
  const data = await readApiData<{ count: number }>(response, "查询未读通知失败，请稍后重试");
  return data.count;
}

/** 批量标记通知已读。 */
export async function markNotificationsRead(
  ids: string[],
  session: AuthSession,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/read`, {
    body: JSON.stringify({ ids }),
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(session),
    },
    method: "POST",
  });
  await readApiVoid(response, "标记已读失败，请稍后重试");
}

/** 全部标记已读。 */
export async function markAllNotificationsRead(
  session: AuthSession,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/notifications/read-all`, {
    headers: authHeaders(session),
    method: "POST",
  });
  await readApiVoid(response, "标记已读失败，请稍后重试");
}
