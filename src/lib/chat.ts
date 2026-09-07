import { API_BASE_URL, notifyUnauthorized, type AuthSession } from "./auth";

// ─── 类型定义 ───────────────────────────────────────────────

type ChatChunk = {
  content: string;
  sequence: number;
};

type ChatSnapshot = {
  content: string;
};

export type ChatToolEvent = {
  displayName: string;
  durationMillis?: number;
  errorCode?: string;
  invocationId: string;
  sequence: number;
  planNo?: number;
  status: "FAILED" | "REUSED" | "RUNNING" | "SUCCEEDED" | "WAITING_HUMAN";
  stepId?: string;
  toolCode: string;
};

export type ChatAgentProgressEvent = {
  message: string;
  phase: "ANALYZING" | "EVALUATING" | "FINALIZING" | "USING_TOOLS";
  roundNo: number;
  sequence: number;
  toolCallCount: number;
};

export type ChatSubAgentEvent = {
  agentId: string;
  agentName: string;
  delegationId: string;
  durationMillis?: number;
  errorCode?: string;
  planNo?: number;
  sequence: number;
  status: "FAILED" | "RUNNING" | "SUCCEEDED";
  stepId?: string;
};

export type ChatPlanStep = {
  capabilityCode: string;
  name: string;
  status: "COMPLETED" | "PENDING" | "RUNNING";
  stepId: string;
};

export type ChatExecutionEventType =
  | "AGENT_COMPLETED"
  | "AGENT_FAILED"
  | "AGENT_STARTED"
  | "CLARIFICATION_REQUIRED"
  | "FINALIZING"
  | "MODEL_COMPLETED"
  | "MODEL_STARTED"
  | "PLANNING"
  | "PLAN_CREATED"
  | "REPLANNING"
  | "RUN_CANCELLED"
  | "RUN_COMPLETED"
  | "RUN_FAILED"
  | "SKILL_ACTIVATED"
  | "STEP_COMPLETED"
  | "STEP_FAILED"
  | "STEP_STARTED"
  | "TOOL_COMPLETED"
  | "TOOL_FAILED"
  | "TOOL_WAITING_HUMAN"
  | "TOOL_STARTED";

export type ChatExecutionTimelineEvent = {
  displayName?: string;
  eventType: ChatExecutionEventType;
  message?: string;
  planNo?: number;
  runId: string;
  sequence: number;
  stepId?: string;
  stepStatus?: "CANCELLED" | "COMPLETED" | "FAILED" | "RUNNING" | "SKIPPED";
  steps: ChatPlanStep[];
};

export type ChatStepOutputEvent = {
  content: string;
  planNo: number;
  runId: string;
  sequence: number;
  stepId: string;
};

export type ChatClarificationEvent = {
  clarificationId: string;
  expiresAt: string;
  interactionType?: "CLARIFICATION";
  options: Array<{ code: string; label: string }>;
  question: string;
  round: number;
  sequence: number;
};

export type ChatInteractionEvent = {
  expiresAt: string;
  fingerprint: string;
  interactionId: string;
  interactionType: "ACTION_APPROVAL" | "PLAN_REVIEW";
  resourceScope?: string;
  revision: number;
  riskLevel: "CRITICAL" | "HIGH" | "LOW" | "MEDIUM";
  sequence: number;
  summary: string;
  title: string;
};

export type PendingInteraction = Omit<ChatInteractionEvent, "sequence"> & {
  assistantMessageId: string;
};

export type InteractionDecisionResult = {
  interactionId: string;
  revision: number;
  status: "CANCELLED" | "REJECTED" | "RESOLVED";
};

export type ClarificationResponse = {
  clarificationId: string;
  interactionType?: ChatClarificationEvent["interactionType"];
  optionCode?: string;
  value?: string;
};

export type ChatStart = {
  assistant: ApplicationAssistantIdentity;
  assistantMessageId: string;
  conversationId: string;
  userMessageId: string;
};

export type ApplicationAssistantIdentity = {
  avatar?: string;
  name: string;
};

export type AvailableApplication = {
  assistant: ApplicationAssistantIdentity;
  code: string;
  description?: string;
  name: string;
};

export type Conversation = {
  createdAt: string;
  id: string;
  lastMessageAt?: string;
  messageCount: number;
  status: "ACTIVE" | "ARCHIVED";
  title: string;
  updatedAt: string;
};

