import { describe, expect, it, vi } from "vitest";
import {
  ChatStreamError,
  consumeEventStream,
  isResumableStreamError,
  parseEventBlock,
  streamChat,
  type StreamChatOptions,
} from "../chat";

function sseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
  return new Response(stream);
}

function options(): StreamChatOptions {
  return {
    applicationCode: "support",
    clientMessageId: "msg-1",
    conversationId: "1",
    query: "你好",
    session: {
      expiresAt: 0,
      tokenName: "Authorization",
      tokenValue: "token",
    },
    onChunk: vi.fn(),
  };
}

describe("parseEventBlock", () => {
  it("解析 event 与 data，默认 event 为 message", () => {
    expect(parseEventBlock("event: tool\ndata: {\"a\":1}\n\n")).toEqual({
      event: "tool",
      data: '{"a":1}',
    });
    expect(parseEventBlock("data: 你好")).toEqual({
      event: "message",
      data: "你好",
    });
  });

  it("合并多行 data 并以换行连接", () => {
    expect(parseEventBlock("data: 第一行\ndata: 第二行")).toEqual({
      event: "message",
      data: "第一行\n第二行",
    });
  });

  it("data 值去除前导空白", () => {
    expect(parseEventBlock("data:   带空格内容").data).toBe("带空格内容");
  });
});

