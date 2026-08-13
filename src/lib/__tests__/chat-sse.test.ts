import { describe, expect, it, vi } from "vitest";
import {
  consumeEventStream,
  parseEventBlock,
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
      sseResponse(["event: message\ndata: {\"content\":\"你\",\"sequence\":1}\n\n"]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenCalledWith("你", 1);
  });

  it("跨 chunk 边界正确拼接被拆分的行", async () => {
    const opts = options();
    // 第一个块只包含半个事件，第二个块补充剩余部分
    await consumeEventStream(
      sseResponse([
        "event: message\nda",
        "ta: {\"content\":\"好\",\"sequence\":2}\n\n",
      ]),
      opts,
    );

    expect(opts.onChunk).toHaveBeenCalledWith("好", 2);
  });

  it("单个 chunk 含多个事件时依次分派", async () => {
    const opts = options();
    await consumeEventStream(
      sseResponse([
        "event: message\ndata: {\"content\":\"你\",\"sequence\":1}\n\nevent: message\ndata: {\"content\":\"好\",\"sequence\":2}\n\n",
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
      sseResponse(["event: message\ndata: {\"content\":\"尾\",\"sequence\":3}"]),
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

  it("单个事件 JSON 解析失败不中断整个流", async () => {
    const opts = options();
    await consumeEventStream(
      sseResponse([
        "event: message\ndata: 不是合法JSON\n\nevent: message\ndata: {\"content\":\"好\",\"sequence\":2}\n\n",
      ]),
      opts,
    );

    // 损坏事件被跳过，后续事件正常分派
    expect(opts.onChunk).toHaveBeenCalledWith("好", 2);
  });
});