export type ConversationMessage = {
  assistant?: ApplicationAssistantIdentity;
  completedAt?: string;
  content: string;
  conversationId: string;
  createdAt: string;
  errorCode?: string;
  errorMessage?: string;
  id: string;
  role: "ASSISTANT" | "SYSTEM" | "USER";
  sequenceNo: number;
  status: "CANCELLED" | "COMPLETED" | "FAILED" | "GENERATING" | "INTERRUPTED" | "WAITING_HUMAN";
};

export type PageResult<T> = {
  current: number;
  records: T[];
  size: number;
  total: number;
};

type ConversationQuery = {
  current?: number;
  keyword?: string;
  size?: number;
  status?: Conversation["status"];
};

export type ChatErrorPayload = {
  code?: string;
  message?: string;
  msg?: string;
};

export type StreamChatOptions = {
  applicationCode: string;
  clientMessageId: string;
  clarificationResponse?: ClarificationResponse;
  conversationId?: string;
  skillCodes?: string[];
  onCancelled?: () => void;
  onChunk: (content: string, sequence: number) => void;
  onClarification?: (event: ChatClarificationEvent) => void;
  onInteractionRequired?: (event: ChatInteractionEvent) => void;
  onInteractionResolved?: (event: ChatInteractionEvent) => void;
  onProgress?: (event: ChatAgentProgressEvent) => void;
  onTimeline?: (event: ChatExecutionTimelineEvent) => void;
  onSnapshot?: (content: string) => void;
  onStart?: (start: ChatStart) => void;
  onStepOutput?: (event: ChatStepOutputEvent) => void;
  onSubAgent?: (event: ChatSubAgentEvent) => void;
  onTool?: (event: ChatToolEvent) => void;
  query: string;
  session: AuthSession;
  signal?: AbortSignal;
};

type ResumeChatOptions = Omit<
  StreamChatOptions,
  | "applicationCode"
  | "clarificationResponse"
  | "clientMessageId"
  | "conversationId"
  | "onStart"
  | "query"
> & {
  afterSequence: number;
  assistantMessageId: string;
};

// ─── 错误类型 ────────────────────────────────────────────────

export class ChatStreamError extends Error {
  readonly code?: string;
  readonly status?: number;

  constructor(message: string, options?: { code?: string; status?: number }) {
    super(message);
    this.name = "ChatStreamError";
    this.code = options?.code;
    this.status = options?.status;
  }
}

/** 只有任务可能仍在服务端运行的传输故障才允许续接，业务终态失败必须重新生成。 */
export function isResumableStreamError(error: unknown): boolean {
  return error instanceof TypeError
    || (error instanceof ChatStreamError && error.code === "STREAM_DISCONNECTED");
}

/** 只有服务端返回的十进制消息主键才能用于续接或取消生成任务。 */
export function isServerMessageId(messageId: string): boolean {
  return /^[1-9]\d*$/.test(messageId);
}

// ─── SSE 解析 ────────────────────────────────────────────────

type ParsedBlock = {
  data: string;
  event: string;
};

export function parseEventBlock(block: string): ParsedBlock {
  let event = "message";
  const dataLines: string[] = [];

  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }

  return { data: dataLines.join("\n"), event };
}

/**
 * 事件路由表：event 类型 → 处理函数。
 * 返回 true 表示流结束（done/error/cancelled），false 表示继续接收。
 */
type EventHandler = (data: string, options: StreamChatOptions) => boolean;

const EVENT_DISPATCH: Record<string, EventHandler> = {
  message(data, options) {
    const chunk = JSON.parse(data) as ChatChunk;
    options.onChunk(chunk.content, chunk.sequence);
    return false;
  },

  snapshot(data, options) {
    const snapshot = JSON.parse(data) as ChatSnapshot;
    options.onSnapshot?.(snapshot.content);
    return false;
  },

  tool(data, options) {
    const tool = JSON.parse(data) as ChatToolEvent;
    options.onTool?.(tool);
    return false;
  },

  agent_progress(data, options) {
    options.onProgress?.(JSON.parse(data) as ChatAgentProgressEvent);
    return false;
  },

  subagent(data, options) {
    const agent = JSON.parse(data) as ChatSubAgentEvent;
    options.onSubAgent?.(agent);
    return false;
  },

  clarification(data, options) {
    const clarification = JSON.parse(data) as ChatClarificationEvent;
    options.onClarification?.(clarification);
    return false;
  },

  interaction_required(data, options) {
    options.onInteractionRequired?.(JSON.parse(data) as ChatInteractionEvent);
    return true;
  },

  interaction_resolved(data, options) {
    options.onInteractionResolved?.(JSON.parse(data) as ChatInteractionEvent);
    return false;
  },

  planning(data, options) {
    options.onTimeline?.(JSON.parse(data) as ChatExecutionTimelineEvent);
    return false;
  },

  plan(data, options) {
    options.onTimeline?.(JSON.parse(data) as ChatExecutionTimelineEvent);
    return false;
  },

  step(data, options) {
    options.onTimeline?.(JSON.parse(data) as ChatExecutionTimelineEvent);
    return false;
  },

  replanning(data, options) {
    options.onTimeline?.(JSON.parse(data) as ChatExecutionTimelineEvent);
    return false;
  },

  step_output(data, options) {
    options.onStepOutput?.(JSON.parse(data) as ChatStepOutputEvent);
    return false;
  },

  done() {
    return true;
  },

  heartbeat() {
    return false;
  },

  error(data) {
    const payload = JSON.parse(data) as ChatErrorPayload;
    throw new ChatStreamError(payload.message || "生成失败，请稍后重试", {
      code: payload.code,
    });
  },

  cancelled(_data, options) {
    options.onCancelled?.();
    return true;
  },

};

