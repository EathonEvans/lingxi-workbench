import { API_BASE_URL, type AuthSession } from "./auth";

type ChatChunk = {
  content: string;
  sequence: number;
};

export type ChatStart = {
  assistantMessageId: string;
  conversationId: string;
  userMessageId: string;
};

export type AvailableApplication = {
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
  completedAt?: string;
  content: string;
  conversationId: string;
  createdAt: string;
  errorCode?: string;
  errorMessage?: string;
  id: string;
  role: "ASSISTANT" | "SYSTEM" | "USER";
  sequenceNo: number;
  status: "CANCELLED" | "COMPLETED" | "FAILED" | "GENERATING" | "INTERRUPTED";
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

type ChatErrorPayload = {
  code?: string;
  message?: string;
  msg?: string;
};

type StreamChatOptions = {
  applicationCode: string;
  clientMessageId: string;
  conversationId: string;
  onCancelled?: () => void;
  onChunk: (content: string, sequence: number) => void;
  onSnapshot?: (content: string) => void;
  onStart?: (start: ChatStart) => void;
  query: string;
  session: AuthSession;
  signal?: AbortSignal;
};

type ResumeChatOptions = Omit<
  StreamChatOptions,
  "applicationCode" | "clientMessageId" | "conversationId" | "onStart" | "query"
> & {
  afterSequence: number;
  assistantMessageId: string;
};

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

function parseEventBlock(block: string) {
  let event = "message";
  const dataLines: string[] = [];

  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }

  return { data: dataLines.join("\n"), event };
}

function handleEvent(
  block: string,
  options: Pick<
    StreamChatOptions,
    "onCancelled" | "onChunk" | "onSnapshot" | "onStart"
  >,
) {
  const { data, event } = parseEventBlock(block);

  if (event === "done") return true;
  if (!data) return false;

  let payload: ChatChunk | ChatErrorPayload;
  try {
    payload = JSON.parse(data) as ChatChunk | ChatErrorPayload;
  } catch {
    throw new ChatStreamError("模型返回的数据格式不正确，请稍后重试");
  }

  if (event === "error") {
    const error = payload as ChatErrorPayload;
    throw new ChatStreamError(error.message || "模型服务暂时不可用，请稍后重试", {
      code: error.code,
    });
  }

  if (event === "cancelled") {
    options.onCancelled?.();
    return true;
  }

  if (event === "start") {
    options.onStart?.(payload as ChatStart);
    return false;
  }

  if (event === "snapshot") {
    options.onSnapshot?.((payload as ChatChunk).content || "");
    return false;
  }

  const chunk = payload as ChatChunk;
  if (typeof chunk.content === "string" && chunk.content) {
    options.onChunk(chunk.content, chunk.sequence || 0);
  }
  return false;
}

async function consumeEventStream(
  response: Response,
  options: Pick<
    StreamChatOptions,
    "onCancelled" | "onChunk" | "onSnapshot" | "onStart"
  >,
) {
  if (!response.body) throw new ChatStreamError("当前浏览器无法读取流式响应");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = false;
  let terminalReceived = false;

  try {
    while (!completed) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      buffer = buffer.replaceAll("\r\n", "\n");

      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary).trim();
        buffer = buffer.slice(boundary + 2);
        if (block && handleEvent(block, options)) {
          completed = true;
          terminalReceived = true;
          break;
        }
        boundary = buffer.indexOf("\n\n");
      }

      if (done) {
        const finalBlock = buffer.trim();
        if (finalBlock && handleEvent(finalBlock, options)) terminalReceived = true;
        break;
      }
    }
    if (!terminalReceived) {
      throw new ChatStreamError("流式连接已中断，刷新页面后可继续接收回答");
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
}

async function requireSuccessfulResponse(response: Response) {
  if (response.ok) return;
  const payload = (await response.json().catch(() => null)) as ChatErrorPayload | null;
  throw new ChatStreamError(payload?.msg || payload?.message || "对话请求失败，请稍后重试", {
    status: response.status,
  });
}

async function readApiData<T>(response: Response, fallbackMessage: string): Promise<T> {
  const payload = (await response.json().catch(() => null)) as
    | { code: number; data?: T; msg?: string }
    | null;
  if (!response.ok || payload?.code !== 200 || payload.data === undefined) {
    throw new ChatStreamError(payload?.msg || fallbackMessage, {
      status: payload?.code || response.status,
    });
  }
  return payload.data;
}

async function readApiVoid(response: Response, fallbackMessage: string) {
  const payload = (await response.json().catch(() => null)) as
    | { code: number; msg?: string }
    | null;
  if (!response.ok || payload?.code !== 200) {
    throw new ChatStreamError(payload?.msg || fallbackMessage, {
      status: payload?.code || response.status,
    });
  }
}

function conversationQueryString(query: ConversationQuery) {
  const params = new URLSearchParams({
    current: String(query.current ?? 1),
    size: String(query.size ?? 20),
  });
  if (query.keyword?.trim()) params.set("keyword", query.keyword.trim());
  if (query.status) params.set("status", query.status);
  return params.toString();
}

