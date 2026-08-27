import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  appendChunkIfNew,
  type PendingGeneration,
  readPendingGeneration,
  savePendingGeneration,
} from "@/hooks/use-chat-stream";

class MemorySessionStorage {
  private readonly values = new Map<string, string>();

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  removeItem(key: string): void {
    this.values.delete(key);
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }
}

function localPending(): PendingGeneration {
  return {
    answer: "",
    applicationCode: "support",
    assistantMessageId: "local-assistant-id",
    lastSequence: 0,
    prompt: "首次发送",
    progress: [],
    subAgents: [],
    stepOutputs: [],
    timeline: [],
    tools: [],
    userMessageId: "client-message-id",
  };
}

describe("pending generation persistence", () => {
  beforeEach(() => {
    vi.stubGlobal("sessionStorage", new MemorySessionStorage());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps a first request before server ids are returned", () => {
    const pending = localPending();

    savePendingGeneration(pending);

    expect(readPendingGeneration()).toEqual(pending);
  });

  it("rejects a server task without its conversation id", () => {
    savePendingGeneration({
      ...localPending(),
      assistantMessageId: "2077574496283791361",
    });

    expect(readPendingGeneration()).toBeUndefined();
  });

  it("does not append a chunk whose sequence was already consumed", () => {
    const pending = localPending();

    expect(appendChunkIfNew(pending, "你", 1)).toBe(true);
    expect(appendChunkIfNew(pending, "不应重复", 1)).toBe(false);
    expect(appendChunkIfNew(pending, "好", 2)).toBe(true);

    expect(pending).toMatchObject({ answer: "你好", lastSequence: 2 });
  });
});