/** 处理单个 SSE 事件块。返回 true 表示流已终止。 */
function dispatchEvent(
  event: string,
  data: string,
  options: StreamChatOptions,
): boolean {
  const handler = EVENT_DISPATCH[event];
  if (!handler) return false;

  try {
    return handler(data, options);
  } catch (error) {
    if (error instanceof ChatStreamError) throw error;
    // 单个事件解析失败不中断整个流
    return false;
  }
}

/** SSE 流读取器：正确处理跨 chunk 边界的行分割。 */
export async function consumeEventStream(
  response: Response,
  options: StreamChatOptions,
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) throw new ChatStreamError("响应流不可用");

  const decoder = new TextDecoder();
  let buffer = "";
  let terminalReceived = false;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split("\n\n");
    // 保留最后一个可能不完整的块，等待下一轮补充
    buffer = blocks.pop() ?? "";

    for (const block of blocks) {
      if (options.signal?.aborted) return;
      const { event, data } = parseEventBlock(block);
      const terminated = dispatchEvent(event, data, options);
      if (terminated) {
        terminalReceived = true;
        return;
      }
    }
  }

  // 处理流末尾可能残留的不完整块
  if (buffer.trim() && !options.signal?.aborted) {
    const { event, data } = parseEventBlock(buffer);
    terminalReceived = dispatchEvent(event, data, options);
  }
  if (!terminalReceived && !options.signal?.aborted) {
    throw new ChatStreamError("连接已中断，任务仍在后台运行，可继续接收", {
      code: "STREAM_DISCONNECTED",
    });
  }
}

// ─── HTTP 基元 ────────────────────────────────────────────────

type ApiEnvelope<T> = {
  code: number;
  data?: T;
  msg?: string;
};

/** 读取 JSON body；后端判定登录失效时先清空本地会话。 */
async function readApiBody(response: Response): Promise<ApiEnvelope<unknown>> {
  if (response.status === 401) {
    notifyUnauthorized();
  }
  try {
    return (await response.json()) as ApiEnvelope<unknown>;
  } catch {
    throw new ChatStreamError("服务响应异常，请稍后重试", {
      status: response.status,
    });
  }
}

/** 读取 API 响应 data 字段，业务失败抛出 ChatStreamError。 */
async function readApiData<T>(
  response: Response,
  fallbackMessage: string,
): Promise<T> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const payload = (await readApiBody(response)) as ApiEnvelope<any> | null;

  if (!response.ok || payload?.code !== 200 || !payload) {
    throw new ChatStreamError(
      payload?.msg || fallbackMessage,
      { status: payload?.code || response.status },
    );
  }

  if (payload.data === undefined) {
    throw new ChatStreamError(fallbackMessage, { status: payload.code });
  }

  return payload.data as T;
}

/** 读取 API 响应（无 data 字段）。 */
async function readApiVoid(
  response: Response,
  fallbackMessage: string,
): Promise<void> {
  const payload = await readApiBody(response);
  if (!response.ok || payload?.code !== 200) {
    throw new ChatStreamError(payload?.msg || fallbackMessage, {
      status: payload?.code || response.status,
    });
  }
}

/** 构造鉴权 headers。 */
function authHeaders(session: AuthSession): Record<string, string> {
  return { [session.tokenName]: session.tokenValue };
}