/**
 * 调用应用端 SSE 对话接口并逐块交付模型增量内容。
 * fetch 支持 POST 和动态认证头，EventSource 无法满足这两个条件。
 */
export async function streamChat({
  applicationCode,
  clientMessageId,
  conversationId,
  onChunk,
  onCancelled,
  onSnapshot,
  onStart,
  query,
  session,
  signal,
}: StreamChatOptions) {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/v1/ai/chat/start`, {
      body: JSON.stringify({ applicationCode, clientMessageId, conversationId, query }),
      headers: {
        "Content-Type": "application/json",
        [session.tokenName]: session.tokenValue,
      },
      method: "POST",
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ChatStreamError("无法连接模型服务，请确认应用端服务已经启动");
  }

  await requireSuccessfulResponse(response);
  const payload = (await response.json().catch(() => null)) as
    | { code: number; data?: ChatStart; msg?: string }
    | null;
  if (payload?.code !== 200 || !payload.data) {
    throw new ChatStreamError(payload?.msg || "创建模型生成任务失败，请稍后重试");
  }
  onStart?.(payload.data);
  await resumeChat({
    afterSequence: 0,
    assistantMessageId: payload.data.assistantMessageId,
    onCancelled,
    onChunk,
    onSnapshot,
    session,
    signal,
  });
}

/** 查询用户端允许选择的已启用、已发布 AI 应用。 */
export async function listAvailableApplications(
  session: AuthSession,
): Promise<AvailableApplication[]> {
  const response = await fetch(`${API_BASE_URL}/v1/applications/available`, {
    headers: { [session.tokenName]: session.tokenValue },
  });
  return readApiData(response, "查询可用应用失败，请稍后重试");
}

/** 浏览器刷新或网络恢复后，从最后一个增量序号继续订阅生成任务。 */
export async function resumeChat({
  afterSequence,
  assistantMessageId,
  onCancelled,
  onChunk,
  onSnapshot,
  session,
  signal,
}: ResumeChatOptions) {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/v1/ai/chat/resume`, {
      body: JSON.stringify({ afterSequence, assistantMessageId }),
      headers: {
        Accept: "text/event-stream, application/json",
        "Content-Type": "application/json",
        [session.tokenName]: session.tokenValue,
      },
      method: "POST",
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ChatStreamError("无法重新连接模型生成任务，请稍后重试");
  }
  await requireSuccessfulResponse(response);
  await consumeEventStream(response, { onCancelled, onChunk, onSnapshot });
}

/** 显式取消模型生成；单纯中断 SSE 连接不会调用该接口。 */
export async function cancelChat(assistantMessageId: string, session: AuthSession) {
  const response = await fetch(`${API_BASE_URL}/v1/ai/chat/cancel`, {
    body: JSON.stringify({ assistantMessageId }),
    headers: {
      "Content-Type": "application/json",
      [session.tokenName]: session.tokenValue,
    },
    method: "POST",
  });
  await requireSuccessfulResponse(response);
  const payload = (await response.json().catch(() => null)) as
    | { code: number; msg?: string }
    | null;
  if (payload?.code !== 200) {
    throw new ChatStreamError(payload?.msg || "停止生成失败，请稍后重试");
  }
}

/** 创建空会话，首条消息发送成功后由后端自动生成会话标题。 */
export async function createConversation(session: AuthSession): Promise<Conversation> {
  const response = await fetch(`${API_BASE_URL}/v1/conversations/create`, {
    headers: { [session.tokenName]: session.tokenValue },
    method: "POST",
  });
  const payload = (await response.json().catch(() => null)) as
    | { code: number; data?: Conversation; msg?: string }
    | null;
  if (!response.ok || payload?.code !== 200 || !payload.data) {
    throw new ChatStreamError(payload?.msg || "创建会话失败，请稍后重试", {
      status: payload?.code || response.status,
    });
  }
  return payload.data;
}

/** 分页查询当前账号的会话记录。 */
export async function listConversations(
  session: AuthSession,
  query: ConversationQuery = {},
): Promise<PageResult<Conversation>> {
  const response = await fetch(
    `${API_BASE_URL}/v1/conversations/list?${conversationQueryString(query)}`,
    { headers: { [session.tokenName]: session.tokenValue } },
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
    { headers: { [session.tokenName]: session.tokenValue } },
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
        [session.tokenName]: session.tokenValue,
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
      headers: { [session.tokenName]: session.tokenValue },
      method: "POST",
    },
  );
  await readApiVoid(
    response,
    action === "archive" ? "归档会话失败，请稍后重试" : "删除会话失败，请稍后重试",
  );
}

/** 归档会话，归档后仍保留历史消息，但不能继续发送。 */
export async function archiveConversation(conversationId: string, session: AuthSession) {
  await changeConversation("archive", conversationId, session);
}

/** 删除当前用户的会话记录。 */
export async function deleteConversation(conversationId: string, session: AuthSession) {
  await changeConversation("delete", conversationId, session);
}
