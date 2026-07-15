import { API_BASE_URL, type AuthSession } from "./auth";

type ChatChunk = {
  content: string;
};

type ChatErrorPayload = {
  code?: string;
  message?: string;
  msg?: string;
};

type StreamChatOptions = {
  onChunk: (content: string) => void;
  prompt: string;
  session: AuthSession;
  signal?: AbortSignal;
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

function handleEvent(block: string, onChunk: (content: string) => void) {
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

  const chunk = payload as ChatChunk;
  if (typeof chunk.content === "string" && chunk.content) onChunk(chunk.content);
  return false;
}

/**
 * 调用应用端 SSE 对话接口并逐块交付模型增量内容。
 * fetch 支持 POST 和动态认证头，EventSource 无法满足这两个条件。
 */
export async function streamChat({
  onChunk,
  prompt,
  session,
  signal,
}: StreamChatOptions) {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/v1/ai/chat/stream`, {
      body: JSON.stringify({ prompt }),
      headers: {
        // 流式成功响应使用 SSE，进入流之前的参数与认证错误仍使用统一 JSON 响应体。
        Accept: "text/event-stream, application/json",
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

  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ChatErrorPayload | null;
    throw new ChatStreamError(payload?.msg || payload?.message || "对话请求失败，请稍后重试", {
      status: response.status,
    });
  }

  if (!response.body) throw new ChatStreamError("当前浏览器无法读取流式响应");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = false;

  try {
    while (!completed) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      buffer = buffer.replaceAll("\r\n", "\n");

      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary).trim();
        buffer = buffer.slice(boundary + 2);
        if (block && handleEvent(block, onChunk)) {
          completed = true;
          break;
        }
        boundary = buffer.indexOf("\n\n");
      }

      if (done) {
        const finalBlock = buffer.trim();
        if (finalBlock) handleEvent(finalBlock, onChunk);
        break;
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined);
  }
}