/** 构造查询参数字符串（过滤 undefined 值）。 */
function conversationQueryString(query: ConversationQuery): string {
  return new URLSearchParams(
    Object.entries({
      current: query.current !== undefined ? String(query.current) : undefined,
      keyword: query.keyword || undefined,
      size: query.size !== undefined ? String(query.size) : undefined,
      status: query.status || undefined,
    }).filter(([, v]) => v !== undefined) as [string, string][],
  ).toString();
}

/** 幂等请求缓存：相同 clientMessageId 的并发请求只发一次。 */
const startRequestCache = new Map<string, Promise<ChatStart>>();

// ─── 流式对话 ────────────────────────────────────────────────

/**
 * 创建模型生成任务并向服务端发起流式对话。
 *
 * 流程：
 * 1. POST /start → 获得任务标识（conversationId、assistantMessageId）
 * 2. 调用 resume 端点订阅 SSE，消费增量事件直到流结束
 */
export async function streamChat(options: StreamChatOptions): Promise<void> {
  const { signal } = options;

  // 步骤 1：创建生成任务（幂等去重）
  const key = options.clientMessageId;
  if (!startRequestCache.has(key)) {
    startRequestCache.set(key, initiateChat(options));
  }
  const startRequest = startRequestCache.get(key)!;
  let start: ChatStart;
  try {
    start = await startRequest;
  } finally {
    if (startRequestCache.get(key) === startRequest) {
      startRequestCache.delete(key);
    }
  }

  if (signal?.aborted) return;
  options.onStart?.(start);

  // 步骤 2：订阅 SSE 流
  await resumeStream(start.assistantMessageId, 0, options);
}

/** POST /start：创建模型生成任务并返回任务标识。 */
async function initiateChat(options: StreamChatOptions): Promise<ChatStart> {
  const response = await fetch(`${API_BASE_URL}/v1/ai/chat/start`, {
    body: JSON.stringify({
      applicationCode: options.applicationCode,
      clientMessageId: options.clientMessageId,
      clarificationResponse: options.clarificationResponse,
      conversationId: options.conversationId,
      query: options.query,
      skillCodes: options.skillCodes,
    }),
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(options.session),
    },
    method: "POST",
    signal: options.signal,
  });

  return readApiData<ChatStart>(response, "发起对话失败，请稍后重试");
}

/** POST /resume：订阅 SSE 事件流。 */
async function resumeStream(
  assistantMessageId: string,
  afterSequence: number,
  options: Pick<StreamChatOptions, "session" | "signal" | "onChunk" | "onSnapshot" | "onTool" | "onSubAgent" | "onClarification" | "onInteractionRequired" | "onInteractionResolved" | "onProgress" | "onTimeline" | "onStepOutput" | "onCancelled">,
): Promise<void> {
  if (!isServerMessageId(assistantMessageId)) {
    throw new ChatStreamError("生成任务尚未创建完成，请重新发送", {
      code: "INVALID_GENERATION_TASK_ID",
      status: 400,
    });
  }
  const response = await fetch(`${API_BASE_URL}/v1/ai/chat/resume`, {
    body: JSON.stringify({ assistantMessageId, afterSequence }),
    headers: {
      "Content-Type": "application/json",
      "Accept": "text/event-stream, application/json",
      ...authHeaders(options.session),
    },
    method: "POST",
    signal: options.signal,
  });

  if (!response.ok) {
    // 后端判定登录失效时先清空本地会话，再由统一错误处理跳转登录页
    if (response.status === 401) {
      notifyUnauthorized();
    }
    // 先尝试解析业务错误信息
    try {
      const payload = await response.json() as ChatErrorPayload;
      throw new ChatStreamError(
        payload.message || payload.msg || "恢复生成任务失败",
        { status: response.status },
      );
    } catch (error) {
      if (error instanceof ChatStreamError) throw error;
      throw new ChatStreamError("恢复生成任务失败", {
        status: response.status,
      });
    }
  }

  await consumeEventStream(response, options as StreamChatOptions);
}

/**
 * 刷新页面后恢复已存在的模型生成任务。
 */
export async function resumeChat(options: ResumeChatOptions): Promise<void> {
  await resumeStream(
    options.assistantMessageId,
    options.afterSequence,
    options,
  );
}

/**
 * 取消正在生成的模型任务。
 */
