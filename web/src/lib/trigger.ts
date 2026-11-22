import { API_BASE_URL, notifyUnauthorized, type AuthSession } from "./auth";

// ─── 类型定义 ───────────────────────────────────────────────

export type TriggerStatus = "ACTIVE" | "PAUSED" | "ARCHIVED";

export type WorkbenchTrigger = {
  id: string;
  name: string;
  status: TriggerStatus;
  executionMode: "AGENT" | "WORKFLOW";
  capabilityName?: string;
  conversationId: string;
  goalTemplate: string;
  cronExpr: string;
  timezone: string;
  quietHours?: string;
  allowedToolCodes?: string[];
  nextFireAt?: string;
  lastRunStatus?: string;
  lastRunAt?: string;
  consecutiveFailures: number;
  pauseReason?: string;
  createdAt: string;
  updatedAt: string;
};

export type TriggerFire = {
  firedAt?: string;
  id: string;
  plannedAt: string;
  skipReason?: string;
  status: "CANCELLED" | "EXPIRED" | "FAILED" | "FIRED" | "RUNNING" | "SKIPPED" | "SUCCEEDED";
  triggerId: string;
};

export type TriggerCapability = {
  code: string;
  name: string;
  type: "AGENT" | "WORKFLOW";
};

export type TriggerCapabilities = {
  applicationId: string;
  applicationVersionId: string;
  capabilities: TriggerCapability[];
};

export type TriggerFirePage = {
  current: number;
  records: TriggerFire[];
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

// ─── 触发器接口 ──────────────────────────────────────────────

/** 查询应用当前发布版本中可绑定的能力。 */
export async function listTriggerCapabilities(
  applicationCode: string,
  session: AuthSession,
): Promise<TriggerCapabilities> {
  const response = await fetch(
    `${API_BASE_URL}/v1/triggers/capabilities?applicationCode=${encodeURIComponent(applicationCode)}`,
    { headers: authHeaders(session) },
  );
  return readApiData(response, "获取可绑定能力失败，请稍后重试");
}

/** 分页查询当前用户的触发器。 */
export async function listTriggers(
  session: AuthSession,
  query: { current?: number; size?: number } = {},
): Promise<TriggerFirePage & { records: WorkbenchTrigger[] }> {
  const params = new URLSearchParams({
    current: String(query.current ?? 1),
    size: String(query.size ?? 20),
  });
  const response = await fetch(`${API_BASE_URL}/v1/triggers/list?${params.toString()}`, {
    headers: authHeaders(session),
  });
  return readApiData(response, "查询定时任务失败，请稍后重试");
}

/** 创建定时触发器。 */
export async function createTrigger(
  payload: Record<string, unknown>,
  session: AuthSession,
): Promise<WorkbenchTrigger> {
  const response = await fetch(`${API_BASE_URL}/v1/triggers/create`, {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(session),
    },
    method: "POST",
  });
  return readApiData(response, "创建定时任务失败，请稍后重试");
}

/** 更新触发器可编辑字段。 */
export async function updateTrigger(
  triggerId: string,
  payload: Record<string, unknown>,
  session: AuthSession,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/triggers/update/${encodeURIComponent(triggerId)}`, {
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(session),
    },
    method: "POST",
  });
  await readApiVoid(response, "更新定时任务失败，请稍后重试");
}

async function triggerAction(
  action: "pause" | "resume" | "delete",
  triggerId: string,
  session: AuthSession,
  fallbackMessage: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/triggers/${action}/${encodeURIComponent(triggerId)}`,
    { headers: authHeaders(session), method: "POST" },
  );
  await readApiVoid(response, fallbackMessage);
}

export function pauseTrigger(triggerId: string, session: AuthSession): Promise<void> {
  return triggerAction("pause", triggerId, session, "暂停任务失败，请稍后重试");
}

export function resumeTrigger(triggerId: string, session: AuthSession): Promise<void> {
  return triggerAction("resume", triggerId, session, "恢复任务失败，请稍后重试");
}

export function deleteTrigger(triggerId: string, session: AuthSession): Promise<void> {
  return triggerAction("delete", triggerId, session, "删除任务失败，请稍后重试");
}

/** 分页查询触发器运行历史。 */
export async function listTriggerFires(
  triggerId: string,
  session: AuthSession,
  query: { current?: number; size?: number } = {},
): Promise<TriggerFirePage> {
  const params = new URLSearchParams({
    current: String(query.current ?? 1),
    size: String(query.size ?? 10),
    triggerId,
  });
  const response = await fetch(`${API_BASE_URL}/v1/triggers/fire-list?${params.toString()}`, {
    headers: authHeaders(session),
  });
  return readApiData(response, "查询运行历史失败，请稍后重试");
}