describe("consumeEventStream", () => {
  it("消费完整事件块并分派", async () => {
    const opts = options();
    await consumeEventStream(
      sseResponse(["event: message\ndata: {\"content\":\"你\",\"sequence\":1}\n\nevent: done\ndata: {}\n\n"]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenCalledWith("你", 1);
  });

  it("分派 Agent 安全阶段进度", async () => {
    const opts = { ...options(), onProgress: vi.fn() };
    await consumeEventStream(
      sseResponse([
        "event: agent_progress\ndata: {\"sequence\":2,\"phase\":\"EVALUATING\",\"message\":\"正在评估已获得的信息\",\"roundNo\":2,\"toolCallCount\":2}\n\nevent: done\ndata: {}\n\n",
      ]),
      opts,
    );

    expect(opts.onProgress).toHaveBeenCalledWith({
      message: "正在评估已获得的信息",
      phase: "EVALUATING",
      roundNo: 2,
      sequence: 2,
      toolCallCount: 2,
    });
  });

  it("跨 chunk 边界正确拼接被拆分的行", async () => {
    const opts = options();
    // 第一个块只包含半个事件，第二个块补充剩余部分
    await consumeEventStream(
      sseResponse([
        "event: message\nda",
        "ta: {\"content\":\"好\",\"sequence\":2}\n\nevent: done\ndata: {}\n\n",
      ]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenCalledWith("好", 2);
  });

  it("单个 chunk 含多个事件时依次分派", async () => {
    const opts = options();
    await consumeEventStream(
      sseResponse([
        "event: message\ndata: {\"content\":\"你\",\"sequence\":1}\n\nevent: message\ndata: {\"content\":\"好\",\"sequence\":2}\n\nevent: done\ndata: {}\n\n",
      ]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenNthCalledWith(1, "你", 1);
    expect(opts.onChunk).toHaveBeenNthCalledWith(2, "好", 2);
  });

  it("流末尾残留的不完整块也被处理", async () => {
    const opts = options();
    // 末尾没有空行分隔，属于流结束时残留的完整事件
    await consumeEventStream(
      sseResponse(["event: message\ndata: {\"content\":\"尾\",\"sequence\":3}\n\nevent: done\ndata: {}"]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenCalledWith("尾", 3);
  });

  it("done 事件终止流，不再分派后续块", async () => {
    const opts = options();
    await consumeEventStream(
      sseResponse([
        "event: message\ndata: {\"content\":\"你\",\"sequence\":1}\n\nevent: done\ndata: {}\n\nevent: message\ndata: {\"content\":\"不应到达\",\"sequence\":2}\n\n",
      ]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenCalledTimes(1);
  });

  it("error 事件抛出 ChatStreamError", async () => {
    const opts = options();
    await expect(
      consumeEventStream(
        sseResponse(['event: error\ndata: {"code":"AI_MODEL_ERROR","message":"模型超时"}\n\n']),
        opts,
      ),
    ).rejects.toThrow(/模型超时/);
  });

  it("工具审批事件暂停当前流且不误报连接中断", async () => {
    const onInteractionRequired = vi.fn();
    const opts = { ...options(), onInteractionRequired };
    await consumeEventStream(
      sseResponse([
        'event: interaction_required\ndata: {"interactionId":"91","interactionType":"ACTION_APPROVAL","title":"确认发送邮件","summary":"将向外部邮箱发送内容","riskLevel":"HIGH","resourceScope":"mail:send","fingerprint":"abc","revision":0,"expiresAt":"2026-08-16T01:00:00+08:00","sequence":7}\n\n',
      ]),
      opts,
    );

    expect(onInteractionRequired).toHaveBeenCalledWith({
      expiresAt: "2026-08-16T01:00:00+08:00",
      fingerprint: "abc",
      interactionId: "91",
      interactionType: "ACTION_APPROVAL",
      resourceScope: "mail:send",
      revision: 0,
      riskLevel: "HIGH",
      sequence: 7,
      summary: "将向外部邮箱发送内容",
      title: "确认发送邮件",
    });
  });

  it("审批恢复事件后继续接收模型输出", async () => {
    const onInteractionResolved = vi.fn();
    const opts = { ...options(), onInteractionResolved };
    await consumeEventStream(
      sseResponse([
        'event: interaction_resolved\ndata: {"interactionId":"91","interactionType":"ACTION_APPROVAL","title":"确认发送邮件","summary":"已批准","riskLevel":"HIGH","fingerprint":"abc","revision":1,"expiresAt":"2026-08-16T01:00:00+08:00","sequence":8}\n\nevent: message\ndata: {"content":"已发送","sequence":9}\n\nevent: done\ndata: {}\n\n',
      ]),
      opts,
    );

    expect(onInteractionResolved).toHaveBeenCalledOnce();
    expect(opts.onChunk).toHaveBeenCalledWith("已发送", 9);
  });

  it("流在没有终态事件时断开应保留后台任务用于续接", async () => {
    const response = sseResponse(['event: message\ndata: {"content":"部分","sequence":1}\n\n']);
    const onChunk = vi.fn();
    const opts = options();
    opts.onChunk = onChunk;

    await expect(consumeEventStream(response, opts)).rejects.toMatchObject({
      code: "STREAM_DISCONNECTED",
      message: "连接已中断，任务仍在后台运行，可继续接收",
    });
    expect(onChunk).toHaveBeenCalledWith("部分", 1);
  });

  it("单个事件 JSON 解析失败不中断整个流", async () => {
    const opts = options();
    await consumeEventStream(
      sseResponse([
        "event: message\ndata: 不是合法JSON\n\nevent: message\ndata: {\"content\":\"好\",\"sequence\":2}\n\nevent: done\ndata: {}\n\n",
      ]),
      opts,
    );

    // 损坏事件被跳过，后续事件正常分派
    expect(opts.onChunk).toHaveBeenCalledWith("好", 2);
  });
});

describe("isResumableStreamError", () => {
  it("仅允许真实传输中断或浏览器网络错误续接", () => {
    expect(isResumableStreamError(new ChatStreamError("连接中断", {
      code: "STREAM_DISCONNECTED",
    }))).toBe(true);
    expect(isResumableStreamError(new TypeError("Failed to fetch"))).toBe(true);
  });

  it("服务端终态失败不允许继续接收", () => {
    expect(isResumableStreamError(new ChatStreamError("Agent运行失败", {
      code: "AGENT_RUNTIME_FAILED",
    }))).toBe(false);
    expect(isResumableStreamError(new ChatStreamError("模型超时", {
      code: "AI_MODEL_TIMEOUT",
    }))).toBe(false);
  });
});

describe("streamChat", () => {
  it("首次发送使用客户端会话标识，服务端创建会话后再订阅流", async () => {
    const onStart = vi.fn();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 200,
            data: {
              assistantMessageId: "12",
              conversationId: "10",
              userMessageId: "11",
            },
            msg: "",
          }),
          { headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(sseResponse(["event: done\ndata: {}\n\n"]));
    vi.stubGlobal("fetch", fetchMock);

    try {
      await streamChat({
        ...options(),
        conversationId: undefined,
        onStart,
        skillCodes: ["fact_check"],
      });

      const startRequest = fetchMock.mock.calls[0]?.[1];
      const requestBody = JSON.parse(String(startRequest?.body)) as Record<string, unknown>;
      expect(requestBody).toMatchObject({
        applicationCode: "support",
        clientMessageId: "msg-1",
        query: "你好",
        skillCodes: ["fact_check"],
      });
      expect(requestBody.conversationId).toBeUndefined();
      expect(onStart).toHaveBeenCalledWith({
        assistantMessageId: "12",
        conversationId: "10",
        userMessageId: "11",
      });
      expect(fetchMock).toHaveBeenCalledTimes(2);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