export async function cancelChat(
  assistantMessageId: string,
  session: AuthSession,
): Promise<void> {
  if (!isServerMessageId(assistantMessageId)) {
    throw new ChatStreamError("生成任务尚未创建完成", {
      code: "INVALID_GENERATION_TASK_ID",
      status: 400,
    });
  }
  const response = await fetch(
    `${API_BASE_URL}/v1/ai/chat/cancel`,
    {
      body: JSON.stringify({ assistantMessageId }),
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(session),
      },
      method: "POST",
    },
  );
  await readApiVoid(response, "停止生成失败，请稍后重试");
}

/** 提交 HITL 决策；审批通过后服务端通过 RocketMQ 恢复原执行任务。 */
export async function decideInteraction(
  interaction: Pick<ChatInteractionEvent, "fingerprint" | "interactionId" | "revision">,
  decision: "APPROVE" | "REJECT",
  idempotencyKey: string,
  session: AuthSession,
): Promise<InteractionDecisionResult> {
  const response = await fetch(
    `${API_BASE_URL}/v1/interactions/${encodeURIComponent(interaction.interactionId)}/decision`,
    {
      body: JSON.stringify({
        decision,
        expectedRevision: interaction.revision,
        fingerprint: interaction.fingerprint,
      }),
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
        ...authHeaders(session),
      },
      method: "POST",
    },
  );
  return readApiData(response, "提交操作确认失败，请稍后重试");
}

/** 查询指定会话仍在等待用户处理的交互。 */
export async function listPendingInteractions(
  conversationId: string,
  session: AuthSession,
): Promise<PendingInteraction[]> {
  const response = await fetch(
    `${API_BASE_URL}/v1/interactions/pending?conversationId=${encodeURIComponent(conversationId)}`,
    { headers: authHeaders(session) },
  );
  return readApiData(response, "查询待确认操作失败，请稍后重试");
}

// ─── 可用应用 ────────────────────────────────────────────────

export async function listAvailableApplications(
  session: AuthSession,
): Promise<AvailableApplication[]> {
  const response = await fetch(
    `${API_BASE_URL}/v1/applications/available`,
    { headers: authHeaders(session) },
  );
  return readApiData<AvailableApplication[]>(
    response,
    "获取可用应用失败，请稍后重试",
  );
}

// ─── 会话管理 ────────────────────────────────────────────────

/** 分页查询当前账号的会话记录。 */
export async function listConversations(
  session: AuthSession,
  query: ConversationQuery = {},
): Promise<PageResult<Conversation>> {
  const response = await fetch(
    `${API_BASE_URL}/v1/conversations/list?${conversationQueryString(query)}`,
    { headers: authHeaders(session) },
  );
  return readApiData(response, "查询会话记录失败，请稍后重试");
}

/** 分页查询会话消息；后端按消息序号倒序返回，便于向上加载更早记录。 */
export async function listConversationMessages(
  conversationId: string,
  session: AuthSession,
  query: Pick<ConversationQuery, "current" | "size"> = {},
): Promise<PageResult<ConversationMessage>> {
  const params = new URLSearchParams({
    conversationId,
    current: String(query.current ?? 1),
    size: String(query.size ?? 50),
  });
  const response = await fetch(
    `${API_BASE_URL}/v1/conversations/message-list?${params.toString()}`,
    { headers: authHeaders(session) },
  );
  return readApiData(response, "查询历史消息失败，请稍后重试");
}

/** 修改会话标题。 */
export async function renameConversation(
  conversationId: string,
  title: string,
  session: AuthSession,
) {
  const response = await fetch(
    `${API_BASE_URL}/v1/conversations/rename?conversationId=${encodeURIComponent(conversationId)}`,
    {
      body: JSON.stringify({ title }),
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(session),
      },
      method: "POST",
    },
  );
  await readApiVoid(response, "修改会话标题失败，请稍后重试");
}

async function changeConversation(
  action: "archive" | "delete",
  conversationId: string,
  session: AuthSession,
) {
  const response = await fetch(
    `${API_BASE_URL}/v1/conversations/${action}?conversationId=${encodeURIComponent(conversationId)}`,
    {
      headers: authHeaders(session),
      method: "POST",
    },
  );
  await readApiVoid(
    response,
    action === "archive" ? "归档会话失败，请稍后重试" : "删除会话失败，请稍后重试",
  );
}

/** 归档会话，归档后仍保留历史消息，但不能继续发送。 */
export async function archiveConversation(
  conversationId: string,
  session: AuthSession,
) {
  await changeConversation("archive", conversationId, session);
}

/** 删除当前用户的会话记录。 */
export async function deleteConversation(
  conversationId: string,
  session: AuthSession,
) {
  await changeConversation("delete", conversationId, session);
}
